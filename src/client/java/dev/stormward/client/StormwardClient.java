package dev.stormward.client;

import dev.stormward.network.StormPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.Comparator;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/** Presentation only: remote snapshots cannot alter weather, blocks, or entity movement. */
public final class StormwardClient implements ClientModInitializer {
    private static final String[] STAGES = {"GATHERING", "STORM WATCH", "TORNADO WARNING", "TORNADO", "DISSIPATING"};
    private static final int[] COLORS = {0xff89a8b4, 0xfff3d579, 0xffff9c62, 0xffff6e72, 0xff89a8b4};
    private static final int MAX_SNAPSHOT_AGE = 100;
    private ClientSettings settings;
    private List<StormPayload.Frame> storms = List.of();
    private ClientWorld observedWorld;
    private long ticks;
    private long receivedAt = -MAX_SNAPSHOT_AGE;
    private float exposure = 1;
    private final ArrayDeque<Particle> particles = new ArrayDeque<>();

    @Override
    public void onInitializeClient() {
        settings = ClientSettings.load();
        ClientPlayNetworking.registerGlobalReceiver(StormPayload.ID, (payload, context) -> {
            // The Fabric payload callback runs on the client thread.
            observedWorld = context.client().world;
            storms = payload.storms().stream().limit(16).toList();
            exposure = Math.clamp(payload.exposure(), 0, 1);
            receivedAt = ticks;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register((draw, counter) -> drawHud(draw));
    }

    private void clear() {
        particles.forEach(Particle::markDead);
        particles.clear();
        storms = List.of();
        observedWorld = null;
        receivedAt = -MAX_SNAPSHOT_AGE;
        exposure = 1;
    }

    private void tick(MinecraftClient client) {
        ticks++;
        particles.removeIf(particle -> !particle.isAlive());
        if (client.world != observedWorld) {
            clear();
            observedWorld = client.world;
        }
        if (client.world == null || client.player == null || client.isPaused() || ticks - receivedAt >= MAX_SNAPSHOT_AGE) return;
        int budget = settings.liveParticleCap == 0 ? 0 : settings.particleBudget;
        ParticlesMode mode = client.options.getParticles().getValue();
        if (mode == ParticlesMode.MINIMAL) budget /= 4;
        if (mode == ParticlesMode.DECREASED) budget /= 2;
        var nearby = storms.stream()
                .filter(frame -> squaredDistance(client, frame) < (double) settings.effectRange * settings.effectRange)
                .sorted(Comparator.comparingDouble(frame -> squaredDistance(client, frame)))
                .toList();
        // A single cap covers every visible storm. Distance reduces detail before distribution.
        for (int index = 0; index < nearby.size() && budget > 0; index++) {
            StormPayload.Frame frame = nearby.get(index);
            double distance = Math.sqrt(squaredDistance(client, frame));
            int allocation = Math.max(1, budget / (nearby.size() - index));
            int detail = Math.max(1, (int) (allocation * Math.max(0.2, 1 - distance / settings.effectRange)));
            emitStorm(client, frame, Math.min(budget, detail));
            budget -= detail;
        }
        if (settings.windSound && settings.soundVolume > 0 && ticks % 100 == 0 && !nearby.isEmpty()) {
            StormPayload.Frame nearest = nearby.getFirst();
            double distance = Math.sqrt(squaredDistance(client, nearest));
            float volume = (float) (settings.soundVolume * Math.max(0, 1 - distance / settings.effectRange));
            if (volume > 0.02f) {
                // WEATHER and master sliders remain authoritative for all local storm audio.
                client.world.playSound(client.player.getX(), client.player.getY(), client.player.getZ(),
                        SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.WEATHER, volume, 0.65f, false);
            }
        }
    }

    private void emitStorm(MinecraftClient client, StormPayload.Frame frame, int budget) {
        double age = Math.min(40, ticks - receivedAt);
        double centerX = frame.x() + frame.velocityX() * age;
        double centerZ = frame.z() + frame.velocityZ() * age;
        double phase = ticks * (0.075 + frame.strength() * 0.025);
        double radius = Math.clamp(frame.radius(), 4.0f, 96.0f);
        int stage = Math.clamp(frame.stage(), 0, 4);
        double funnelHeight = 54 + radius * 0.4;
        for (int i = 0; i < budget; i++) {
            // The moving stratified samples fill all heights with several spiral strands.
            double fraction = ((i * 0.61803398875 + ticks * 0.031) % 1);
            double angle = phase + i * 2.3999632297 + fraction * 10;
            boolean funnel = stage == 3 || stage == 4 || (stage == 2 && fraction > 0.55);
            double height = funnel ? fraction * funnelHeight : 54 + fraction * 12;
            double width = funnel ? radius * (0.14 + Math.pow(fraction, 1.35) * 0.72) : radius * (0.9 + fraction * 0.6);
            double lean = fraction * fraction * 7;
            double x = centerX + Math.cos(angle) * width + lean * Math.sin(phase * 0.15);
            double z = centerZ + Math.sin(angle) * width;
            double y = frame.y() + height;
            double rotation = funnel ? 0.13 : 0.04;
            while (particles.size() >= settings.liveParticleCap) particles.removeFirst().markDead();
            // Direct creation supports distant funnels. The explicit local budgets and vanilla
            // particles preference above govern this effect, rather than the 32-block default cutoff.
            Particle particle = client.particleManager.addParticle(funnel && (i % 3 != 0) ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD,
                    x, y, z, frame.velocityX() - Math.sin(angle) * rotation, funnel ? 0.08 : 0.015,
                    frame.velocityZ() + Math.cos(angle) * rotation);
            if (particle != null) {
                particle.setMaxAge(32);
                particle.scale((float) (funnel ? 2.3 + fraction * 3.0 : 5.0));
                particle.setColor(0.30f + (float) fraction * 0.13f, 0.35f + (float) fraction * 0.13f, 0.38f + (float) fraction * 0.13f);
                particles.addLast(particle);
            }
        }
    }

    private double squaredDistance(MinecraftClient client, StormPayload.Frame frame) {
        double age = Math.min(40, Math.max(0, ticks - receivedAt));
        double dx = frame.x() + frame.velocityX() * age - client.player.getX();
        double dz = frame.z() + frame.velocityZ() * age - client.player.getZ();
        return dx * dx + dz * dz;
    }

    private boolean receiver(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString().equals("stormward:storm_receiver");
    }

    private void drawHud(DrawContext draw) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!settings.hud || client.player == null || client.world == null || client.options.hudHidden
                || (!receiver(client.player.getMainHandStack()) && !receiver(client.player.getOffHandStack()))) return;
        int width = 242;
        int x = Math.max(4, client.getWindow().getScaledWidth() - width - 8);
        int y = 8;
        draw.fill(x, y, x + width, y + 112, 0xde101d27);
        draw.fill(x, y, x + width, y + 2, 0xff66d9c3);
        text(draw, "STORMWARD  /  RECEIVER", x + 9, y + 8, 0xff9ce3d5);
        boolean stale = ticks - receivedAt >= MAX_SNAPSHOT_AGE;
        StormPayload.Frame nearest = storms.stream().min(Comparator.comparingDouble(frame -> squaredDistance(client, frame))).orElse(null);
        drawRadar(draw, x + 43, y + 61, nearest, client, stale);
        if (nearest == null || stale) {
            text(draw, stale ? "WAITING FOR SIGNAL" : "NO ACTIVE STORMS", x + 88, y + 30, 0xffdce5ec);
            text(draw, stale ? "Server snapshot stale" : "Within receiver range", x + 88, y + 46, 0xff9bafbb);
            text(draw, "Stay ready. Build shelter.", x + 88, y + 72, 0xff9bafbb);
        } else {
            int stage = Math.clamp(nearest.stage(), 0, 4);
            double age = Math.min(40, Math.max(0, ticks - receivedAt));
            double dx = nearest.x() + nearest.velocityX() * age - client.player.getX();
            double dz = nearest.z() + nearest.velocityZ() * age - client.player.getZ();
            double distance = Math.hypot(dx, dz);
            text(draw, STAGES[stage], x + 88, y + 28, COLORS[stage]);
            text(draw, String.format(Locale.ROOT, "%.0f blocks %s", distance, bearing(dx, dz)), x + 88, y + 41, 0xffdce5ec);
            double speedSquared = nearest.velocityX() * nearest.velocityX() + nearest.velocityZ() * nearest.velocityZ();
            double closestTicks = speedSquared > 1e-8 ? -(dx * nearest.velocityX() + dz * nearest.velocityZ()) / speedSquared : -1;
            String course;
            if (speedSquared <= 1e-8) course = "Course: nearly stationary";
            else if (closestTicks < 0) course = "Course: moving away";
            else if (closestTicks > 2400) course = "Approach: beyond 2 min";
            else course = String.format(Locale.ROOT, "Nearest in %.0fs / %.0fm", closestTicks / 20,
                        Math.hypot(dx + nearest.velocityX() * closestTicks, dz + nearest.velocityZ() * closestTicks));
            text(draw, course, x + 88, y + 54, 0xffb6c8d4);
            int shelterColor = exposure < 0.2 ? 0xff66d9c3 : exposure < 0.6 ? 0xfff3d579 : 0xffff9c62;
            text(draw, String.format(Locale.ROOT, "Wind exposure: %.0f%%", exposure * 100), x + 88, y + 72, shelterColor);
            draw.fill(x + 88, y + 84, x + width - 10, y + 87, 0xff304554);
            draw.fill(x + 88, y + 84, x + 88 + (int) ((width - 98) * exposure), y + 87, shelterColor);
        }
        text(draw, "Linear estimate. Loaded fronts only.", x + 9, y + 99, 0xff8ca4b2);
    }

    private void drawRadar(DrawContext draw, int centerX, int centerY, StormPayload.Frame frame, MinecraftClient client, boolean stale) {
        draw.fill(centerX - 31, centerY - 31, centerX + 32, centerY + 32, 0xff152d36);
        for (int i = 0; i < 40; i++) {
            double angle = Math.PI * 2 * i / 40;
            dot(draw, centerX + (int) (Math.cos(angle) * 29), centerY + (int) (Math.sin(angle) * 29), 0xff31515d);
        }
        draw.fill(centerX - 25, centerY, centerX + 26, centerY + 1, 0xff26434d);
        draw.fill(centerX, centerY - 25, centerX + 1, centerY + 26, 0xff26434d);
        text(draw, "N", centerX - 2, centerY - 28, 0xff7eacb8);
        dot(draw, centerX, centerY, 0xffd4fff0);
        if (frame == null || stale) return;
        double age = Math.min(40, Math.max(0, ticks - receivedAt));
        double dx = frame.x() + frame.velocityX() * age - client.player.getX();
        double dz = frame.z() + frame.velocityZ() * age - client.player.getZ();
        double range = Math.max(128, Math.hypot(dx, dz) * 1.15);
        int px = centerX + (int) (dx / range * 25);
        int pz = centerY + (int) (dz / range * 25);
        int color = COLORS[Math.clamp(frame.stage(), 0, 4)];
        for (int i = 1; i <= 6; i++) {
            double fx = dx + frame.velocityX() * i * 40;
            double fz = dz + frame.velocityZ() * i * 40;
            int tx = (int) (fx / range * 25);
            int tz = (int) (fz / range * 25);
            if (Math.abs(tx) < 28 && Math.abs(tz) < 28) dot(draw, centerX + tx, centerY + tz, 0xff69828a);
        }
        draw.fill(px - 2, pz - 2, px + 3, pz + 3, color);
    }

    private static String bearing(double dx, double dz) {
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double degrees = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        return names[(int) Math.round(degrees / 45) % 8];
    }

    private static void dot(DrawContext draw, int x, int y, int color) {
        draw.fill(x, y, x + 1, y + 1, color);
    }

    private static void text(DrawContext draw, String value, int x, int y, int color) {
        draw.drawText(MinecraftClient.getInstance().textRenderer, value, x, y, color, false);
    }
}
