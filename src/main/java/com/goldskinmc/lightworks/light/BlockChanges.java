package com.goldskinmc.lightworks.light;

import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Blocks that changed shape in the client world, for what is painted on them: a fixed beam finds its faces once, so
 * it has to hear when one goes. Changes a beam could not tell apart (a lamp lighting up, crops growing, its own light
 * blocks coming and going) are left out. Client thread only.
 */
public final class BlockChanges {
    /** More changes than this between two looks (an explosion) count as a change everywhere. */
    private static final int KEPT = 256;
    private static final long[] RECENT = new long[KEPT];
    private static long count;

    private BlockChanges() {
    }

    public static void changed(BlockGetter level, BlockPos pos, BlockState before, BlockState after) {
        if (before == after || !ClientSafety.works(ClientSafety.LAYERS)) {
            return;
        }
        try {
            if (before.isSolidRender(level, pos) != after.isSolidRender(level, pos)
                    || before.getVisualShape(level, pos, CollisionContext.empty()) != after.getVisualShape(level, pos, CollisionContext.empty())) {
                RECENT[(int) (count++ % KEPT)] = pos.asLong();
            }
        } catch (RuntimeException e) {
            ClientSafety.failed(ClientSafety.LAYERS, e);
        }
    }

    /** How many changes there have been: pass it to {@link #any} next time. */
    static long count() {
        return count;
    }

    /** True if a block changed since {@code since} that {@code matters}, given as {@link BlockPos#asLong}. */
    static boolean any(long since, LongPredicate matters) {
        if (count - since > KEPT) {
            return true;
        }
        for (long i = since; i < count; i++) {
            if (matters.test(RECENT[(int) (i % KEPT)])) {
                return true;
            }
        }
        return false;
    }
}
