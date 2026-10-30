package com.goldskinmc.lightworks.beam;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BeamRaysTest {
    @Test
    void theCentreComesFirst() {
        for (BeamRays rays : new BeamRays[] {BeamRays.SINGLE, new BeamRays(3, 1), new BeamRays(3, 3)}) {
            assertArrayEquals(new int[] {0, 0}, rays.offsets()[0]);
        }
    }

    @Test
    void everyRayComesOnce() {
        assertEquals(1, BeamRays.SINGLE.offsets().length);
        assertEquals(3, new BeamRays(3, 1).offsets().length);
        int[][] grid = new BeamRays(3, 3).offsets();
        assertEquals(9, grid.length);
        Set<Integer> seen = new HashSet<>();
        for (int[] ray : grid) {
            assertTrue(seen.add(BeamFrame.corner(ray[0], ray[1])));
        }
    }

    @Test
    void threeAcrossStaysOnTheSideAxis() {
        for (int[] ray : new BeamRays(3, 1).offsets()) {
            assertEquals(0, ray[1]);
        }
    }

    @Test
    void otherCountsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new BeamRays(2, 1));
    }
}
