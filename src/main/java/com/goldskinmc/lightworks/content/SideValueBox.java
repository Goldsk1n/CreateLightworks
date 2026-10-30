package com.goldskinmc.lightworks.content;

import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A value box on a side of a block narrower than its block space. It is drawn off the outline, while a click
 * lands on the outline, so a click counts by where it is across the face, anywhere on the box's square.
 */
public abstract class SideValueBox extends CenteredSideValueBoxTransform {
    protected SideValueBox(BiPredicate<BlockState, Direction> allowedSides) {
        super(allowedSides);
    }

    @Override
    public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
        if (!isSideActive(state, getSide())) {
            return false;
        }
        Vec3 d = localHit.subtract(getLocalOffset(level, pos, state));
        double half = scale / 2;
        Direction.Axis depth = getSide().getAxis();
        return (depth == Direction.Axis.X || Math.abs(d.x) < half) && (depth == Direction.Axis.Y || Math.abs(d.y) < half)
                && (depth == Direction.Axis.Z || Math.abs(d.z) < half);
    }
}
