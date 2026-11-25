package com.goldskinmc.lightworks.beam;

/**
 * How strongly a beam lights a Beam Sensor's lens: the light the player sees there ({@link ConeFalloff#lightInBeam}, the
 * square beam), 0 when the lens is turned away from the light or lies outside the beam. Whether anything stands between
 * them is the caller's business (one ray). Pure maths with no Minecraft classes.
 */
public final class BeamSensing {
    private BeamSensing() {
    }

    /**
     * Light reaching a lens, 0 to the beam's intensity at full brightness.
     *
     * @param lensX  the middle of the lens face, in the world
     * @param faceX  the lens face's outward unit normal
     * @param originX where the beam starts
     * @param axisX  the beam's unit direction
     * @param range  how far the beam reaches here (its own range, capped by the server's)
     */
    public static double light(double lensX, double lensY, double lensZ, double faceX, double faceY, double faceZ,
            double originX, double originY, double originZ, double axisX, double axisY, double axisZ,
            ConeFalloff shape, double lensHalf, double range) {
        return light(lensX, lensY, lensZ, faceX, faceY, faceZ, originX, originY, originZ, axisX, axisY, axisZ, null,
                shape, lensHalf, range);
    }

    /** As above, the square's side kept to {@code sideHint} ({@link BeamFrame#side(double, double, double, double[])}). */
    public static double light(double lensX, double lensY, double lensZ, double faceX, double faceY, double faceZ,
            double originX, double originY, double originZ, double axisX, double axisY, double axisZ, double[] sideHint,
            ConeFalloff shape, double lensHalf, double range) {
        double dx = lensX - originX;
        double dy = lensY - originY;
        double dz = lensZ - originZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= 1e-6 || distance > range) {
            return 0;
        }
        // The light comes from the lamp: it reaches only a face turned towards it
        if ((dx * faceX + dy * faceY + dz * faceZ) / distance >= 0) {
            return 0;
        }
        double[] u = BeamFrame.side(axisX, axisY, axisZ, sideHint);
        double[] v = BeamFrame.up(axisX, axisY, axisZ, u);
        return Math.max(0, shape.lightInBeam(dx, dy, dz, axisX, axisY, axisZ, u[0], u[1], u[2], v[0], v[1], v[2], lensHalf));
    }

    /** The redstone signal for that light at the light's brightness (0 to 15): 0 to 15, at least 1 for any light at all. */
    public static int signal(double light, int brightness) {
        double value = light * Math.max(0, Math.min(15, brightness)) / 15.0;
        if (value <= 0.01) {
            return 0;
        }
        return (int) Math.max(1, Math.min(15, Math.round(value)));
    }
}
