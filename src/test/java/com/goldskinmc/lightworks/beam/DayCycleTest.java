package com.goldskinmc.lightworks.beam;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DayCycleTest {
    @Test
    void morningAndNoonAreDay() {
        assertFalse(DayCycle.isNight(0));
        assertFalse(DayCycle.isNight(6000));
        assertFalse(DayCycle.isNight(12999));
    }

    @Test
    void midnightIsNight() {
        assertTrue(DayCycle.isNight(13000));
        assertTrue(DayCycle.isNight(18000));
        assertTrue(DayCycle.isNight(22999));
    }

    @Test
    void laterDaysAreTheSame() {
        assertFalse(DayCycle.isNight(24000 * 40 + 1000));
        assertTrue(DayCycle.isNight(24000 * 40 + 18000));
        assertFalse(DayCycle.isNight(23000));
    }
}
