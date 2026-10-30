package com.goldskinmc.lightworks.beam;

/**
 * Night by the clock, not by how much sky a block can see, so a light on a tower and one under its roof
 * agree. Pure maths with no Minecraft classes, so it is unit-tested without booting the game.
 */
public final class DayCycle {
    private static final int TICKS_PER_DAY = 24000;
    /** About sunset. */
    private static final int NIGHT_START = 13000;
    /** About sunrise. */
    private static final int NIGHT_END = 23000;

    private DayCycle() {
    }

    /** @param dayTime the world's day time in ticks, which keeps counting from day to day */
    public static boolean isNight(long dayTime) {
        long time = Math.floorMod(dayTime, (long) TICKS_PER_DAY);
        return time >= NIGHT_START && time < NIGHT_END;
    }
}
