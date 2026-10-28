package com.goldskinmc.lightworks.beam;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BeamSpotTest {
    private static final double EPS = 1e-9;
    /** LambDynamicLights' ratio: 15 levels over 7.75 blocks. */
    private static final double LDL_RATIO = 15 / 7.75;

    // Beam along +X hitting a wall whose west face is at x = 10; spot centre half a block in front
    private final BeamSpot spot = new BeamSpot(9.5, 64.5, 0.5, 10, 64.5, 0.5, 1, 0, 0, 15);

    @Test
    void brightestAtTheSpotCentre() {
        assertEquals(15, spot.lightAt(9.5, 64.5, 0.5, LDL_RATIO), EPS);
    }

    @Test
    void fadesWithDistanceFromTheSpot() {
        assertEquals(15 - 2 * LDL_RATIO, spot.lightAt(9.5, 66.5, 0.5, LDL_RATIO), EPS);
        assertEquals(15 - 3 * LDL_RATIO, spot.lightAt(6.5, 64.5, 0.5, LDL_RATIO), EPS);
    }

    @Test
    void doesNotShineThroughTheWall() {
        assertEquals(0, spot.lightAt(10.5, 64.5, 0.5, LDL_RATIO), EPS);
        assertEquals(0, spot.lightAt(11.5, 64.5, 0.5, LDL_RATIO), EPS);
    }

    @Test
    void neverNegative() {
        assertEquals(0, spot.lightAt(-20, 64.5, 0.5, LDL_RATIO), EPS);
    }

    @Test
    void radiusIsWhereLightReachesZeroCappedByMax() {
        assertEquals(7.75, spot.radius(LDL_RATIO, 8), EPS);
        assertEquals(5, spot.radius(LDL_RATIO, 5), EPS);
        BeamSpot dim = new BeamSpot(0.5, 0.5, 0.5, 1, 0.5, 0.5, 1, 0, 0, 3);
        assertEquals(3 / LDL_RATIO, dim.radius(LDL_RATIO, 8), EPS);
    }

    @Test
    void boundsCoverTheLitBlocks() {
        assertArrayEquals(new int[] {1, 56, -8, 17, 72, 8}, spot.bounds(LDL_RATIO, 8));
    }
}
