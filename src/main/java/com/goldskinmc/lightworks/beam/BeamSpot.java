package com.goldskinmc.lightworks.beam;

/**
 * The lit spot where a beam lands, as a dynamic light: brightest at the spot centre, fading by
 * {@code falloffRatio} per block like LambDynamicLights' own lights, and dark beyond the lit surface so
 * the light does not shine through the wall it hits. With the struck face's normal known, "beyond" is measured from that
 * face: block centres in its own layer keep their light (a dirt path, slab, farmland or carpet is lit from its own
 * block's light, its top lying below the block's), the next layer back is dark. Without it (a hit on a Sable vehicle),
 * from a plane square to the beam through the hit.
 * <p>
 * Pure maths with no Minecraft classes. Coordinates are world coordinates; positions passed to
 * {@link #lightAt} are block centres.
 *
 * @param spotX    centre of the spot, just in front of the lit surface
 * @param surfaceX where the beam meets the surface
 * @param dirX     unit vector of the beam
 * @param peak     light level at the spot centre, 0 to 15
 * @param normalX  unit normal of the struck face, out of it towards the lamp; all zero when not known
 */
public record BeamSpot(double spotX, double spotY, double spotZ,
                       double surfaceX, double surfaceY, double surfaceZ,
                       double dirX, double dirY, double dirZ,
                       double peak, double normalX, double normalY, double normalZ) {

    /**
     * How far behind the struck face a block centre may lie and keep its light: the face's own layer of blocks (centres
     * half a block in) but not the one behind it (a block and a half), so a wall one block thick still stops the light.
     */
    public static final double BEHIND_FACE = 1.0;

    /** A spot whose struck face is not known: dark beyond a plane square to the beam. */
    public BeamSpot(double spotX, double spotY, double spotZ, double surfaceX, double surfaceY, double surfaceZ,
            double dirX, double dirY, double dirZ, double peak) {
        this(spotX, spotY, spotZ, surfaceX, surfaceY, surfaceZ, dirX, dirY, dirZ, peak, 0, 0, 0);
    }

    /** Light at block centre (x, y, z), never negative. */
    public double lightAt(double x, double y, double z, double falloffRatio) {
        if (normalX == 0 && normalY == 0 && normalZ == 0) {
            double beyondSurface = (x - surfaceX) * dirX + (y - surfaceY) * dirY + (z - surfaceZ) * dirZ;
            if (beyondSurface > 0) {
                return 0;
            }
        } else {
            double behindFace = -((x - surfaceX) * normalX + (y - surfaceY) * normalY + (z - surfaceZ) * normalZ);
            if (behindFace >= BEHIND_FACE) {
                return 0;
            }
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
