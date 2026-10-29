package com.goldskinmc.lightworks.beam;

/**
 * Light level inside a cone-shaped beam, on the 0 to 15 scale LambDynamicLights' {@code lightAtPos} uses.
 * A fully lit core cone is surrounded by a soft edge where light fades to zero. Along the axis the beam
 * keeps full brightness out to {@code fullRange}, then fades linearly to zero at {@code range}.
 * <p>
 * Pure maths with no Minecraft classes, so it is unit-tested without booting the game.
 */
public final class ConeFalloff {
    private final double coreDegrees;
    private final double edgeDegrees;
    private final double cosCore;
    private final double cosEdge;
    private final double fullRange;
    private final double range;
    private final double intensity;

    /**
     * @param coreDegrees angle between the beam axis and the edge of the fully lit core
     * @param edgeDegrees width of the soft edge outside the core, where light fades to zero
     * @param fullRange   distance along the axis that stays at full brightness, in blocks
     * @param range       distance along the axis where light reaches zero, in blocks
     * @param intensity   light level on the axis within {@code fullRange}, 0 to 15
     */
    public ConeFalloff(double coreDegrees, double edgeDegrees, double fullRange, double range, double intensity) {
        if (coreDegrees < 0 || edgeDegrees < 0 || coreDegrees + edgeDegrees >= 90) {
            throw new IllegalArgumentException("Cone angles must be non-negative and total under 90 degrees");
        }
        if (range <= 0 || fullRange < 0 || fullRange >= range) {
            throw new IllegalArgumentException("Ranges must satisfy 0 <= fullRange < range");
        }
        if (intensity < 0 || intensity > 15) {
            throw new IllegalArgumentException("Intensity must be between 0 and 15");
        }
        this.coreDegrees = coreDegrees;
        this.edgeDegrees = edgeDegrees;
        this.cosCore = Math.cos(Math.toRadians(coreDegrees));
        this.cosEdge = Math.cos(Math.toRadians(coreDegrees + edgeDegrees));
        this.fullRange = fullRange;
        this.range = range;
        this.intensity = intensity;
    }

    public double range() {
        return range;
    }

    public double coreDegrees() {
        return coreDegrees;
    }

    public double edgeDegrees() {
        return edgeDegrees;
    }

    /** Light on the beam axis at {@code distance} blocks from the origin. */
    public double onAxis(double distance) {
        if (distance <= 0 || distance >= range) {
            return 0;
        }
        if (distance <= fullRange) {
            return intensity;
        }
        return intensity * (range - distance) / (range - fullRange);
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
        return onAxis(along) * angular;
    }

    /**
     * Light at offset (dx, dy, dz) from the beam origin for the square beam the player sees: like
     * {@link #lightAt} but the core and edge are squares across the axes (ux, uy, uz) and (vx, vy, vz)
     * (see {@link BeamFrame}), measured by the larger of the two angles off the axis.
     */
    public double lightAtSquare(double dx, double dy, double dz, double ax, double ay, double az,
            double ux, double uy, double uz, double vx, double vy, double vz) {
        double along = dx * ax + dy * ay + dz * az;
        if (along <= 0 || along >= range) {
            return 0;
        }
        double across = Math.max(Math.abs(dx * ux + dy * uy + dz * uz), Math.abs(dx * vx + dy * vy + dz * vz));
        // The cosine of the larger angle off the axis, then the same edge as the round beam
        double cos = along / Math.sqrt(along * along + across * across);
        double angular;
        if (cos >= cosCore) {
            angular = 1;
        } else if (cos <= cosEdge) {
            return 0;
        } else {
            angular = (cos - cosEdge) / (cosCore - cosEdge);
        }
        return onAxis(along) * angular;
    }
}
