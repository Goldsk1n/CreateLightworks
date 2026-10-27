package com.goldskinmc.lightworks.beam;

/**
 * Light level inside a cone-shaped beam, on the 0 to 15 scale LambDynamicLights' {@code lightAtPos} uses.
 * A fully lit core cone is surrounded by a soft edge where light fades to zero, and light also fades
 * linearly along the axis until it reaches zero at {@code range}.
 * <p>
 * Pure maths with no Minecraft classes, so it is unit-tested without booting the game.
 */
public final class ConeFalloff {
    private final double cosCore;
    private final double cosEdge;
    private final double range;
    private final double intensity;

    /**
     * @param coreDegrees angle between the beam axis and the edge of the fully lit core
     * @param edgeDegrees width of the soft edge outside the core, where light fades to zero
     * @param range       distance along the axis where light reaches zero, in blocks
     * @param intensity   light level on the axis at the origin, 0 to 15
     */
    public ConeFalloff(double coreDegrees, double edgeDegrees, double range, double intensity) {
        if (coreDegrees < 0 || edgeDegrees < 0 || coreDegrees + edgeDegrees >= 90) {
            throw new IllegalArgumentException("Cone angles must be non-negative and total under 90 degrees");
        }
        if (range <= 0) {
            throw new IllegalArgumentException("Range must be positive");
        }
        if (intensity < 0 || intensity > 15) {
            throw new IllegalArgumentException("Intensity must be between 0 and 15");
        }
        this.cosCore = Math.cos(Math.toRadians(coreDegrees));
        this.cosEdge = Math.cos(Math.toRadians(coreDegrees + edgeDegrees));
        this.range = range;
        this.intensity = intensity;
    }

    /**
     * Light at offset (dx, dy, dz) from the beam origin, for a beam pointing along the unit vector
     * (ax, ay, az).
     */
    public double lightAt(double dx, double dy, double dz, double ax, double ay, double az) {
        double along = dx * ax + dy * ay + dz * az;
        if (along <= 0 || along >= range) {
            return 0;
        }
        double cos = along / Math.sqrt(dx * dx + dy * dy + dz * dz);
        double angular;
        if (cos >= cosCore) {
            angular = 1;
        } else if (cos <= cosEdge) {
            return 0;
        } else {
            angular = (cos - cosEdge) / (cosCore - cosEdge);
        }
        return intensity * angular * (1 - along / range);
    }
}
