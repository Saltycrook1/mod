package dev.stormward.sim;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WindTest {
    @Test void defaultStrengthCoreOvercomesGravityButShelterPreventsLift() {
        Storm storm = new Storm(new Storm.State(new UUID(3, 4), 0, 0, 0, 0, 32, 1, 3400, 1));
        assertTrue(Wind.sample(storm, 0, 0, 0, 0, 1).y() > 0.08);
        assertEquals(0, Wind.sample(storm, 0, 0, 0, 0, 0).y());
        assertTrue(Wind.sample(storm, 60, 0, 0, 0, 1).y() < 0.08);
    }
    private Storm tornado() {
        return new Storm(new Storm.State(new UUID(1, 2), 0, 0, .04, .02, 32, 5, 3400, 1));
    }

    @Test void forceIsFiniteAndBoundedAcrossCoreAndEdges() {
        Storm storm = tornado();
        for (int x = -64; x <= 64; x += 4) {
            for (int z = -64; z <= 64; z += 4) {
                for (int y = -10; y <= 100; y += 10) {
                    Wind.Force force = Wind.sample(storm, x, y, z, 0, 1);
                    assertTrue(Double.isFinite(force.length()));
                    assertTrue(force.length() <= Wind.MAX_ACCELERATION + 1e-10);
                }
            }
        }
    }

    @Test void shelterAndDistanceReduceAcceleration() {
        Storm storm = tornado();
        Wind.Force near = Wind.sample(storm, 12, 0, 0, 0, 1);
        Wind.Force far = Wind.sample(storm, 40, 0, 0, 0, 1);
        Wind.Force sheltered = Wind.sample(storm, 12, 0, 0, 0, .1);
        assertTrue(near.length() > far.length());
        assertTrue(sheltered.length() < near.length());
        assertEquals(0, Wind.sample(storm, 12, 0, 0, 0, 0).length());
        assertEquals(0, Wind.sample(storm, 64, 0, 0, 0, 1).length());
        assertEquals(0, Wind.sample(storm, 0, 100, 0, 0, 1).length());
    }

    @Test void warningDoesNotLiftEntitiesAndMalformedInputIsCalm() {
        Storm warning = new Storm(new Storm.State(new UUID(1, 2), 0, 0, 0, 0, 32, 5, 2500, 1));
        assertEquals(0, Wind.sample(warning, 10, 0, 0, 0, 1).y());
        assertEquals(0, Wind.sample(warning, Double.NaN, 0, 0, 0, 1).length());
        assertEquals(0, Wind.sample(warning, 10, 0, 0, 0, Double.NaN).length());
    }
}
