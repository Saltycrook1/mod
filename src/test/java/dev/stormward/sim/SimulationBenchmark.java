package dev.stormward.sim;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * Reproducible kernel microbenchmark, not a Minecraft server or comparative benchmark.
 * Storms stay in the tornado stage; "entities" are precomputed positions, not game objects.
 * Includes all storm/entity force pairs and eight local shelter queries per simulated tick.
 */
public final class SimulationBenchmark {
    private static volatile double blackhole;
    private static final int WARMUP = 5000;
    private static final int MEASURED = 20000;

    public static void main(String[] args) {
        System.out.printf("Stormward pure-Java kernel microbenchmark%nJava: %s %s%nOS: %s %s %s%n"
                        + "Processors visible to JVM: %d; max heap MiB: %d%nWarmup: %d ticks; measured: %d ticks%n",
                System.getProperty("java.vendor"), System.getProperty("java.version"),
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / 1048576,
                WARMUP, MEASURED);
        scenario(1, 200);
        scenario(4, 800);
        System.out.printf("Checksum: %.6f%n", blackhole);
        System.out.println("Limitations: synthetic positions/voxel predicates; no Minecraft entity queries,"
                + " chunks, caches, packets, NBT, destruction, rendering or GC control. No competitor comparison.");
    }

    private static void scenario(int stormCount, int entityCount) {
        Storm[] storms = new Storm[stormCount];
        for (int i = 0; i < stormCount; i++) {
            storms[i] = new Storm(new Storm.State(new UUID(1, i + 1), i * 12, 0,
                    .04, .02, 48, 2, 3400, 1));
        }
        double[][] entities = new double[entityCount][3];
        for (int i = 0; i < entityCount; i++) {
            entities[i][0] = Math.sin(i * 2.399963) * (8 + i % 96);
            entities[i][1] = i % 40;
            entities[i][2] = Math.cos(i * 2.399963) * (8 + i % 96);
        }
        for (int i = 0; i < WARMUP; i++) workload(storms, entities, i);
        long[] times = new long[MEASURED];
        long start = System.nanoTime();
        for (int i = 0; i < MEASURED; i++) {
            long tickStart = System.nanoTime();
            workload(storms, entities, i);
            times[i] = System.nanoTime() - tickStart;
        }
        double meanMs = (System.nanoTime() - start) / 1_000_000.0 / MEASURED;
        Arrays.sort(times);
        System.out.printf(Locale.ROOT,
                "%d storms; %d synthetic entities; %d force samples/tick; 8 shelter queries/tick: "
                        + "mean=%.4f ms p50=%.4f ms p95=%.4f ms p99=%.4f ms max=%.4f ms%n",
                stormCount, entityCount, stormCount * entityCount, meanMs,
                times[MEASURED / 2] / 1_000_000.0, times[(int) (MEASURED * .95)] / 1_000_000.0,
                times[(int) (MEASURED * .99)] / 1_000_000.0, times[MEASURED - 1] / 1_000_000.0);
    }

    private static void workload(Storm[] storms, double[][] entities, int tick) {
        double sum = 0;
        for (Storm storm : storms) {
            for (int i = 0; i < entities.length; i++) {
                double[] entity = entities[i];
                Wind.Force force = Wind.sample(storm, entity[0], entity[1], entity[2], 0,
                        (i % 4) / 3.0);
                sum += force.x() + force.y() + force.z();
            }
        }
        for (int i = 0; i < 8; i++) {
            int origin = (tick + i) & 7;
            Shelter.Result result = Shelter.evaluate((x, y, z) -> y == 3 && (x & 3) == 0
                    || y <= 1 && Math.floorMod(x + z, 7) == 0, origin, 0, 0);
            sum += result.exposure() + result.samples();
        }
        blackhole = sum;
    }
}
