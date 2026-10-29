package dev.stormward.sim;

/** Server-controlled stages. Durations are expressed in game ticks, not wall time. */
public enum StormStage {
    GATHERING(1200), WATCH(1200), WARNING(600), TORNADO(2400), DISSIPATING(600);

    private final int ticks;

    StormStage(int ticks) { this.ticks = ticks; }

    public int ticks(int timeDivisor) { return Math.max(1, ticks / Math.max(1, timeDivisor)); }
}
