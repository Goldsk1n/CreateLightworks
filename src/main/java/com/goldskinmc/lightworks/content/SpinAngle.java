package com.goldskinmc.lightworks.content;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * An angle turned by a shaft, integrated once per tick on each side (Pan-Tilt Head pan, Rotating Beacon
 * rotor). Reading it mid-tick gives the same answer whether its block entity has ticked yet or not, so a
 * light reading another block's angle never lags a tick behind it.
 */
public final class SpinAngle {
    /** Shaft speed to degrees per tick: rpm x 360 / 60 / 20, as fast as the shaft itself turns. */
    public static final float DEGREES_PER_RPM_TICK = 0.3f;

    private float angle;
    private float previous;
    private long tickedAt = Long.MIN_VALUE;

    /** Advances by {@code step} degrees for the tick at {@code gameTime}. */
    public void tick(float step, long gameTime) {
        previous = angle;
        angle = Mth.wrapDegrees(angle + step);
        tickedAt = gameTime;
    }

    /**
     * The angle {@code partialTick} into the current tick. Copies that never tick (contraption render
     * worlds, Ponder scenes) hold still.
     */
    public float at(float partialTick, float step, @Nullable Level level) {
        if (!TickPhase.ticked(level, tickedAt)) {
            return angle + step * partialTick;
        }
        return Mth.rotLerp(partialTick, previous, angle);
    }

    public float get() {
        return angle;
    }

    /** Jumps to {@code value} without interpolating (loading, or a server correction). */
    public void set(float value) {
        angle = value;
        previous = value;
    }
}
