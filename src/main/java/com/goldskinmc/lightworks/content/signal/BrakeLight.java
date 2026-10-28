package com.goldskinmc.lightworks.content.signal;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Tells a Tail and Brake Light when its vehicle slows: fed the vehicle's motion every client tick, it
 * reports braking for {@value #BRAKE_TICKS} ticks after the speed drops.
 */
public final class BrakeLight {
    /** One second of brake light. */
    static final int BRAKE_TICKS = 20;
    /** Speed drop per tick, in blocks per tick, that counts as braking: 1.6 blocks per second squared. */
    private static final double MIN_DECELERATION = 0.004;
    /** Below this speed (blocks per tick) the vehicle counts as standing. */
    private static final double MIN_SPEED = 0.01;

    private double speed;
    private int brakeTicks;

    public void tick(@Nullable Vec3 motion) {
        double next = motion == null ? 0 : motion.length();
        if (speed > MIN_SPEED && speed - next > MIN_DECELERATION) {
            brakeTicks = BRAKE_TICKS;
        } else if (brakeTicks > 0) {
            brakeTicks--;
        }
        speed = next;
    }

    /** The vehicle stopped outright (a contraption stopping or reversing). */
    public void stopped() {
        if (speed > MIN_SPEED) {
            brakeTicks = BRAKE_TICKS;
        }
        speed = 0;
    }

    /** Brake light on now, for a second (Ponder scenes, which have no real vehicle). */
    public void trigger() {
        brakeTicks = BRAKE_TICKS;
    }

    public boolean braking() {
        return brakeTicks > 0;
    }
}
