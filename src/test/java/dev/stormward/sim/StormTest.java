package dev.stormward.sim;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StormTest {
    private Storm storm(int divisor) { return Storm.create(new UUID(1, 2), 0, 0, .04, .02, 32, 2, divisor); }

    @Test void defaultLifecycleVisitsEveryStageAtExactBoundaries() {
        Storm storm = storm(1);
        int elapsed = 0;
        for (StormStage stage : StormStage.values()) {
            assertEquals(stage, storm.stage());
            assertEquals(stage.ticks(1), storm.stageRemainingTicks());
            for (int i = 0; i < stage.ticks(1); i++) storm.tick(true);
            elapsed += stage.ticks(1);
            assertEquals(elapsed, storm.ageTicks());
        }
        assertEquals(6000, elapsed);
        assertFalse(storm.active());
        assertEquals(0, storm.stageRemainingTicks());
        assertEquals(0, storm.intensity());
        Storm.State ended = storm.state();
        storm.tick(true);
        assertEquals(ended, storm.state());
    }

    @Test void unloadedStormDoesNotAdvanceOrMove() {
        Storm storm = storm(1);
        Storm.State before = storm.state();
        for (int tick = 0; tick < 10_000; tick++) storm.tick(false);
        assertEquals(before, storm.state());
        storm.tick(true);
        assertEquals(.04, storm.x());
        assertEquals(.02, storm.z());
    }

    @Test void stateRoundtripResumesIdentically() {
        Storm original = storm(10);
        for (int tick = 0; tick < 307; tick++) original.tick(true);
        Storm restored = new Storm(original.state());
        assertEquals(original.state(), restored.state());
        for (int tick = 0; tick < 293; tick++) {
            original.tick(true);
            restored.tick(true);
            assertEquals(original.state(), restored.state());
            assertEquals(original.stage(), restored.stage());
        }
    }

    @Test void acceleratedLifecycleRetainsAllStagesAndClampExpiredSaves() {
        Storm storm = storm(10);
        assertEquals(600, storm.lifetimeTicks());
        Storm expired = new Storm(new Storm.State(storm.id(), 0, 0, 0, 0, 16, 1, 9000, 10));
        assertEquals(600, expired.ageTicks());
        assertFalse(expired.active());
        Storm fastest = storm(600);
        assertEquals(10, fastest.lifetimeTicks());
        for (StormStage stage : StormStage.values()) assertTrue(stage.ticks(600) > 0);
    }

    @Test void invalidOrUnboundedPersistedStateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Storm.create(UUID.randomUUID(), Double.NaN, 0, 0, 0, 16, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> Storm.create(UUID.randomUUID(), 0, 0, 2, 0, 16, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> Storm.create(UUID.randomUUID(), 0, 0, 0, 0, 10000, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> Storm.create(UUID.randomUUID(), 0, 0, 0, 0, 16, 10000, 1));
    }
}
