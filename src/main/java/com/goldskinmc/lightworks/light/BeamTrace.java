package com.goldskinmc.lightworks.light;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Finds where a beam stops: the first block whose visual shape it meets, or the end of its range.
 * Lives here rather than in {@code beam/} because it needs the world; the beam maths stays pure.
 */
public final class BeamTrace {
    /** Start the ray just past the lens, so it never hits the light's own block. */
    private static final double START_OFFSET = 0.01;

    private BeamTrace() {
    }

    /**
     * @param end      where the beam stops, on the hit face or at full range
     * @param block    the block it hit, or null if it ran out of range
     * @param distance from {@code origin} to {@code end}, in blocks
     */
    public record Hit(Vec3 end, @Nullable BlockPos block, double distance) {
        public boolean hitBlock() {
            return block != null;
        }
    }

    public static Hit trace(BlockGetter level, Vec3 origin, Vec3 direction, double range) {
        Vec3 dir = direction.normalize();
        Vec3 start = origin.add(dir.scale(START_OFFSET));
        Vec3 limit = origin.add(dir.scale(range));
        BlockHitResult result = level.clip(new ClipContext(start, limit,
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (result.getType() == HitResult.Type.MISS) {
            return new Hit(limit, null, range);
        }
        Vec3 end = result.getLocation();
        // clip() can hand back a mutable position that it reuses; keep our own copy
        return new Hit(end, result.getBlockPos().immutable(), end.distanceTo(origin));
    }
}
