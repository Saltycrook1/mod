package dev.stormward.sim;

/** Cheap local roof and wall sampling. Server adapters treat unknown voxels as cover. */
public final class Shelter {
    public static final int MAX_SAMPLES = 163;
    public static final int REACH = 8;
    @FunctionalInterface public interface BlockQuery { boolean solid(int x, int y, int z); }
    public record Result(double exposure, double roofCoverage, double wallCoverage, int samples) {
        public boolean sheltered() { return exposure <= 0.20; }
    }
    private static final int[][] ROOF_OFFSETS = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
    private Shelter() {}

    public static Result evaluate(BlockQuery query, int x, int feetY, int z) {
        int samples = 0;
        int roofs = 0;
        for (int[] offset : ROOF_OFFSETS) {
            for (int dy = 2; dy <= REACH; dy++) {
                samples++;
                if (query.solid(x + offset[0], feetY + dy, z + offset[1])) {
                    roofs++;
                    break;
                }
            }
        }
        int barriers = 0;
        for (int[] direction : DIRECTIONS) {
            boolean lower = false;
            boolean upper = false;
            int reach = direction[0] != 0 && direction[1] != 0 ? 5 : REACH;
            for (int distance = 1; distance <= reach && !(lower && upper); distance++) {
                int sx = x + direction[0] * distance;
                int sz = z + direction[1] * distance;
                if (!lower) {
                    samples++;
                    lower = query.solid(sx, feetY, sz);
                }
                if (!upper) {
                    samples++;
                    upper = query.solid(sx, feetY + 1, sz);
                }
            }
            if (lower && upper) barriers++;
        }
        double roofCoverage = roofs / 5.0;
        double wallCoverage = barriers / 8.0;
        double exposure = Math.max(0, 1 - 0.45 * roofCoverage - 0.55 * wallCoverage);
        return new Result(exposure, roofCoverage, wallCoverage, samples);
    }
}
