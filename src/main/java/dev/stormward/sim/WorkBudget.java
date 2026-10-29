package dev.stormward.sim;

/** A shared per-world budget prevents storm counts from multiplying expensive work. */
public final class WorkBudget {
    private final int limit;
    private int remaining;
    public WorkBudget(int limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative work limit");
        this.limit = limit;
        reset();
    }
    public void reset() { remaining = limit; }
    public boolean trySpend() {
        if (remaining <= 0) return false;
        remaining--;
        return true;
    }
    public int remaining() { return remaining; }
    public int spent() { return limit - remaining; }
}
