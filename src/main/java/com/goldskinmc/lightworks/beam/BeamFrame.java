package com.goldskinmc.lightworks.beam;

/**
 * The square cross-section of a beam: two unit axes across it, so the visible beam (a truncated pyramid),
 * its traced corner rays and its coloured spot all line up. For a beam pointing along the unit vector A,
 * the side axis U is horizontal (A x up, normalised), or east for a beam pointing straight up or down; the
 * other axis is V = A x U. A beam pointing level therefore has a level bottom edge, like a block.
 * <p>
 * Pure maths with no Minecraft classes, so it is unit-tested without booting the game.
 */
public final class BeamFrame {
    /** Corner rays per side of the square (corners, edge middles, centre). */
    public static final int GRID = 3;

    private BeamFrame() {
    }

    /** U for a beam along the unit vector (ax, ay, az). */
    public static double[] side(double ax, double ay, double az) {
        // A x (0, 1, 0) = (-az, 0, ax)
        double length = Math.sqrt(az * az + ax * ax);
        if (length < 1e-4) {
            return new double[] {1, 0, 0};
        }
        return new double[] {-az / length, 0, ax / length};
    }

    /** V = A x U. */
    public static double[] up(double ax, double ay, double az, double[] side) {
        return new double[] {
                ay * side[2] - az * side[1],
                az * side[0] - ax * side[2],
                ax * side[1] - ay * side[0]
        };
    }

    /** Offset -1, 0 or 1 of corner ray {@code index} (0 to GRID * GRID - 1) along U. */
    public static int cornerU(int index) {
        return index % GRID - 1;
    }

    /** Offset -1, 0 or 1 of corner ray {@code index} along V. */
    public static int cornerV(int index) {
        return index / GRID - 1;
    }

    /** Index of the corner ray at offsets (u, v), each -1, 0 or 1. */
    public static int corner(int u, int v) {
        return (v + 1) * GRID + u + 1;
    }

    /**
     * Rays per side of the square that outline the visible beam, finer than the corner rays: drawn between only the
     * corners and edge middles, a beam over rough ground got skewed sides and gaps where neighbours stopped far apart.
     */
    public static final int RIM_PER_SIDE = 6;
    /** Rays round the whole rim. */
    public static final int RIM = 4 * RIM_PER_SIDE;

    /**
     * Offset along U, -1 to 1, of rim ray {@code k} (0 to {@link #RIM} - 1): the rim walked from the (-1, -1) corner
     * along U, then up V, back along U and down V, so ray k and ray k + 1 are neighbours and ray 0 follows the last.
     */
    public static double rimU(int k) {
        double t = 2.0 * (k % RIM_PER_SIDE) / RIM_PER_SIDE;
        return switch (k / RIM_PER_SIDE) {
            case 0 -> -1 + t;
            case 1 -> 1;
            case 2 -> 1 - t;
            default -> -1;
        };
    }

    /** Offset along V of rim ray {@code k}, as {@link #rimU}. */
    public static double rimV(int k) {
        double t = 2.0 * (k % RIM_PER_SIDE) / RIM_PER_SIDE;
        return switch (k / RIM_PER_SIDE) {
            case 0 -> -1;
            case 1 -> -1 + t;
            case 2 -> 1;
            default -> 1 - t;
        };
    }
}
