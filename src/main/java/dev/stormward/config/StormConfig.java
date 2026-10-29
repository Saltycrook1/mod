package dev.stormward.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StormConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean naturalStorms = true;
    public int naturalIntervalTicks = 24000;
    public double naturalChance = 0.35;
    public int maxStorms = 3;
    public int entityBudgetPerTick = 48;
    public int blockAttemptsPerTick = 12;
    public int maxBlocksPerStorm = 256;
    public boolean destruction = false;
    public boolean protectPlayerBuilds = true;
    public double maxBlockHardness = 0.8;
    public int destructionMinY = -64;
    public int destructionMaxY = 320;
    public double windMultiplier = 1.0;
    public boolean affectPlayers = true;
    public boolean warningMessages = true;
    public boolean sirenSounds = true;
    public int maxProtectedChunks = 65536;

    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("stormward.json"); }
    public static StormConfig load() throws IOException {
        StormConfig value;
        if (Files.exists(path())) {
            try (var reader = Files.newBufferedReader(path())) { value = GSON.fromJson(reader, StormConfig.class); }
            catch (RuntimeException e) { throw new IOException("Invalid stormward.json; refusing unsafe defaults", e); }
            if (value == null) throw new IOException("stormward.json must be an object");
        } else { value = new StormConfig(); value.save(); }
        value.validate();
        return value;
    }
    public void validate() {
        maxStorms = Math.clamp(maxStorms, 1, 8);
        naturalIntervalTicks = Math.clamp(naturalIntervalTicks, 1200, 1728000);
        naturalChance = finite(naturalChance, 0.35, 0, 1);
        entityBudgetPerTick = Math.clamp(entityBudgetPerTick, 1, 128);
        blockAttemptsPerTick = Math.clamp(blockAttemptsPerTick, 0, 64);
        maxBlocksPerStorm = Math.clamp(maxBlocksPerStorm, 0, 4096);
        maxBlockHardness = finite(maxBlockHardness, 0.8, 0, 3);
        windMultiplier = finite(windMultiplier, 1, 0, 2);
        destructionMinY = Math.clamp(destructionMinY, -64, 320);
        destructionMaxY = Math.clamp(destructionMaxY, destructionMinY, 320);
        maxProtectedChunks = Math.clamp(maxProtectedChunks, 256, 65536);
    }
    private static double finite(double n, double fallback, double min, double max) {
        return Double.isFinite(n) ? Math.clamp(n, min, max) : fallback;
    }
    public void save() throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), GSON.toJson(this) + "\n");
    }
}
