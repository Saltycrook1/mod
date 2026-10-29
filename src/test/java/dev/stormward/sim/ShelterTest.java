package dev.stormward.sim;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ShelterTest {
    @Test void openTerrainHasFullExposureAndBoundedReads() {
        AtomicInteger reads = new AtomicInteger();
        Shelter.Result result = Shelter.evaluate((x, y, z) -> {
            reads.incrementAndGet();
            assertTrue(Math.hypot(x, z) <= Shelter.REACH);
            assertTrue(y >= 0 && y <= 8);
            return false;
        }, 0, 0, 0);
        assertEquals(1, result.exposure());
        assertEquals(result.samples(), reads.get());
        assertTrue(result.samples() <= Shelter.MAX_SAMPLES);
        assertFalse(result.sheltered());
    }

    @Test void roofAloneDoesNotMakeASafeShelter() {
        Shelter.Result roofOnly = Shelter.evaluate((x, y, z) -> y == 3, 0, 0, 0);
        assertEquals(1, roofOnly.roofCoverage());
        assertEquals(0, roofOnly.wallCoverage());
        assertEquals(.55, roofOnly.exposure(), 1e-10);
        assertFalse(roofOnly.sheltered());
    }

    @Test void enclosedRoomHasNearZeroExposure() {
        Shelter.Result room = Shelter.evaluate(ShelterTest::roomSolid, 0, 0, 0);
        assertEquals(1, room.roofCoverage());
        assertEquals(1, room.wallCoverage());
        assertEquals(0, room.exposure(), 1e-10);
        assertTrue(room.sheltered());
        assertTrue(room.samples() <= Shelter.MAX_SAMPLES);
    }

    @Test void DoorwayAndRoofHolesIncreaseExposure() {
        Shelter.Result room = Shelter.evaluate(ShelterTest::roomSolid, 0, 0, 0);
        Shelter.Result hole = Shelter.evaluate((x, y, z) ->
                roomSolid(x, y, z) && !(x == 0 && z == 0 && y == 3), 0, 0, 0);
        Shelter.Result doorway = Shelter.evaluate((x, y, z) ->
                roomSolid(x, y, z) && !(x == 3 && z == 0 && y <= 1), 0, 0, 0);
        assertTrue(hole.exposure() > room.exposure());
        assertTrue(doorway.exposure() > room.exposure());
        assertTrue(hole.roofCoverage() < room.roofCoverage());
        assertTrue(doorway.wallCoverage() < room.wallCoverage());
    }

    @Test void unsupportedRoofAndWallFragmentsArePartialProtection() {
        Shelter.Result walls = Shelter.evaluate((x, y, z) -> y <= 1
                && (Math.abs(x) == 3 || Math.abs(z) == 3), 0, 0, 0);
        assertEquals(.45, walls.exposure(), 1e-10);
        assertFalse(walls.sheltered());
        Shelter.Result distant = Shelter.evaluate((x, y, z) -> y == 9, 0, 0, 0);
        assertEquals(1, distant.exposure());
    }

    private static boolean roomSolid(int x, int y, int z) {
        return y == 3 || ((Math.abs(x) == 3 || Math.abs(z) == 3) && y <= 1);
    }
}
