package dev.stormward.sim;

/** Bounded per-tick acceleration, independent of render effects and random particles. */
public final class Wind {
    public static final double MAX_ACCELERATION = 0.28;
    public record Force(double x, double y, double z) {
        public double length() { return Math.sqrt(x * x + y * y + z * z); }
    }
    private static final Force CALM = new Force(0, 0, 0);
    private Wind() {}

    public static Force sample(Storm storm, double entityX, double entityY, double entityZ,
                               double groundY, double exposure) {
        if (!storm.active() || !Double.isFinite(entityX) || !Double.isFinite(entityY)
                || !Double.isFinite(entityZ) || !Double.isFinite(groundY)
                || !Double.isFinite(exposure)) return CALM;
        double dx = storm.x() - entityX;
        double dz = storm.z() - entityZ;
        double distance = Math.hypot(dx, dz);
        double height = entityY - groundY;
        double range = storm.radius() * 2;
        if (distance >= range || height < -8 || height > 96) return CALM;
        double falloff = Math.pow(1 - distance / range, 2);
        double shelter = Math.clamp(exposure, 0, 1);
        double power = storm.intensity() * falloff * shelter;
        double nx = distance > 0.01 ? dx / distance : 0;
        double nz = distance > 0.01 ? dz / distance : 0;
        double x = (nx * 0.065 - nz * 0.085) * power;
        double z = (nz * 0.065 + nx * 0.085) * power;
        double y = storm.stage() == StormStage.TORNADO
                // Normal living-entity gravity is about 0.08 blocks/tick². A mature
                // strength-one core must overcome it, while shelter/falloff still apply.
                ? 0.16 * power * Math.max(0, 1 - Math.max(0, height) / 96) : 0;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length > MAX_ACCELERATION) {
            double multiplier = MAX_ACCELERATION / length;
            x *= multiplier;
            y *= multiplier;
            z *= multiplier;
        }
        return new Force(x, y, z);
    }
}
