package com.goldskinmc.lightworks.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BeamFrameTest {
    private static final double EPS = 1e-9;

    @Test
    void levelBeamHasLevelSideAxis() {
        double[] u = BeamFrame.side(0, 0, 1);
        assertEquals(0, u[1], EPS);
        assertEquals(1, Math.hypot(u[0], u[2]), EPS);
        // Perpendicular to the beam
        assertEquals(0, u[2], EPS);
    }

    @Test
    void axesAreOrthonormal() {
        double ax = 0.3;
        double ay = 0.5;
        double az = Math.sqrt(1 - ax * ax - ay * ay);
        double[] u = BeamFrame.side(ax, ay, az);
        double[] v = BeamFrame.up(ax, ay, az, u);
        assertEquals(0, u[0] * ax + u[1] * ay + u[2] * az, EPS);
        assertEquals(0, v[0] * ax + v[1] * ay + v[2] * az, EPS);
        assertEquals(0, u[0] * v[0] + u[1] * v[1] + u[2] * v[2], EPS);
        assertEquals(1, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), EPS);
    }

    @Test
    void verticalBeamUsesEast() {
        double[] u = BeamFrame.side(0, -1, 0);
        assertEquals(1, u[0], EPS);
        double[] v = BeamFrame.up(0, -1, 0, u);
        assertEquals(1, Math.abs(v[2]), EPS);
    }

    @Test
    void cornerIndicesRoundTrip() {
        for (int i = 0; i < BeamFrame.GRID * BeamFrame.GRID; i++) {
            assertEquals(i, BeamFrame.corner(BeamFrame.cornerU(i), BeamFrame.cornerV(i)));
        }
        assertEquals(4, BeamFrame.corner(0, 0));
    }

    @Test
    void squareFalloffReachesFurtherAlongTheDiagonal() {
        ConeFalloff shape = new ConeFalloff(10, 5, 10, 20, 15);
        double[] u = BeamFrame.side(0, 0, 1);
        double[] v = BeamFrame.up(0, 0, 1, u);
        // 12 degrees off axis along U and V: outside the round core, still in the square one's edge
        double t = Math.tan(Math.toRadians(12)) * 5;
        double round = shape.lightAt(t, t, 5, 0, 0, 1);
        double square = shape.lightAtSquare(t, t, 5, 0, 0, 1, u[0], u[1], u[2], v[0], v[1], v[2]);
        assertEquals(true, square > round);
        // On an axis both agree
        assertEquals(shape.lightAt(t, 0, 5, 0, 0, 1),
                shape.lightAtSquare(t, 0, 5, 0, 0, 1, u[0], u[1], u[2], v[0], v[1], v[2]), 1e-6);
    }
}
