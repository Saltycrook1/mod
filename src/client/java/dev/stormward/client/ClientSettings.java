package dev.stormward.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local presentation preferences. None of these values change server simulation. */
final class ClientSettings {
    int particleBudget = 48;
    int liveParticleCap = 1024;
    int effectRange = 256;
    boolean windSound = true;
    boolean hud = true;
    double soundVolume = 0.35;

    static ClientSettings load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("stormward-client.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        ClientSettings settings = new ClientSettings();
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                ClientSettings loaded = gson.fromJson(reader, ClientSettings.class);
                if (loaded != null) settings = loaded;
            } catch (IOException | RuntimeException exception) {
                LoggerFactory.getLogger("stormward-client").warn("Cannot read {}; using local defaults", path, exception);
            }
        } else {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, gson.toJson(settings));
            } catch (IOException exception) {
                LoggerFactory.getLogger("stormward-client").warn("Cannot write local presentation settings", exception);
            }
        }
        settings.particleBudget = Math.clamp(settings.particleBudget, 0, 128);
        settings.liveParticleCap = Math.clamp(settings.liveParticleCap, 0, 2048);
        settings.effectRange = Math.clamp(settings.effectRange, 32, 512);
        settings.soundVolume = Double.isFinite(settings.soundVolume) ? Math.clamp(settings.soundVolume, 0.0, 1.0) : 0.35;
        return settings;
    }
}
