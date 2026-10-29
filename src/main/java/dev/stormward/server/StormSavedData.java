package dev.stormward.server;

import dev.stormward.Stormward;
import dev.stormward.sim.Storm;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import java.util.*;

public final class StormSavedData extends PersistentState {
    public final List<Storm> storms = new ArrayList<>();
    public final Map<UUID, Integer> brokenCounts = new HashMap<>();
    public final LongOpenHashSet protectedChunks = new LongOpenHashSet();
    public boolean destructionLocked;
    public long nextNaturalTick;
    private NbtCompound unknownSchema;
    public boolean incompatibleSchema() { return unknownSchema != null; }
    private static final Type<StormSavedData> TYPE = new Type<>(StormSavedData::new, StormSavedData::readNbt, null);

    public static StormSavedData get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, "stormward_storms");
    }
    public static StormSavedData readNbt(NbtCompound root, RegistryWrapper.WrapperLookup lookup) {
        StormSavedData data = new StormSavedData();
        // Unknown schemas fail closed for destruction and retain no unsafe simulation data.
        if (root.getInt("schema") != 1) {
            data.destructionLocked = true; data.unknownSchema = root.copy();
            Stormward.LOGGER.error("Unknown Stormward save schema {}; preserving data and suspending simulation", root.getInt("schema"));
            return data;
        }
        NbtList list = root.getList("storms", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < Math.min(list.size(), 8); i++) {
            NbtCompound n = list.getCompound(i);
            try {
                Storm storm = new Storm(new Storm.State(n.getUuid("id"), n.getDouble("x"), n.getDouble("z"),
                    n.getDouble("vx"), n.getDouble("vz"), n.getDouble("radius"), n.getDouble("strength"),
                    n.getInt("age"), n.getInt("divisor")));
                if (storm.active() && !data.brokenCounts.containsKey(storm.id())) {
                    data.storms.add(storm); data.brokenCounts.put(storm.id(), Math.clamp(n.getInt("broken"), 0, 4096));
                }
            } catch (RuntimeException e) { Stormward.LOGGER.warn("Skipping invalid persisted storm {}", i); }
        }
        long[] chunks = root.getLongArray("protectedChunks");
        data.destructionLocked = root.getBoolean("destructionLocked") || chunks.length > 65536;
        for (int i = 0; i < Math.min(chunks.length, 65536); i++) data.protectedChunks.add(chunks[i]);
        data.nextNaturalTick = Math.max(0, root.getLong("nextNaturalTick"));
        return data;
    }
    @Override public NbtCompound writeNbt(NbtCompound root, RegistryWrapper.WrapperLookup lookup) {
        if (unknownSchema != null) return unknownSchema.copy();
        root.putInt("schema", 1);
        NbtList list = new NbtList();
        for (Storm s : storms) {
            NbtCompound n = new NbtCompound();
            n.putUuid("id", s.id()); n.putDouble("x", s.x()); n.putDouble("z", s.z());
            n.putDouble("vx", s.velocityX()); n.putDouble("vz", s.velocityZ());
            n.putDouble("radius", s.radius()); n.putDouble("strength", s.strength());
            n.putInt("age", s.ageTicks()); n.putInt("divisor", s.timeDivisor());
            n.putInt("broken", brokenCounts.getOrDefault(s.id(), 0)); list.add(n);
        }
        root.put("storms", list); root.putLongArray("protectedChunks", protectedChunks.toLongArray());
        root.putBoolean("destructionLocked", destructionLocked); root.putLong("nextNaturalTick", nextNaturalTick);
        return root;
    }
    public void protect(long chunk) {
        if (protectedChunks.contains(chunk)) return;
        if (protectedChunks.size() >= Stormward.CONFIG.maxProtectedChunks) destructionLocked = true;
        else protectedChunks.add(chunk);
        markDirty();
    }
}
