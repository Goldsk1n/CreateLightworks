package com.goldskinmc.lightworks.beam;

/**
 * Where a directional light points, relative to the face it is mounted on: {@code elevation} degrees up
 * from the mount plane (90 = straight out of the mount, 0 = along its surface) and {@code yaw} degrees
 * around the mount's normal. Whole degrees, so a light aimed by hand always lands on the same angles.
 * <p>
 * The mount frame for a facing F (a unit axis vector): R = north (0, 0, -1) for up and down facings,
 * otherwise up (0, 1, 0); S = F x R. Yaw 0 leans toward R, yaw 90 toward S. The basis (S, F, R) is a proper
 * rotation, which renderers use to turn a model pointing up into the aim.
 * <p>
 * Pure maths with no Minecraft classes, so it is unit-tested without booting the game.
 */
public record Aim(int yaw, int elevation) {
    /** Straight out of the mount: what a light without stored aim does. */
    public static final Aim STRAIGHT = new Aim(0, 90);

    public Aim {
        yaw = Math.floorMod(yaw, 360);
        elevation = Math.max(0, Math.min(90, elevation));
        if (elevation == 90) {
            // Every yaw is the same direction here: keep one canonical value
            yaw = 0;
        }
    }

    /** Unit direction of this aim for a light mounted with facing (fx, fy, fz). */
    public double[] direction(int fx, int fy, int fz) {
        return direction(fx, fy, fz, yaw, elevation);
    }

    /** Unit direction for fractional angles, for lights that turn smoothly (Pan-Tilt Head, beacons). */
    public static double[] direction(int fx, int fy, int fz, double yawDegrees, double elevationDegrees) {
        if (elevationDegrees >= 90) {
            return new double[] {fx, fy, fz};
        }
        double[] r = reference(fy);
        double[] s = cross(fx, fy, fz, r);
        double yaw = Math.toRadians(yawDegrees);
        double elevation = Math.toRadians(elevationDegrees);
        double flat = Math.cos(elevation);
        double up = Math.sin(elevation);
        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);
        return new double[] {
                flat * (cy * r[0] + sy * s[0]) + up * fx,
                flat * (cy * r[1] + sy * s[1]) + up * fy,
                flat * (cy * r[2] + sy * s[2]) + up * fz
        };
    }

    /**
     * The aim closest to direction (dx, dy, dz) for a light mounted with facing (fx, fy, fz). A direction
     * pointing into the mount is mirrored out of it, so looking down at a light on the floor aims it up
     * at the same angle, like a reflection.
     */
    public static Aim fromDirection(int fx, int fy, int fz, double dx, double dy, double dz) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-9) {
            return STRAIGHT;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        double along = dx * fx + dy * fy + dz * fz;
        if (along < 0) {
            dx -= 2 * along * fx;
            dy -= 2 * along * fy;
            dz -= 2 * along * fz;
            along = -along;
        }
        double[] r = reference(fy);
        double[] s = cross(fx, fy, fz, r);
        double onR = dx * r[0] + dy * r[1] + dz * r[2];
        double onS = dx * s[0] + dy * s[1] + dz * s[2];
        int elevation = (int) Math.round(Math.toDegrees(Math.asin(Math.min(1, along))));
        int yaw = (int) Math.round(Math.toDegrees(Math.atan2(onS, onR)));
        return new Aim(yaw, elevation);
    }

    /** R of the mount frame. */
    public static double[] reference(int fy) {
        return fy != 0 ? new double[] {0, 0, -1} : new double[] {0, 1, 0};
    }

    /** S = F x R of the mount frame. */
    public static double[] side(int fx, int fy, int fz) {
        return cross(fx, fy, fz, reference(fy));
    }

    private static double[] cross(int fx, int fy, int fz, double[] r) {
        return new double[] {
                fy * r[2] - fz * r[1],
                fz * r[0] - fx * r[2],
                fx * r[1] - fy * r[0]
        };
    }
}
