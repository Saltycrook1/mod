package dev.stormward.sim;

/** Constant-velocity short-range forecast; ETA is advisory and updates as the storm moves. */
public final class Forecast {
    public record Prediction(double distance, double bearingDegrees, double closestDistance,
                             int etaTicks, boolean approaching) {}
    private Forecast() {}

    public static Prediction forObserver(Storm storm, double playerX, double playerZ) {
        double dx = storm.x() - playerX;
        double dz = storm.z() - playerZ;
        double distance = Math.hypot(dx, dz);
        double bearing = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        double vx = storm.velocityX();
        double vz = storm.velocityZ();
        double speedSquared = vx * vx + vz * vz;
        if (speedSquared < 1.0e-10) return new Prediction(distance, bearing, distance, -1, false);
        double eta = -(dx * vx + dz * vz) / speedSquared;
        if (eta <= 0) return new Prediction(distance, bearing, distance, -1, false);
        double closest = Math.hypot(dx + eta * vx, dz + eta * vz);
        return new Prediction(distance, bearing, closest, (int) Math.min(Integer.MAX_VALUE, Math.ceil(eta)), true);
    }
}
