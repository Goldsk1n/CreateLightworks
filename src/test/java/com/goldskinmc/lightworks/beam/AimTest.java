package com.goldskinmc.lightworks.beam;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AimTest {
    private static final int[][] FACINGS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    @Test
    void straightPointsAlongTheFacingExactly() {
        for (int[] f : FACINGS) {
            assertArrayEquals(new double[] {f[0], f[1], f[2]}, Aim.STRAIGHT.direction(f[0], f[1], f[2]), 0);
        }
    }

    @Test
    void directionsAreUnitVectorsInFrontOfTheMount() {
        for (int[] f : FACINGS) {
            for (int yaw = 0; yaw < 360; yaw += 15) {
                for (int elevation = 0; elevation <= 90; elevation += 10) {
                    double[] d = new Aim(yaw, elevation).direction(f[0], f[1], f[2]);
                    assertEquals(1, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-9);
                    double along = d[0] * f[0] + d[1] * f[1] + d[2] * f[2];
                    assertEquals(Math.sin(Math.toRadians(elevation)), along, 1e-9);
                }
            }
        }
    }

    @Test
    void fromDirectionRoundTripsEveryWholeDegreeAim() {
        for (int[] f : FACINGS) {
            for (int yaw = 0; yaw < 360; yaw += 7) {
                for (int elevation = 0; elevation < 90; elevation += 9) {
                    Aim aim = new Aim(yaw, elevation);
                    double[] d = aim.direction(f[0], f[1], f[2]);
                    assertEquals(aim, Aim.fromDirection(f[0], f[1], f[2], d[0], d[1], d[2]));
                }
            }
        }
    }

    @Test
    void lookingIntoTheMountIsMirroredOutOfIt() {
        // A light on the floor, looked at from above while facing east and 30 degrees down
        double down = Math.toRadians(30);
        Aim aim = Aim.fromDirection(0, 1, 0, Math.cos(down), -Math.sin(down), 0);
        assertEquals(30, aim.elevation());
        double[] d = aim.direction(0, 1, 0);
        assertEquals(Math.cos(down), d[0], 0.02);
        assertEquals(Math.sin(down), d[1], 0.02);
        assertEquals(0, d[2], 0.02);
    }

    @Test
    void anglesAreNormalised() {
        assertEquals(new Aim(350, 0), new Aim(-10, -5));
        assertEquals(Aim.STRAIGHT, new Aim(123, 120));
    }

    @Test
    void mountFrameIsAProperRotation() {
        for (int[] f : FACINGS) {
            double[] r = Aim.reference(f[1]);
            double[] s = Aim.side(f[0], f[1], f[2]);
            // det(S, F, R) = S . (F x R) = |S|^2
            double det = s[0] * (f[1] * r[2] - f[2] * r[1]) + s[1] * (f[2] * r[0] - f[0] * r[2])
                    + s[2] * (f[0] * r[1] - f[1] * r[0]);
            assertEquals(1, det, 1e-12);
        }
    }
}
