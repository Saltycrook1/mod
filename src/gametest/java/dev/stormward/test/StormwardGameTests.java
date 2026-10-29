package dev.stormward.test;

import dev.stormward.Stormward;
import dev.stormward.block.StormSirenBlock;
import dev.stormward.config.StormConfig;
import dev.stormward.network.StormPayload;
import dev.stormward.server.StormManager;
import dev.stormward.server.StormSavedData;
import dev.stormward.sim.Shelter;
import dev.stormward.sim.Storm;
import dev.stormward.sim.StormStage;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** A single ordered fixture prevents world/config mutations from racing between tests. */
public final class StormwardGameTests implements FabricGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void dedicatedServerIntegration(TestContext context) {
        ServerWorld world = context.getWorld();
        StormConfig previous = Stormward.CONFIG;
        StormSavedData data = StormSavedData.get(world);
        var previousStorms = List.copyOf(data.storms);
        var previousBroken = new java.util.HashMap<>(data.brokenCounts);
        var previousChunks = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(data.protectedChunks);
        boolean previousLocked = data.destructionLocked;
        long previousNaturalTick = data.nextNaturalTick;
        List<CowEntity> cows = new ArrayList<>();
        try {
            StormConfig config = new StormConfig();
            config.naturalStorms = false;
            config.warningMessages = false;
            config.sirenSounds = false;
            config.maxStorms = 8;
            Stormward.CONFIG = config;
            data.storms.clear(); data.brokenCounts.clear(); data.protectedChunks.clear(); data.destructionLocked = false;
            BlockPos origin = context.getAbsolutePos(new BlockPos(3, 2, 3));
            persistence(context, world, origin);
            packet(context, world);
            shelter(context, world, origin);
            destruction(context, world, origin, config);
            siren(context, world, origin);
            boundedQuery(context, world, origin, cows);
            zeroForcePreservesVelocity(context, world, origin, config);
            unloadedFreeze(context, world, origin);
            benchmark(context, world, origin, cows);
            Stormward.LOGGER.info("STORMWARD_INTEGRATION_PASS: NBT, packet bounds/roundtrip, shelter, destruction, siren redstone, bounded query, unloaded pause, benchmarks");
            context.complete();
        } finally {
            for (CowEntity cow : cows) cow.discard();
            data.storms.clear(); data.storms.addAll(previousStorms);
            data.brokenCounts.clear(); data.brokenCounts.putAll(previousBroken);
            data.protectedChunks.clear(); data.protectedChunks.addAll(previousChunks);
            data.destructionLocked = previousLocked;
            data.nextNaturalTick = previousNaturalTick;
            Stormward.CONFIG = previous;
        }
    }

    private static Storm tornado(BlockPos at) {
        return new Storm(new Storm.State(UUID.randomUUID(), at.getX() + 0.5, at.getZ() + 0.5,
                0, 0, 32, 2.5, 3200, 1));
    }

    private static void persistence(TestContext c, ServerWorld world, BlockPos at) {
        StormSavedData data = new StormSavedData();
        Storm storm = tornado(at);
        data.storms.add(storm); data.brokenCounts.put(storm.id(), 7);
        data.protectedChunks.add(ChunkPos.toLong(at.getX() >> 4, at.getZ() >> 4));
        data.destructionLocked = true;
        NbtCompound encoded = data.writeNbt(new NbtCompound(), world.getRegistryManager());
        StormSavedData decoded = StormSavedData.readNbt(encoded, world.getRegistryManager());
        c.assertTrue(decoded.storms.size() == 1, "One storm must survive NBT roundtrip");
        c.assertTrue(decoded.storms.getFirst().state().equals(storm.state()), "Every storm state field must survive NBT");
        c.assertTrue(decoded.brokenCounts.get(storm.id()) == 7, "Destruction count must persist across reload");
        c.assertTrue(decoded.protectedChunks.equals(data.protectedChunks), "Protected chunks must persist");
        c.assertTrue(decoded.destructionLocked, "Protection overflow lock must persist");
        // Resumption must remain deterministic after serialization.
        storm.tick(true); decoded.storms.getFirst().tick(true);
        c.assertTrue(decoded.storms.getFirst().state().equals(storm.state()), "Reloaded storm must resume identically");
        NbtCompound unknown = new NbtCompound(); unknown.putInt("schema", 999);
        NbtCompound futureFields = new NbtCompound(); futureFields.putString("futureField", "preserve this unknown data");
        unknown.put("futurePayload", futureFields); unknown.putLongArray("futureArray", new long[]{1, 3, 7});
        StormSavedData future = StormSavedData.readNbt(unknown, world.getRegistryManager());
        c.assertTrue(future.storms.isEmpty() && future.destructionLocked, "Unknown persistence schemas must fail closed");
        c.assertTrue(future.incompatibleSchema(), "Unknown schema must suspend simulation");
        c.assertTrue(future.writeNbt(new NbtCompound(), world.getRegistryManager()).equals(unknown),
                "Unknown schema must preserve its full original raw NBT on save");
    }

    private static void packet(TestContext c, ServerWorld world) {
        List<StormPayload.Frame> frames = new ArrayList<>();
        for (int i = 0; i < 8; i++) frames.add(new StormPayload.Frame(UUID.randomUUID(), 12.5 + i, 80, -4.5,
                0.05, -0.02, 32, 2.5f, i % 5, 280));
        StormPayload sent = new StormPayload(List.copyOf(frames), 0.25f, 48);
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), world.getRegistryManager());
        try {
            StormPayload.CODEC.encode(buffer, sent);
            int bytes = buffer.readableBytes();
            c.assertTrue(StormPayload.CODEC.decode(buffer).equals(sent), "Multiplayer snapshot codec must roundtrip eight storms");
            c.assertTrue(buffer.readableBytes() == 0, "Snapshot must consume its full packet");
            c.assertTrue(bytes < 1024, "Maximum snapshot must stay below one KiB");
            buffer.clear(); buffer.writeVarInt(9);
            boolean rejected = false;
            try { StormPayload.CODEC.decode(buffer); } catch (IllegalArgumentException expected) { rejected = true; }
            c.assertTrue(rejected, "Nine-storm packet must be rejected before allocation");
            buffer.clear(); buffer.writeVarInt(-1); rejected = false;
            try { StormPayload.CODEC.decode(buffer); } catch (IllegalArgumentException expected) { rejected = true; }
            c.assertTrue(rejected, "Negative snapshot length must be rejected");
            Stormward.LOGGER.info("STORMWARD_PACKET maximumSnapshotBytes={}", bytes);
        } finally { buffer.release(); }
    }

    private static void shelter(TestContext c, ServerWorld world, BlockPos origin) {
        BlockPos at = origin.up(24);
        // GameTest may start below surrounding terrain. Every ray must stay inside
        // this known loaded, cleared fixture volume so external terrain cannot seal a breach.
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            c.assertTrue(StormManager.loaded(world, at.getX() + x, at.getZ() + z), "Shelter fixture must only use already loaded chunks");
            for (int y = 0; y <= 8; y++) world.setBlockState(at.add(x, y, z), Blocks.AIR.getDefaultState(), 2);
        }
        // Solid 7x7 roof, four two-block walls, then remove one entire wall.
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            for (int y = 0; y <= 3; y++) world.setBlockState(at.add(x, y, z), Blocks.AIR.getDefaultState(), 2);
            world.setBlockState(at.add(x, 3, z), Blocks.STONE.getDefaultState(), 2);
            if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                world.setBlockState(at.add(x, 0, z), Blocks.STONE.getDefaultState(), 2);
                world.setBlockState(at.add(x, 1, z), Blocks.STONE.getDefaultState(), 2);
            }
        }
        Shelter.BlockQuery query = (x, y, z) -> {
            BlockPos pos = new BlockPos(x, y, z);
            return !StormManager.loaded(world, x, z) || world.getBlockState(pos).isSideSolidFullSquare(world, pos, Direction.UP);
        };
        Shelter.Result enclosed = Shelter.evaluate(query, at.getX(), at.getY(), at.getZ());
        CowEntity enclosedObserver = EntityType.COW.create(world);
        c.assertTrue(enclosedObserver != null, "Shelter observer must exist");
        enclosedObserver.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        double enclosedServer = StormManager.exposure(world, enclosedObserver);
        c.assertTrue(Math.abs(enclosedServer - enclosed.exposure()) < 1e-9, "Server exposure must use the same complete shelter geometry");
        c.assertTrue(enclosed.exposure() <= 0.2, "Enclosed solid shelter must protect occupants");
        for (int z = -3; z <= 3; z++) for (int y = 0; y < 2; y++) world.setBlockState(at.add(3, y, z), Blocks.AIR.getDefaultState(), 2);
        Shelter.Result breached = Shelter.evaluate(query, at.getX(), at.getY(), at.getZ());
        CowEntity breachedObserver = EntityType.COW.create(world);
        c.assertTrue(breachedObserver != null, "Breached shelter observer must exist");
        breachedObserver.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        double breachedServer = StormManager.exposure(world, breachedObserver);
        Stormward.LOGGER.info("STORMWARD_SHELTER enclosed exposure={} roof={} walls={} server={}; breached exposure={} roof={} walls={} server={}",
                enclosed.exposure(), enclosed.roofCoverage(), enclosed.wallCoverage(), enclosedServer,
                breached.exposure(), breached.roofCoverage(), breached.wallCoverage(), breachedServer);
        c.assertTrue(Math.abs(breachedServer - breached.exposure()) < 1e-9, "Server exposure must use the same breached shelter geometry");
        c.assertTrue(breached.exposure() > enclosed.exposure(), "A breached wall must increase exposure");
        c.assertTrue(breached.samples() <= Shelter.MAX_SAMPLES, "Shelter probes must stay within budget");
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = 0; y <= 3; y++) world.setBlockState(at.add(x, y, z), Blocks.AIR.getDefaultState(), 2);
    }

    private static void zeroForcePreservesVelocity(TestContext c, ServerWorld world, BlockPos at, StormConfig config) {
        StormSavedData data = StormSavedData.get(world);
        CowEntity cow = spawn(world, at, 0);
        Vec3d original = new Vec3d(3, -2, 4);
        double previousMultiplier = config.windMultiplier;
        try {
            cow.setVelocity(original);
            data.storms.add(tornado(at));
            config.windMultiplier = 0;
            StormManager.tick(world);
            c.assertTrue(cow.getVelocity().equals(original), "Zero wind multiplier must preserve existing vanilla velocity above storm caps");
            data.storms.clear();
            config.windMultiplier = 1;
            Storm tiny = new Storm(new Storm.State(UUID.randomUUID(), at.getX() + 10.5, at.getZ() + 10.5, 0, 0, 8, 2, 3200, 1));
            c.assertTrue(StormManager.loaded(world, (int)Math.floor(tiny.x()), (int)Math.floor(tiny.z())),
                    "Zero-force fixture storm must actually be loaded");
            data.storms.add(tiny);
            cow.setVelocity(original);
            StormManager.tick(world);
            c.assertTrue(cow.getVelocity().equals(original), "Zero sampled force outside influence must preserve existing vanilla velocity");
        } finally {
            cow.discard(); data.storms.clear(); config.windMultiplier = previousMultiplier;
        }
    }

    private static void destruction(TestContext c, ServerWorld world, BlockPos at, StormConfig config) {
        StormSavedData data = StormSavedData.get(world);
        Storm storm = tornado(at);
        BlockPos pos = at.add(1, 0, 0);
        world.setBlockState(pos, Blocks.OAK_LEAVES.getDefaultState(), 2);
        c.assertFalse(StormManager.canDestroy(world, storm, pos), "Default config must disable destruction");
        config.destruction = true; config.maxBlocksPerStorm = 1;
        c.assertTrue(StormManager.canDestroy(world, storm, pos), "Eligible leaves may be destroyed after opt-in");
        c.assertTrue(StormManager.tryDestroy(world, storm, pos), "Actual eligible block must be removed");
        c.assertTrue(world.getBlockState(pos).isAir(), "Destroyed block must become air");
        world.setBlockState(pos, Blocks.OAK_LEAVES.getDefaultState(), 2);
        c.assertFalse(StormManager.canDestroy(world, storm, pos), "Lifetime destruction budget must stop further damage");
        data.brokenCounts.clear(); config.maxBlocksPerStorm = 256;
        world.setBlockState(pos, Blocks.CHEST.getDefaultState(), 2);
        c.assertFalse(StormManager.canDestroy(world, storm, pos), "Containers must always be protected");
        world.setBlockState(pos, Blocks.BEDROCK.getDefaultState(), 2);
        c.assertFalse(StormManager.canDestroy(world, storm, pos), "Unbreakable blocks must always be protected");
        world.setBlockState(pos, Stormward.REINFORCED.getDefaultState(), 2);
        c.assertFalse(StormManager.canDestroy(world, storm, pos), "Reinforced shelter must always be protected");
        world.setBlockState(pos, Blocks.OAK_LEAVES.getDefaultState(), 2);
        long chunk = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        data.protectedChunks.add(chunk);
        c.assertFalse(StormManager.canDestroy(world, storm, pos), "Protected player-build chunks must resist damage");
        data.protectedChunks.clear(); data.destructionLocked = true;
        c.assertFalse(StormManager.canDestroy(world, storm, pos), "Protection overflow lock must fail closed");
        data.destructionLocked = false;
        int oldProtectionCap = config.maxProtectedChunks;
        config.maxProtectedChunks = 256;
        for (int i = 0; i < 256; i++) data.protect(ChunkPos.toLong(i, 1000));
        data.protect(ChunkPos.toLong(9999, 1000));
        c.assertTrue(data.destructionLocked && data.protectedChunks.size() == 256,
                "Protection overflow must lock destruction without growing metadata past its cap");
        data.protectedChunks.clear(); data.destructionLocked = false; config.maxProtectedChunks = oldProtectionCap;
        config.destructionMaxY = pos.getY() - 1;
        c.assertFalse(StormManager.canDestroy(world, storm, pos), "Configured Y boundary must be honored");
        config.destructionMaxY = 320; config.destruction = false;
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
        c.assertTrue(world.getEntitiesByType(EntityType.ITEM, new Box(at).expand(8), e -> true).isEmpty(), "Storm destruction must not spawn duplicate drops");
        data.brokenCounts.clear();
    }

    private static void boundedQuery(TestContext c, ServerWorld world, BlockPos at, List<CowEntity> cows) {
        for (int i = 0; i < 64; i++) cows.add(spawn(world, at, i));
        List<LivingEntity> found = new ArrayList<>();
        world.collectEntitiesByType(TypeFilter.instanceOf(LivingEntity.class), new Box(at).expand(8), e -> true, found, 12);
        c.assertTrue(found.size() == 12, "Loaded-world spatial query must honor collection limit");
        for (CowEntity cow : cows) cow.discard();
        cows.clear();
    }

    private static void siren(TestContext c, ServerWorld world, BlockPos at) {
        StormSavedData data = StormSavedData.get(world);
        BlockPos pos = at.add(2, 0, 0);
        world.setBlockState(pos, Stormward.SIREN.getDefaultState(), 2);
        var idle = world.getBlockState(pos);
        c.assertFalse(idle.get(StormSirenBlock.POWERED), "New siren must be idle");
        c.assertTrue(idle.getWeakRedstonePower(world, pos, Direction.NORTH) == 0, "Idle siren must output no redstone");
        Storm warning = new Storm(new Storm.State(UUID.randomUUID(), at.getX(), at.getZ(), 0, 0, 32, 2.5, 2500, 1));
        c.assertTrue(warning.stage() == StormStage.WARNING, "Siren fixture must be in warning stage");
        data.storms.add(warning);
        idle.scheduledTick(world, pos, world.random);
        var active = world.getBlockState(pos);
        c.assertTrue(active.get(StormSirenBlock.POWERED), "Loaded nearby warning must power siren on scheduled tick");
        c.assertTrue(active.getWeakRedstonePower(world, pos, Direction.NORTH) == 15, "Warning siren must output redstone level 15");
        data.storms.clear();
        active.scheduledTick(world, pos, world.random);
        c.assertFalse(world.getBlockState(pos).get(StormSirenBlock.POWERED), "Stopping storms must reset siren on next scheduled tick");
        c.assertTrue(world.getBlockState(pos).getWeakRedstonePower(world, pos, Direction.NORTH) == 0, "Stopped siren must output zero");
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
    }

    private static void unloadedFreeze(TestContext c, ServerWorld world, BlockPos at) {
        StormSavedData data = StormSavedData.get(world);
        Storm far = Storm.create(UUID.randomUUID(), 25_000_000, 25_000_000, 0.05, 0, 32, 2.5, 1);
        c.assertFalse(world.isChunkLoaded(new BlockPos(25_000_000, at.getY(), 25_000_000)), "Remote fixture must begin unloaded");
        data.storms.add(far);
        Storm.State original = far.state();
        StormManager.tick(world);
        c.assertTrue(far.state().equals(original), "Unloaded storms must freeze lifecycle and movement");
        c.assertFalse(world.isChunkLoaded(new BlockPos(25_000_000, at.getY(), 25_000_000)), "Simulation must not load distant chunks");
        data.storms.clear();
    }

    private static CowEntity spawn(ServerWorld world, BlockPos at, int i) {
        CowEntity cow = EntityType.COW.create(world);
        if (cow == null) throw new IllegalStateException("Cow factory returned null");
        cow.setAiDisabled(true); cow.setNoGravity(true); cow.setInvulnerable(true);
        cow.refreshPositionAndAngles(at.getX() + (i % 8) * 0.5 - 1.5, at.getY(), at.getZ() + ((i / 8) % 8) * 0.5 - 1.5, 0, 0);
        if (!world.spawnEntity(cow)) throw new IllegalStateException("Benchmark cow failed to spawn");
        return cow;
    }

    private static void benchmark(TestContext c, ServerWorld world, BlockPos at, List<CowEntity> cows) {
        StringBuilder report = new StringBuilder();
        report.append("# Headless loaded-world adapter burst benchmark\n\n");
        report.append("Java: ").append(System.getProperty("java.runtime.version")).append(" (").append(System.getProperty("java.vm.name")).append(")\n\n");
        report.append("OS: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append(' ').append(System.getProperty("os.arch")).append("\n\n");
        report.append("CPU environment: ").append(System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unknown")).append("; logical processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n\n");
        report.append("Heap max bytes: ").append(Runtime.getRuntime().maxMemory()).append("; JVM args: ").append(ManagementFactory.getRuntimeMXBean().getInputArguments()).append("\n\n");
        report.append("Minecraft 1.21.1; Fabric Loader 0.20.1; Fabric API 0.116.7+1.21.1; all storms stationary radius 32 strength 2.5. Destruction off, 48 entity processing budget, no connected clients.\n\n");
        report.append("100 warmup calls + 400 measured calls per scenario. Times cover StormManager.tick only; calls run back-to-back at fixed world time. This measures a loaded-world adapter burst, not whole-server MSPT/TPS, rendering FPS, allocation, network delivery, or a competitor comparison. Entity physics does not advance during a scenario, and caches stay hot.\n\n");
        report.append("| Storms | Cows | p50 ms | p95 ms | max ms |\n|---:|---:|---:|---:|---:|\n");
        StormSavedData data = StormSavedData.get(world);
        for (int entityCount : new int[]{0, 64, 256}) {
            for (CowEntity cow : cows) cow.discard(); cows.clear();
            for (int i = 0; i < entityCount; i++) cows.add(spawn(world, at, i));
            for (int stormCount : new int[]{0, 1, 3, 8}) {
                data.storms.clear(); data.brokenCounts.clear();
                for (int i = 0; i < stormCount; i++) data.storms.add(tornado(at));
                for (int i = 0; i < 100; i++) StormManager.tick(world);
                long[] elapsed = new long[400];
                for (int i = 0; i < elapsed.length; i++) {
                    long start = System.nanoTime();
                    StormManager.tick(world);
                    elapsed[i] = System.nanoTime() - start;
                }
                Arrays.sort(elapsed);
                report.append(String.format(java.util.Locale.ROOT, "| %d | %d | %.6f | %.6f | %.6f |%n", stormCount, entityCount,
                        elapsed[199] / 1_000_000.0, elapsed[379] / 1_000_000.0, elapsed[399] / 1_000_000.0));
            }
        }
        try {
            Path target = Path.of("..", "adapter-benchmark.md").toAbsolutePath().normalize();
            Files.writeString(target, report.toString());
            Stormward.LOGGER.info("STORMWARD_BENCHMARK {}", target);
        } catch (IOException e) { throw new IllegalStateException("Cannot write benchmark artifact", e); }
        c.assertTrue(cows.size() == 256, "Stress fixture must actually contain 256 cows");
        data.storms.clear();
    }
}
