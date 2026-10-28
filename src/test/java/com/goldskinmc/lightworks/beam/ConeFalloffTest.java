package com.goldskinmc.lightworks.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConeFalloffTest {
    private static final double EPS = 1e-9;
    // 10 degree core, 5 degree soft edge, full brightness to 32 blocks, dark at 48; beam along +Z
    private final ConeFalloff cone = new ConeFalloff(10, 5, 32, 48, 15);

    private double alongZ(double dx, double dy, double dz) {
        return cone.lightAt(dx, dy, dz, 0, 0, 1);
    }

    @Test
    void fullBrightnessUpToFullRange() {
        assertEquals(15, alongZ(0, 0, 1), EPS);
        assertEquals(15, alongZ(0, 0, 20), EPS);
        assertEquals(15, alongZ(0, 0, 32), EPS);
    }

    @Test
    void fadesLinearlyFromFullRangeToRange() {
        assertEquals(7.5, alongZ(0, 0, 40), EPS);
        assertEquals(15 * 4.0 / 16, alongZ(0, 0, 44), EPS);
    }

    @Test
    void onAxisMatchesLightAtOnTheAxis() {
        for (double d : new double[] {0.5, 10, 32, 33, 47.5}) {
            assertEquals(cone.onAxis(d), alongZ(0, 0, d), EPS);
        }
    }

    @Test
    void behindTheLightIsDark() {
        assertEquals(0, alongZ(0, 0, -5), EPS);
        assertEquals(0, alongZ(3, 0, 0), EPS);
    }

    @Test
    void atAndBeyondRangeIsDark() {
        assertEquals(0, alongZ(0, 0, 48), EPS);
        assertEquals(0, alongZ(0, 0, 60), EPS);
    }

    @Test
    void insideTheCoreIsAsBrightAsTheAxis() {
        double offAxis = 40 * Math.tan(Math.toRadians(8));
        assertEquals(alongZ(0, 0, 40), alongZ(offAxis, 0, 40), EPS);
    }

    @Test
    void softEdgeFadesBetweenCoreAndDark() {
        double inEdge = 10 * Math.tan(Math.toRadians(12.5));
        double light = alongZ(inEdge, 0, 10);
        assertTrue(light > 0 && light < alongZ(0, 0, 10), "edge light should be partial, was " + light);
        double outside = 10 * Math.tan(Math.toRadians(16));
        assertEquals(0, alongZ(outside, 0, 10), EPS);
    }

    @Test
    void resultDoesNotDependOnBeamDirection() {
        double alongX = cone.lightAt(40, 1, 0, 1, 0, 0);
        double s = Math.sqrt(0.5);
        double diagonal = cone.lightAt(40 * s, 1, 40 * s, s, 0, s);
        assertEquals(alongZ(1, 0, 40), alongX, EPS);
        assertEquals(alongX, diagonal, EPS);
    }

    @Test
    void rejectsInvalidShapes() {
        assertThrows(IllegalArgumentException.class, () -> new ConeFalloff(80, 10, 32, 48, 15));
        assertThrows(IllegalArgumentException.class, () -> new ConeFalloff(10, 5, 0, 0, 15));
        assertThrows(IllegalArgumentException.class, () -> new ConeFalloff(10, 5, 48, 48, 15));
        assertThrows(IllegalArgumentException.class, () -> new ConeFalloff(10, 5, 32, 48, 16));
    }
}
