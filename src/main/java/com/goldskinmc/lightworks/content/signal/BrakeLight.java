package com.goldskinmc.lightworks.content.signal;

import java.util.Arrays;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Tells a Tail and Brake Light when its vehicle slows: fed the vehicle's motion every client tick, it
 * reports braking for {@value #BRAKE_TICKS} ticks after the speed drops. Coming to a stand lights it at
 * once. Slowing is judged on the average speed of the last {@value #WINDOW} ticks against the
 * {@value #WINDOW} before: the motion a client sees jitters when ticks run late (it lit the brake light on
 * carts starting off under load), and averaging smooths that out while a real slowdown, even a sudden one,
 * still shows within a few ticks. A vehicle's first {@value #SETTLE_TICKS} ticks of movement do not count:
 * a contraption's motion settles after it starts.
 */
public final class BrakeLight {
    /** One second of brake light. */
    static final int BRAKE_TICKS = 20;
    /** Ticks per averaging window. */
    static final int WINDOW = 3;
    /** Speed drop per tick, in blocks per tick, that counts as braking: 1.6 blocks per second squared. */
    private static final double MIN_DECELERATION = 0.004;
    /** ...and at least this share of the speed. */
    private static final double MIN_SHARE = 0.1;
    /** Below this speed (blocks per tick) the vehicle counts as standing. */
    private static final double MIN_SPEED = 0.01;
    /** A vehicle that has just started moving settles for this many ticks before slowing counts. */
    static final int SETTLE_TICKS = 20;

    /** The last 2 x WINDOW speeds, oldest first. */
    private final double[] speeds = new double[2 * WINDOW];
    private int seen;
    private int brakeTicks;
    private int movingTicks;

    public void tick(@Nullable Vec3 motion) {
        double next = motion == null ? 0 : motion.length();
        double last = seen > 0 ? speeds[speeds.length - 1] : 0;
        System.arraycopy(speeds, 1, speeds, 0, speeds.length - 1);
        speeds[speeds.length - 1] = next;
        seen = Math.min(seen + 1, speeds.length);
        movingTicks = next > MIN_SPEED ? movingTicks + 1 : 0;
        if (last > MIN_SPEED && next < MIN_SPEED) {
            // Came to a stand
            brakeTicks = BRAKE_TICKS;
            return;
        }
        if (seen == speeds.length && movingTicks > SETTLE_TICKS) {
            double earlier = 0;
            double recent = 0;
            for (int i = 0; i < WINDOW; i++) {
                earlier += speeds[i] / WINDOW;
                recent += speeds[WINDOW + i] / WINDOW;
            }
            // The two windows' middles are WINDOW ticks apart
            double drop = earlier - recent;
            if (earlier > MIN_SPEED && drop > Math.max(MIN_DECELERATION * WINDOW, MIN_SHARE * earlier)) {
                brakeTicks = BRAKE_TICKS;
                return;
            }
        }
        if (brakeTicks > 0) {
            brakeTicks--;
        }
    }

    /** The vehicle stopped outright (a contraption stopping or reversing). */
    public void stopped() {
        if (seen > 0 && speeds[speeds.length - 1] > MIN_SPEED) {
            brakeTicks = BRAKE_TICKS;
        }
        Arrays.fill(speeds, 0);
        seen = 0;
        movingTicks = 0;
    }

    /** Brake light on now, for a second (Ponder scenes, which have no real vehicle). */
    public void trigger() {
        brakeTicks = BRAKE_TICKS;
    }

    public boolean braking() {
        return brakeTicks > 0;
    }
}
