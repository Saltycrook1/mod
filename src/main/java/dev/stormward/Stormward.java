package dev.stormward;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.stormward.block.StormSirenBlock;
import dev.stormward.config.StormConfig;
import dev.stormward.network.StormPayload;
import dev.stormward.server.*;
import dev.stormward.sim.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import static net.minecraft.server.command.CommandManager.*;

public final class Stormward implements ModInitializer {
    public static final String ID = "stormward";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static StormConfig CONFIG = new StormConfig();
    public static final Item RECEIVER = new Item(new Item.Settings().maxCount(1));
    public static final Block REINFORCED = new Block(AbstractBlock.Settings.create().strength(50, 1200));
    public static final Block SIREN = new StormSirenBlock(AbstractBlock.Settings.create().strength(5, 20));

    @Override public void onInitialize() {
        try { CONFIG = StormConfig.load(); } catch (IOException e) { throw new IllegalStateException("Cannot load Stormward configuration", e); }
        Registry.register(Registries.ITEM, Identifier.of(ID, "storm_receiver"), RECEIVER);
        registerBlock("reinforced_shelter", REINFORCED); registerBlock("storm_siren", SIREN);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(RECEIVER));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> entries.add(SIREN));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> entries.add(REINFORCED));
        PayloadTypeRegistry.playS2C().register(StormPayload.ID, StormPayload.CODEC);
        ServerTickEvents.END_WORLD_TICK.register(StormManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> StormManager.clearRuntime());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> StormManager.sync(handler.player.getServerWorld(), StormSavedData.get(handler.player.getServerWorld())));
        // Conservative build protection: reserve the entire target chunk before a block-item interaction.
        // This can protect failed placement attempts too; false positives preserve player construction.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (CONFIG.protectPlayerBuilds && world instanceof ServerWorld serverWorld && player.getStackInHand(hand).getItem() instanceof BlockItem) {
                var pos = hit.getBlockPos().offset(hit.getSide());
                StormSavedData.get(serverWorld).protect(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
                StormSavedData.get(serverWorld).protect(ChunkPos.toLong(hit.getBlockPos().getX() >> 4, hit.getBlockPos().getZ() >> 4));
            }
            return ActionResult.PASS;
        });
        commands();
        LOGGER.info("Stormward initialized: Minecraft 1.21.1 / Fabric. Destruction={}, storms capped at {}", CONFIG.destruction, CONFIG.maxStorms);
    }
    private static void registerBlock(String name, Block block) {
        Registry.register(Registries.BLOCK, Identifier.of(ID, name), block);
        Registry.register(Registries.ITEM, Identifier.of(ID, name), new BlockItem(block, new Item.Settings()));
    }
    private static void commands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("stormward")
            .then(literal("forecast").executes(c -> {
                var source = c.getSource(); var pos = source.getPosition();
                var storms = StormSavedData.get(source.getWorld()).storms;
                if (storms.isEmpty()) source.sendFeedback(() -> Text.literal("Stormward • No tracked fronts in this dimension."), false);
                for (Storm s : storms) {
                    Forecast.Prediction p = Forecast.forObserver(s, pos.x, pos.z);
                    String estimate = p.approaching() ? String.format(Locale.ROOT, "closest approach %.0fm in %.0fs (linear estimate)", p.closestDistance(), p.etaTicks() / 20.0) : "stationary / moving away";
                    source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT, "%s • %.0fm at %.0f° • %s • next stage %.0fs%s", s.stage(), p.distance(), p.bearingDegrees(), estimate, s.stageRemainingTicks() / 20.0,
                        StormManager.loaded(source.getWorld(), (int)Math.floor(s.x()), (int)Math.floor(s.z())) ? "" : " [paused: unloaded]")), false);
                }
                return storms.size();
            }))
            .then(literal("spawn").requires(s -> s.hasPermissionLevel(2)).executes(c -> spawn(c.getSource(), 1, false))
                .then(argument("strength", DoubleArgumentType.doubleArg(0.1, 5)).executes(c -> spawn(c.getSource(), DoubleArgumentType.getDouble(c, "strength"), false))))
            .then(literal("demo").requires(s -> s.hasPermissionLevel(2)).executes(c -> spawn(c.getSource(), 1, true)))
            .then(literal("stop").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                var data = StormSavedData.get(c.getSource().getWorld()); int count = data.storms.size();
                data.storms.clear(); data.brokenCounts.clear(); data.markDirty(); StormManager.sync(c.getSource().getWorld(), data);
                c.getSource().sendFeedback(() -> Text.literal("Stopped " + count + " fronts."), true); return count;
            }))
            .then(literal("protect").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                var pos = net.minecraft.util.math.BlockPos.ofFloored(c.getSource().getPosition());
                var data = StormSavedData.get(c.getSource().getWorld());
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) data.protect(ChunkPos.toLong((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz));
                c.getSource().sendFeedback(() -> Text.literal("Protected the surrounding 3×3 chunks from storm destruction."), true); return 1;
            }))
            .then(literal("destruction").requires(s -> s.hasPermissionLevel(2)).then(argument("enabled", BoolArgumentType.bool()).executes(c -> {
                boolean enabled = BoolArgumentType.getBool(c, "enabled");
                boolean previous = CONFIG.destruction;
                try { CONFIG.destruction = enabled; CONFIG.save(); } catch (IOException e) { CONFIG.destruction = previous; c.getSource().sendError(Text.literal("Cannot save config: " + e.getMessage())); return 0; }
                c.getSource().sendFeedback(() -> Text.literal("Storm destruction=" + enabled + "; protected chunks, containers and tags still apply."), true); return 1;
            })))
            .then(literal("reload").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                try { CONFIG = StormConfig.load(); StormManager.clearRuntime(); c.getSource().sendFeedback(() -> Text.literal("Stormward config reloaded."), true); return 1; }
                catch (IOException e) { c.getSource().sendError(Text.literal(e.getMessage())); return 0; }
            }))
            .then(literal("status").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                c.getSource().sendFeedback(() -> Text.literal(StormManager.stats(c.getSource().getWorld())), false); return 1;
            }))));
    }
    private static int spawn(ServerCommandSource source, double strength, boolean demo) {
        ServerWorld world = source.getWorld();
        if (world.getRegistryKey() != World.OVERWORLD) { source.sendError(Text.literal("Fronts are supported in the Overworld.")); return 0; }
        StormSavedData data = StormSavedData.get(world);
        if (data.incompatibleSchema()) { source.sendError(Text.literal("Unknown Stormward save schema; restore a compatible mod version before spawning fronts.")); return 0; }
        if (data.storms.size() >= CONFIG.maxStorms) { source.sendError(Text.literal("Storm cap reached; /stormward stop to clear.")); return 0; }
        var pos = source.getPosition();
        double x = pos.x + (demo ? 32 : 64), z = pos.z;
        if (!StormManager.loaded(world, (int)Math.floor(x), (int)Math.floor(z)) || !world.getWorldBorder().contains(net.minecraft.util.math.BlockPos.ofFloored(x, pos.y, z))) {
            source.sendError(Text.literal("Storm origin must be inside a loaded chunk and the world border.")); return 0;
        }
        Storm storm = Storm.create(UUID.randomUUID(), x, z, demo ? -0.015 : -0.025, 0.005, 28 + strength * 3, strength, demo ? 10 : 1);
        data.storms.add(storm); data.markDirty(); StormManager.sync(world, data);
        source.sendFeedback(() -> Text.literal("Stormward • " + (demo ? "30-second demo" : "5-minute front") + " spawned east of you. Hold a Storm Receiver; crouch and seek roof + walls."), true);
        return 1;
    }
}
