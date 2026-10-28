package com.goldskinmc.lightworks.beam;

/**
 * The lit spot where a beam lands, as a dynamic light: brightest at the spot centre, fading by
 * {@code falloffRatio} per block like LambDynamicLights' own lights, and dark beyond the lit surface so
 * the light does not shine through the wall it hits.
 * <p>
 * Pure maths with no Minecraft classes. Coordinates are world coordinates; positions passed to
 * {@link #lightAt} are block centres.
 *
 * @param spotX    centre of the spot, just in front of the lit surface
 * @param surfaceX where the beam meets the surface
 * @param dirX     unit vector of the beam
 * @param peak     light level at the spot centre, 0 to 15
 */
public record BeamSpot(double spotX, double spotY, double spotZ,
                       double surfaceX, double surfaceY, double surfaceZ,
                       double dirX, double dirY, double dirZ,
                       double peak) {

    /** Light at block centre (x, y, z), never negative. */
    public double lightAt(double x, double y, double z, double falloffRatio) {
        double beyondSurface = (x - surfaceX) * dirX + (y - surfaceY) * dirY + (z - surfaceZ) * dirZ;
        if (beyondSurface > 0) {
            return 0;
        }
        double dx = x - spotX;
        double dy = y - spotY;
        double dz = z - spotZ;
        return Math.max(0, peak - Math.sqrt(dx * dx + dy * dy + dz * dz) * falloffRatio);
    }

    /**
     * True when this spot differs enough from {@code previous} to be worth showing: its centre moved more
     * than {@code minMove} blocks, its peak changed by more than {@code minPeakChange}, or its direction
     * turned so that the dot product of the two directions fell below {@code minDirectionDot}.
     */
    public boolean differsFrom(BeamSpot previous, double minMove, double minPeakChange, double minDirectionDot) {
        double dx = spotX - previous.spotX;
        double dy = spotY - previous.spotY;
        double dz = spotZ - previous.spotZ;
        return dx * dx + dy * dy + dz * dz > minMove * minMove
                || Math.abs(peak - previous.peak) > minPeakChange
                || dirX * previous.dirX + dirY * previous.dirY + dirZ * previous.dirZ < minDirectionDot;
    }

    /** Distance from the spot centre at which the light reaches zero, capped at {@code maxRadius}. */
    public double radius(double falloffRatio, double maxRadius) {
        return falloffRatio <= 0 ? maxRadius : Math.min(peak / falloffRatio, maxRadius);
    }

    /** Smallest block box holding every lit block: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public int[] bounds(double falloffRatio, double maxRadius) {
        double r = radius(falloffRatio, maxRadius);
        return new int[] {
                (int) Math.floor(spotX - r), (int) Math.floor(spotY - r), (int) Math.floor(spotZ - r),
                (int) Math.floor(spotX + r), (int) Math.floor(spotY + r), (int) Math.floor(spotZ + r)
        };
    }
}
