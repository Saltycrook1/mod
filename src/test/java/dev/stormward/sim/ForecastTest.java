package dev.stormward.sim;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ForecastTest {
    private Storm storm(double x, double z, double vx, double vz) {
        return Storm.create(new UUID(1, 2), x, z, vx, vz, 32, 1, 1);
    }

    @Test void approachingStormPredictsClosestApproachAndBearing() {
        Forecast.Prediction result = Forecast.forObserver(storm(100, 20, -.1, 0), 0, 0);
        assertTrue(result.approaching());
        assertEquals(1000, result.etaTicks());
        assertEquals(20, result.closestDistance(), 1e-9);
        assertEquals(Math.hypot(100, 20), result.distance(), 1e-9);
        assertTrue(result.bearingDegrees() > 90 && result.bearingDegrees() < 180);
    }

    @Test void stationaryAndRecedingStormsHaveNoEta() {
        for (Storm storm : new Storm[]{storm(100, 0, 0, 0), storm(100, 0, .1, 0)}) {
            Forecast.Prediction result = Forecast.forObserver(storm, 0, 0);
            assertFalse(result.approaching());
            assertEquals(-1, result.etaTicks());
            assertEquals(100, result.closestDistance());
        }
    }

    @Test void cardinalBearingsMatchCompass() {
        assertEquals(0, Forecast.forObserver(storm(0, -100, 0, 0), 0, 0).bearingDegrees());
        assertEquals(90, Forecast.forObserver(storm(100, 0, 0, 0), 0, 0).bearingDegrees());
        assertEquals(180, Forecast.forObserver(storm(0, 100, 0, 0), 0, 0).bearingDegrees());
        assertEquals(270, Forecast.forObserver(storm(-100, 0, 0, 0), 0, 0).bearingDegrees());
    }
}
