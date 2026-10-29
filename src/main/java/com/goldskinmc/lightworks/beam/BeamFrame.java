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
}
