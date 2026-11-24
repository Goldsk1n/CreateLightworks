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

    /** A landing this close in depth to the centre ray's counts fully towards a moving light's middle... */
    public static final double NEAR_DEPTH = 2.0;
    /** ...and this far off not at all. */
    public static final double FAR_DEPTH = 4.0;

    /**
     * How much a ray's landing counts towards a moving light's middle, by how far its depth along the beam lies from the
     * centre ray's: fully within {@link #NEAR_DEPTH}, fading to nothing at {@link #FAR_DEPTH}. Neighbouring steps of
     * terraced ground stay in; a floor the lower rays meet well before a wall does not, or it pulled the light off the
     * wall the beam points at (a Flashlight down a hall, QC Nov 24). Faded, not cut, so no landing jumps in or out.
     */
    public static double depthWeight(double centreDepth, double depth) {
        double off = Math.abs(depth - centreDepth);
        if (off <= NEAR_DEPTH) {
            return 1;
        }
        return off >= FAR_DEPTH ? 0 : (FAR_DEPTH - off) / (FAR_DEPTH - NEAR_DEPTH);
    }

    /** A spot whose struck face is not known: dark beyond a plane square to the beam. */
    public BeamSpot(double spotX, double spotY, double spotZ, double surfaceX, double surfaceY, double surfaceZ,
            double dirX, double dirY, double dirZ, double peak) {
        this(spotX, spotY, spotZ, surfaceX, surfaceY, surfaceZ, dirX, dirY, dirZ, peak, 0, 0, 0);
    }

    /**
     * The light of a lamp at (x, y, z) facing (nx, ny, nz), a unit vector out of its mount: round, centred half a block
     * out from the lamp, cut by nothing, as a held torch's dynamic light is. A cut at the mount's face was tried: on a
     * lamp standing on a vehicle's deck it was an endless plane, and the ground round the deck got no light at all, where
     * block light reaches round the deck's edge (lamps-moving, Nov 21). The surface lies far behind the spot, so
     * {@link #lightAt} never darkens a block for being beyond it.
     */
    public static BeamSpot lamp(double x, double y, double z, double nx, double ny, double nz, double peak) {
        double sx = x + nx * 0.5;
        double sy = y + ny * 0.5;
        double sz = z + nz * 0.5;
        return new BeamSpot(sx, sy, sz, sx + nx * FAR, sy + ny * FAR, sz + nz * FAR, nx, ny, nz, peak);
    }

    /** How far a lamp's surface lies past its spot: further than any light reaches. */
    private static final double FAR = 1000;

    /** The same spot moved by (dx, dy, dz), its surface with it. */
    public BeamSpot shifted(double dx, double dy, double dz) {
        return new BeamSpot(spotX + dx, spotY + dy, spotZ + dz, surfaceX + dx, surfaceY + dy, surfaceZ + dz,
                dirX, dirY, dirZ, peak, normalX, normalY, normalZ);
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
