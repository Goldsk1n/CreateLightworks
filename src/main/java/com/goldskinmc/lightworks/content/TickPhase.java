package com.goldskinmc.lightworks.content;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Whether a block entity has had its turn in the current tick, for angles read between ticks as well as during them.
 * The client ticks block entities before it moves the level's clock on, so frames drawn between ticks see the clock a
 * tick past the block entities' last turn. Taken as "not ticked yet", that made frames draw a turning beacon or head a
 * whole tick ahead of where its light was traced, the light trailing the beam (measured
 * 2.75 degrees at 8 rpm).
 */
public final class TickPhase {
    private static volatile boolean clientTicking;

    private TickPhase() {
    }

    /** Set by the client around each of its ticks. */
    public static void setClientTicking(boolean ticking) {
        clientTicking = ticking;
    }

    /**
     * True if a block entity that last ticked at game time {@code tickedAt} has ticked in the tick now running or, between
     * ticks on the client, in the one just run. Also true for copies that never tick (contraption render worlds, Ponder
     * scenes, {@code tickedAt} still {@link Long#MIN_VALUE}), so they hold still.
     */
    public static boolean ticked(@Nullable Level level, long tickedAt) {
        if (level == null || tickedAt == Long.MIN_VALUE) {
            return true;
        }
        long now = level.getGameTime();
        if (level.isClientSide() && !clientTicking) {
            now--;
        }
        return tickedAt == now;
    }
}
