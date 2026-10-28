package com.goldskinmc.lightworks.compat.ldl;

import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.compat.DynamicSpot;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Beam spots as LambDynamicLights behaviours. LambDynamicLights polls {@code hasChanged()} once per client
 * tick, rebuilds the chunk sections under the old and new bounding boxes, and drops a behaviour once
 * {@code isRemoved()} returns true.
 */
public final class LdlSpots {
    /** LambDynamicLights never lights further than 7.75 blocks from a source. */
    private static final double MAX_RADIUS = 7.75;
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final AtomicLong CALLS = new AtomicLong();
    private static volatile double maxReturned;

    private LdlSpots() {
    }

    public static DynamicSpot create() {
        DynamicLightBehaviorManager manager = LightworksDynamicLights.behaviors();
        if (manager == null) {
            return DynamicSpot.NONE;
        }
        SpotBehavior behavior = new SpotBehavior();
        manager.add(behavior);
        ACTIVE.incrementAndGet();
        return behavior;
    }

    public static String status() {
        if (LightworksDynamicLights.behaviors() == null) {
            return "LambDynamicLights present but not initialised";
        }
        return String.format("LambDynamicLights spots %d, lightAtPos calls %d, max %.1f",
                ACTIVE.get(), CALLS.get(), maxReturned);
    }

    private static final class SpotBehavior implements DynamicLightBehavior, DynamicSpot {
        private static final BoundingBox NOWHERE = new BoundingBox(0, -10_000, 0, 0, -10_000, 0);

        @Nullable
        private volatile BeamSpot spot;
        private volatile boolean changed = true;
        private volatile boolean removed;

        @Override
        public void set(@Nullable BeamSpot next) {
            if (!Objects.equals(spot, next)) {
                spot = next;
                changed = true;
            }
        }

        @Override
        public void remove() {
            if (!removed) {
                removed = true;
                ACTIVE.decrementAndGet();
            }
        }

        @Override
        public double lightAtPos(BlockPos pos, double falloffRatio) {
            CALLS.incrementAndGet();
            BeamSpot current = spot;
            if (current == null) {
                return 0;
            }
            double light = current.lightAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, falloffRatio);
            if (light > maxReturned) {
                maxReturned = light;
            }
            return light;
        }

        @Override
        public BoundingBox getBoundingBox() {
            BeamSpot current = spot;
            if (current == null) {
                return NOWHERE;
            }
            // Same ratio LambDynamicLights passes to lightAtPos: 15 levels over its maximum radius
            int[] b = current.bounds(15 / MAX_RADIUS, MAX_RADIUS);
            return new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
        }

        @Override
        public boolean hasChanged() {
            if (changed) {
                changed = false;
                return true;
            }
            return false;
        }

        @Override
        public boolean isRemoved() {
            return removed;
        }
    }
}
