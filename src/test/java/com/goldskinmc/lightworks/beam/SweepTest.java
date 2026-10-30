package com.goldskinmc.lightworks.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SweepTest {
    private static final float E = 1e-3f;

    @Test
    void noArcPassesTheMovementThroughAndForgetsTheSweep() {
        Sweep sweep = new Sweep();
        sweep.step(10, 45);
        assertEquals(4.8f, sweep.step(4.8f, 0), E);
        assertEquals(0, sweep.offset(), E);
    }

    @Test
    void bouncesAtTheEndsWithoutLosingSpeed() {
        Sweep sweep = new Sweep();
        for (int i = 0; i < 4; i++) {
            sweep.step(10, 45);
        }
        assertEquals(40, sweep.offset(), E);
        assertEquals(0, sweep.step(10, 45), E);
        assertEquals(40, sweep.offset(), E);
        assertEquals(-1, sweep.direction());
        assertEquals(-10, sweep.step(10, 45), E);
    }

    @Test
    void staysInsideTheArcForAnySpeed() {
        for (float move : new float[] {0.3f, 4.8f, 76.8f, -4.8f, -76.8f}) {
            for (float arc : new float[] {5, 45, 180}) {
                Sweep sweep = new Sweep();
                for (int i = 0; i < 2000; i++) {
                    sweep.step(move, arc);
                    assertTrue(Math.abs(sweep.offset()) <= arc + E, move + " on " + arc + ": " + sweep.offset());
                }
            }
        }
    }

    @Test
    void reachesBothEnds() {
        Sweep sweep = new Sweep();
        float min = 0;
        float max = 0;
        for (int i = 0; i < 200; i++) {
            sweep.step(4.8f, 45);
            min = Math.min(min, sweep.offset());
            max = Math.max(max, sweep.offset());
        }
        assertTrue(max > 40 && min < -40, min + " to " + max);
    }

    @Test
    void aSmallerArcBringsTheHeadBackWithoutAJump() {
        Sweep sweep = new Sweep();
        sweep.set(90, 1);
        assertEquals(-10, sweep.step(10, 45), E);
        assertEquals(80, sweep.offset(), E);
        for (int i = 0; i < 3; i++) {
            sweep.step(10, 45);
        }
        assertEquals(50, sweep.offset(), E);
        assertEquals(-5, sweep.step(10, 45), E);
        assertEquals(45, sweep.offset(), E);
        assertEquals(-10, sweep.step(10, 45), E);
    }

    @Test
    void aReversedShaftTurnsItBackTheOtherWay() {
        Sweep sweep = new Sweep();
        sweep.step(10, 45);
        assertEquals(-10, sweep.step(-10, 45), E);
        assertEquals(0, sweep.offset(), E);
    }

    @Test
    void peekChangesNothing() {
        Sweep sweep = new Sweep();
        sweep.set(40, 1);
        assertEquals(0, sweep.peek(10, 45), E);
        assertEquals(40, sweep.offset(), E);
        assertEquals(1, sweep.direction());
    }
}
