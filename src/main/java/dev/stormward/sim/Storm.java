package dev.stormward.sim;

import java.util.Objects;
import java.util.UUID;

/** A deterministic, Minecraft-independent storm. Only the server advances this state. */
public final class Storm {
    public record State(UUID id, double x, double z, double velocityX, double velocityZ,
                        double radius, double strength, int ageTicks, int timeDivisor) {}

    private final UUID id;
    private double x;
    private double z;
    private final double velocityX;
    private final double velocityZ;
    private final double radius;
    private final double strength;
    private int ageTicks;
    private final int timeDivisor;

    public Storm(State state) {
        Objects.requireNonNull(state, "state");
        id = Objects.requireNonNull(state.id(), "id");
        if (!Double.isFinite(state.x()) || !Double.isFinite(state.z())
                || Math.abs(state.x()) > 29_999_000 || Math.abs(state.z()) > 29_999_000
                || !Double.isFinite(state.velocityX()) || !Double.isFinite(state.velocityZ())
                || Math.hypot(state.velocityX(), state.velocityZ()) > 0.5
                || !Double.isFinite(state.radius()) || state.radius() < 8 || state.radius() > 96
                || !Double.isFinite(state.strength()) || state.strength() < 0.1 || state.strength() > 5
                || state.ageTicks() < 0 || state.timeDivisor() < 1 || state.timeDivisor() > 600) {
            throw new IllegalArgumentException("Invalid storm state");
        }
        x = state.x();
        z = state.z();
        velocityX = state.velocityX();
        velocityZ = state.velocityZ();
        radius = state.radius();
        strength = state.strength();
        timeDivisor = state.timeDivisor();
        ageTicks = Math.min(state.ageTicks(), lifetimeTicks());
    }

    public static Storm create(UUID id, double x, double z, double vx, double vz,
                               double radius, double strength, int timeDivisor) {
        return new Storm(new State(id, x, z, vx, vz, radius, strength, 0, timeDivisor));
    }

    public UUID id() { return id; }
    public double x() { return x; }
    public double z() { return z; }
    public double velocityX() { return velocityX; }
    public double velocityZ() { return velocityZ; }
    public double radius() { return radius; }
    public double strength() { return strength; }
    public int ageTicks() { return ageTicks; }
    public int timeDivisor() { return timeDivisor; }
    public State state() {
        return new State(id, x, z, velocityX, velocityZ, radius, strength, ageTicks, timeDivisor);
    }

    public int lifetimeTicks() {
        int total = 0;
        for (StormStage stage : StormStage.values()) total += stage.ticks(timeDivisor);
        return total;
    }

    public boolean active() { return ageTicks < lifetimeTicks(); }

    public StormStage stage() {
        int until = 0;
        for (StormStage stage : StormStage.values()) {
            until += stage.ticks(timeDivisor);
            if (ageTicks < until) return stage;
        }
        return StormStage.DISSIPATING;
    }

    public int stageRemainingTicks() {
        if (!active()) return 0;
        int until = 0;
        for (StormStage stage : StormStage.values()) {
            until += stage.ticks(timeDivisor);
            if (ageTicks < until) return until - ageTicks;
        }
        return 0;
    }

    public int stageElapsedTicks() {
        return stage().ticks(timeDivisor) - stageRemainingTicks();
    }

    /** Freezing an unloaded storm avoids chunk tickets and offline surprise damage. */
    public void tick(boolean loaded) {
        if (!loaded || !active()) return;
        x = Math.clamp(x + velocityX, -29_999_000, 29_999_000);
        z = Math.clamp(z + velocityZ, -29_999_000, 29_999_000);
        ageTicks++;
    }

    /** Smooth tornado growth and decay; warning-stage gusts never reach full force. */
    public double intensity() {
        if (!active()) return 0;
        return strength * switch (stage()) {
            case GATHERING -> 0.035;
            case WATCH -> 0.10;
            case WARNING -> 0.25;
            case TORNADO -> {
                double edge = Math.min(stageElapsedTicks(), stageRemainingTicks());
                double ramp = Math.min(1, edge / Math.max(1.0, 200.0 / timeDivisor));
                yield 0.25 + 0.75 * ramp;
            }
            case DISSIPATING -> 0.25 * stageRemainingTicks() / stage().ticks(timeDivisor);
        };
    }
}
