package dev.stormward.server;

import dev.stormward.Stormward;
import dev.stormward.config.StormConfig;
import dev.stormward.network.StormPayload;
import dev.stormward.sim.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import java.util.*;

public final class StormManager {
    public static final TagKey<Block> FRAGILE = TagKey.of(RegistryKeys.BLOCK, Identifier.of("stormward", "fragile"));
    public static final TagKey<Block> PROTECTED = TagKey.of(RegistryKeys.BLOCK, Identifier.of("stormward", "protected"));
    private static final Map<ServerWorld, RuntimeState> RUNTIME = new WeakHashMap<>();
    private record Cover(BlockPos pos, long tick, double exposure) {}
    private static final class RuntimeState {
        long ticks, nanos, maxNanos, blockAttempts, broken, entities;
        int cursor;
        final LinkedHashMap<UUID, Cover> cover = new LinkedHashMap<>();
    }
    private StormManager() {}
    public static boolean loaded(ServerWorld world, int x, int z) {
        // getChunk(..., false) never creates tickets or loads a missing chunk.
        return world.getChunkManager().getWorldChunk(x >> 4, z >> 4) != null;
    }
    public static double exposure(ServerWorld world, LivingEntity entity) {
        return exposureAt(world, entity.getBlockPos(), entity.getUuid());
    }
    private static double exposureAt(ServerWorld world, BlockPos pos, UUID id) {
        RuntimeState runtime = RUNTIME.computeIfAbsent(world, w -> new RuntimeState());
        Cover cached = runtime.cover.get(id);
        if (cached != null && cached.pos.equals(pos) && world.getTime() - cached.tick < 10) return cached.exposure;
        double exposure = Shelter.evaluate((x, y, z) -> {
            if (!loaded(world, x, z)) return true; // unknown terrain is conservatively sheltered
            BlockPos sample = new BlockPos(x, y, z);
            return world.getBlockState(sample).isSideSolidFullSquare(world, sample, Direction.UP);
        }, pos.getX(), pos.getY(), pos.getZ()).exposure();
        if (runtime.cover.size() >= 512) runtime.cover.remove(runtime.cover.keySet().iterator().next());
        runtime.cover.put(id, new Cover(pos.toImmutable(), world.getTime(), exposure));
        return exposure;
    }
    public static void tick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) return;
        long start = System.nanoTime();
        RuntimeState runtime = RUNTIME.computeIfAbsent(world, w -> new RuntimeState());
        StormSavedData data = StormSavedData.get(world);
        if (data.incompatibleSchema()) return;
        StormConfig config = Stormward.CONFIG;
        if (data.storms.removeIf(s -> !world.getWorldBorder().contains(BlockPos.ofFloored(s.x() + s.velocityX(), 0, s.z() + s.velocityZ())))) {
            Set<UUID> ids = new HashSet<>(); data.storms.forEach(s -> ids.add(s.id()));
            data.brokenCounts.keySet().retainAll(ids); data.markDirty();
        }
        natural(world, data);
        int count = data.storms.size();
        int left = config.entityBudgetPerTick;
        // Rotate order so overloaded storms share the global dimension budget.
        for (int i = 0; i < count; i++) {
            Storm storm = data.storms.get(Math.floorMod(runtime.cursor + i, count));
            if (!storm.active()) continue;
            int x = MathHelper.floor(storm.x()), z = MathHelper.floor(storm.z());
            boolean active = loaded(world, x, z) && loaded(world, MathHelper.floor(storm.x() + storm.velocityX()), MathHelper.floor(storm.z() + storm.velocityZ()));
            if (!active) continue;
            StormStage previous = storm.stage();
            storm.tick(true); data.markDirty();
            if (storm.stage() != previous && config.warningMessages) announce(world, storm);
            int quota = Math.min(left, Math.max(1, config.entityBudgetPerTick / Math.max(1, count)));
            if (quota > 0) {
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                double range = storm.radius() * 2;
                Box box = new Box(storm.x() - range, y - 8, storm.z() - range, storm.x() + range, y + 96, storm.z() + range);
                List<LivingEntity> entities = new ArrayList<>(quota);
                // Collect first, then filter: rejected creative/spectator entities must not
                // make the engine enumerate an unlimited number of additional candidates.
                world.collectEntitiesByType(TypeFilter.instanceOf(LivingEntity.class), box,
                    e -> true, entities, quota);
                left -= entities.size(); runtime.entities += entities.size();
                for (LivingEntity entity : entities) {
                    if (!entity.isAlive() || entity.isSpectator()
                        || (entity instanceof PlayerEntity p && (!config.affectPlayers || p.isCreative()))) continue;
                    if (!world.getWorldBorder().contains(entity.getBlockPos())) continue;
                    Wind.Force force = Wind.sample(storm, entity.getX(), entity.getY(), entity.getZ(), y, exposure(world, entity));
                    double factor = config.windMultiplier * (entity.isSneaking() ? 0.45 : 1);
                    if (factor <= 0 || force.length() == 0) continue;
                    Vec3d v = entity.getVelocity();
                    // Cap storm acceleration without clipping existing vanilla falls/knockback.
                    entity.setVelocity(boundedAdd(v.x, force.x() * factor, 1.2), boundedAdd(v.y, force.y() * factor, 0.8), boundedAdd(v.z, force.z() * factor, 1.2));
                    entity.velocityModified = true;
                    if (entity instanceof ServerPlayerEntity player) player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
                }
            }
        }
        if (count > 0) {
            Storm chosen = data.storms.get(Math.floorMod(runtime.cursor++, count));
            if (config.destruction && chosen.stage() == StormStage.TORNADO && loaded(world, MathHelper.floor(chosen.x()), MathHelper.floor(chosen.z()))) {
                for (int i = 0; i < config.blockAttemptsPerTick; i++) {
                    runtime.blockAttempts++;
                    int x = MathHelper.floor(chosen.x() + (world.random.nextDouble() * 2 - 1) * chosen.radius());
                    int z = MathHelper.floor(chosen.z() + (world.random.nextDouble() * 2 - 1) * chosen.radius());
                    if (!loaded(world, x, z)) continue;
                    int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                    if (tryDestroy(world, chosen, new BlockPos(x, y, z))) runtime.broken++;
                }
            }
        }
        if (data.storms.removeIf(s -> !s.active())) {
            Set<UUID> ids = new HashSet<>(); data.storms.forEach(s -> ids.add(s.id()));
            data.brokenCounts.keySet().retainAll(ids); data.markDirty();
        }
        if (world.getTime() % 20 == 0) sync(world, data);
        long elapsed = System.nanoTime() - start;
        runtime.ticks++; runtime.nanos += elapsed; runtime.maxNanos = Math.max(runtime.maxNanos, elapsed);
    }
    private static double boundedAdd(double current, double delta, double limit) {
        double bound = Math.max(limit, Math.abs(current));
        return Math.clamp(current + delta, -bound, bound);
    }
    public static boolean canDestroy(ServerWorld world, Storm storm, BlockPos pos) {
        StormConfig c = Stormward.CONFIG;
        StormSavedData data = StormSavedData.get(world);
        if (!c.destruction || data.destructionLocked || storm.stage() != StormStage.TORNADO || !storm.active()
            || pos.getY() < c.destructionMinY || pos.getY() > c.destructionMaxY
            || !world.getWorldBorder().contains(pos) || !loaded(world, pos.getX(), pos.getZ())
            || pos.getSquaredDistance(storm.x(), pos.getY(), storm.z()) > storm.radius() * storm.radius()
            || data.brokenCounts.getOrDefault(storm.id(), 0) >= c.maxBlocksPerStorm
            || (c.protectPlayerBuilds && data.protectedChunks.contains(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4)))) return false;
        BlockState state = world.getBlockState(pos);
        float hardness = state.getHardness(world, pos);
        return state.isIn(FRAGILE) && !state.isIn(PROTECTED) && !state.hasBlockEntity() && state.getFluidState().isEmpty()
            && hardness >= 0 && hardness <= c.maxBlockHardness;
    }
    public static boolean tryDestroy(ServerWorld world, Storm storm, BlockPos pos) {
        if (!canDestroy(world, storm, pos)) return false;
        // No drops, block entities, falling blocks, or debris entities: no loot duplication path.
        if (!world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS, 0)) return false;
        StormSavedData data = StormSavedData.get(world);
        data.brokenCounts.merge(storm.id(), 1, Integer::sum); data.markDirty(); return true;
    }
    private static void natural(ServerWorld world, StormSavedData data) {
        if (world.getTime() < data.nextNaturalTick) return;
        data.nextNaturalTick = world.getTime() + Stormward.CONFIG.naturalIntervalTicks; data.markDirty();
        if (!Stormward.CONFIG.naturalStorms || data.storms.size() >= Stormward.CONFIG.maxStorms || world.getPlayers().isEmpty()
            || world.random.nextDouble() > Stormward.CONFIG.naturalChance) return;
        ServerPlayerEntity p = world.getPlayers().get(world.random.nextInt(world.getPlayers().size()));
        double angle = world.random.nextDouble() * Math.PI * 2;
        double x = p.getX() + Math.cos(angle) * 128, z = p.getZ() + Math.sin(angle) * 128;
        if (!loaded(world, MathHelper.floor(x), MathHelper.floor(z)) || !world.getWorldBorder().contains(BlockPos.ofFloored(x, p.getY(), z))) return;
        double speed = 0.015 + world.random.nextDouble() * 0.025;
        data.storms.add(Storm.create(UUID.randomUUID(), x, z, -Math.cos(angle) * speed, -Math.sin(angle) * speed,
            20 + world.random.nextInt(25), 0.7 + world.random.nextDouble() * 1.3, world.random.nextBoolean() ? 1 : 2)); data.markDirty();
    }
    private static void announce(ServerWorld world, Storm storm) {
        for (ServerPlayerEntity player : world.getPlayers()) if (Math.hypot(player.getX() - storm.x(), player.getZ() - storm.z()) <= 384)
            player.sendMessage(Text.literal("Stormward • " + storm.stage().name() + (storm.stage() == StormStage.WARNING ? " — seek enclosed cover" : "")), false);
    }
    public static void sync(ServerWorld world, StormSavedData data) {
        List<StormPayload.Frame> frames = new ArrayList<>();
        for (Storm s : data.storms) {
            if (!loaded(world, MathHelper.floor(s.x()), MathHelper.floor(s.z()))) continue;
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(s.x()), MathHelper.floor(s.z()));
            frames.add(new StormPayload.Frame(s.id(), s.x(), y, s.z(), s.velocityX(), s.velocityZ(), (float)s.radius(), (float)s.intensity(), s.stage().ordinal(), s.stageRemainingTicks()));
            if (frames.size() == StormPayload.MAX_STORMS) break;
        }
        for (ServerPlayerEntity p : world.getPlayers()) if (ServerPlayNetworking.canSend(p, StormPayload.ID)) {
            List<StormPayload.Frame> nearby = frames.stream().filter(f -> Math.hypot(f.x() - p.getX(), f.z() - p.getZ()) <= 512).toList();
            ServerPlayNetworking.send(p, new StormPayload(nearby, (float)(nearby.isEmpty() ? 1 : exposure(world, p)), Stormward.CONFIG.entityBudgetPerTick));
        }
    }
    public static String stats(ServerWorld world) {
        RuntimeState r = RUNTIME.computeIfAbsent(world, w -> new RuntimeState());
        return String.format(Locale.ROOT, "%d storms | mean %.3f ms, max %.3f ms over %d ticks | entity visits %d | block attempts %d, removed %d | protected chunks %d | lock %s",
            StormSavedData.get(world).storms.size(), r.nanos / Math.max(1.0, r.ticks) / 1e6, r.maxNanos / 1e6, r.ticks, r.entities, r.blockAttempts, r.broken,
            StormSavedData.get(world).protectedChunks.size(), StormSavedData.get(world).destructionLocked);
    }
    public static void clearRuntime() { RUNTIME.clear(); }
}
