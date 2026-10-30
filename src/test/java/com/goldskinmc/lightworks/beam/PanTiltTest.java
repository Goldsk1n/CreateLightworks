package com.goldskinmc.lightworks.beam;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PanTiltTest {
    private static final int[][] FACINGS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    @Test
    void levelPointsStraightOutOfAWall() {
        for (int[] f : FACINGS) {
            if (f[1] == 0) {
                assertEquals(Aim.STRAIGHT, PanTilt.LEVEL.toAim(f[0], f[1], f[2]));
                assertEquals(PanTilt.LEVEL, PanTilt.of(f[0], f[1], f[2], Aim.STRAIGHT));
            }
        }
    }

    @Test
    void panTurnsRightSeenFromAbove() {
        // Floor light: pan 0 is north, 90 east
        assertArrayEquals(new double[] {0, 0, -1}, new PanTilt(0, 0).direction(0, 1, 0), 1e-9);
        assertArrayEquals(new double[] {1, 0, 0}, new PanTilt(90, 0).direction(0, 1, 0), 1e-9);
        // Wall light facing north: pan 90 turns it east, tilt 90 points it up
        assertArrayEquals(new double[] {1, 0, 0}, new PanTilt(90, 0).direction(0, 0, -1), 1e-9);
        assertArrayEquals(new double[] {0, 1, 0}, new PanTilt(0, 90).direction(0, 0, -1), 1e-9);
    }

    @Test
    void floorAndCeilingConvertExactly() {
        for (int fy : new int[] {1, -1}) {
            for (int pan = -179; pan <= 180; pan += 7) {
                for (int tilt = PanTilt.minTilt(fy); tilt <= PanTilt.maxTilt(fy); tilt += 9) {
                    if (Math.abs(tilt) == 90) {
                        continue;
                    }
                    PanTilt set = new PanTilt(pan, tilt);
                    assertEquals(set, PanTilt.of(0, fy, 0, set.toAim(0, fy, 0)));
                }
            }
        }
    }

    @Test
    void wallsConvertToTheNearestDegree() {
        for (int[] f : FACINGS) {
            if (f[1] != 0) {
                continue;
            }
            for (int pan = -90; pan <= 90; pan += 5) {
                for (int tilt = -85; tilt <= 85; tilt += 5) {
                    PanTilt back = PanTilt.of(f[0], f[1], f[2], new PanTilt(pan, tilt).toAim(f[0], f[1], f[2]));
                    assertTrue(Math.abs(back.tilt() - tilt) <= 1, "tilt " + tilt + " came back " + back);
                    if (Math.abs(tilt) < 80) {
                        assertTrue(Math.abs(back.pan() - pan) <= 2, "pan " + pan + " came back " + back);
                    }
                }
            }
        }
    }

    @Test
    void clampKeepsLightsOutOfTheirMount() {
        assertEquals(new PanTilt(-170, 0), new PanTilt(190, -20).clamp(1));
        assertEquals(new PanTilt(180, -90), new PanTilt(180, -100).clamp(-1));
        assertEquals(new PanTilt(90, 90), new PanTilt(120, 95).clamp(0));
        assertEquals(new PanTilt(-90, -90), new PanTilt(-120, -95).clamp(0));
    }
}
