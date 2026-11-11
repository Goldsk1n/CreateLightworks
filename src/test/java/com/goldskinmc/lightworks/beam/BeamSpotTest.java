package com.goldskinmc.lightworks.beam;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // A beam coming down at 45 degrees from the west onto ground whose top is y = 64, the struck face's normal up
    private final BeamSpot onGround = new BeamSpot(9.65, 64.35, 0.5, 10, 64, 0.5,
            Math.sqrt(0.5), -Math.sqrt(0.5), 0, 15, 0, 1, 0);

    @Test
    void lightsTheGroundLayerItself() {
        // A dirt path's top lies inside its own block, lit by that block's light: the ground layer keeps it, on both
        // sides of the landing
        assertTrue(onGround.lightAt(10.5, 63.5, 0.5, LDL_RATIO) > 0);
        assertTrue(onGround.lightAt(12.5, 63.5, 0.5, LDL_RATIO) > 0);
        assertTrue(onGround.lightAt(8.5, 63.5, 0.5, LDL_RATIO) > 0);
        // and the air above it on the far side, which the old plane square to the beam left dark
        assertTrue(onGround.lightAt(12.5, 64.5, 0.5, LDL_RATIO) > 0);
    }

    @Test
    void stopsAtTheLayerBehindTheStruckFace() {
        assertEquals(0, onGround.lightAt(10.5, 62.5, 0.5, LDL_RATIO), EPS);
        // A wall one block thick, struck on its west face at x = 10: the wall's own block may hold light (it is drawn
        // from its neighbours' light), the far side of it may not
        BeamSpot onWall = new BeamSpot(9.5, 64.5, 0.5, 10, 64.5, 0.5, 1, 0, 0, 15, -1, 0, 0);
        assertEquals(0, onWall.lightAt(11.5, 64.5, 0.5, LDL_RATIO), EPS);
        assertEquals(0, onWall.lightAt(11.5, 65.5, 0.5, LDL_RATIO), EPS);
        assertTrue(onWall.lightAt(9.5, 64.5, 0.5, LDL_RATIO) > 0);
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

    @Test
    void smallMovesAreNotWorthShowing() {
        BeamSpot nudged = new BeamSpot(9.5, 64.7, 0.5, 10, 64.7, 0.5, 1, 0, 0, 15);
        assertFalse(nudged.differsFrom(spot, 0.25, 0.5, 0.99985));
        BeamSpot moved = new BeamSpot(9.5, 64.8, 0.5, 10, 64.8, 0.5, 1, 0, 0, 15);
        assertTrue(moved.differsFrom(spot, 0.25, 0.5, 0.99985));
    }

    @Test
    void brightnessOrDirectionChangesAreWorthShowing() {
        BeamSpot dimmer = new BeamSpot(9.5, 64.5, 0.5, 10, 64.5, 0.5, 1, 0, 0, 14);
        assertTrue(dimmer.differsFrom(spot, 0.25, 0.5, 0.99985));
        double c = Math.cos(Math.toRadians(2));
        double s = Math.sin(Math.toRadians(2));
        BeamSpot turned = new BeamSpot(9.5, 64.5, 0.5, 10, 64.5, 0.5, c, s, 0, 15);
        assertTrue(turned.differsFrom(spot, 0.25, 0.5, 0.99985));
    }
}
