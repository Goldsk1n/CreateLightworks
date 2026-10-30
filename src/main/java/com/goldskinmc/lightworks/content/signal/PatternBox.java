package com.goldskinmc.lightworks.content.signal;

import com.goldskinmc.lightworks.content.SideValueBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The Warning Light's pattern box, on every side face: centred on the lamp's body, just off its foot. */
final class PatternBox extends SideValueBox {
    /** From the block centre to the box, half a pixel off the foot, in blocks. */
    private static final double OUT = 4.5 / 16;
    /** From the mount to the box's centre: halfway up the 10 pixel lamp, in blocks. */
    private static final double ALONG = 5 / 16.0;

    PatternBox() {
        super((state, side) -> side.getAxis() != state.getValue(SignalLampBlock.FACING).getAxis());
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction mount = state.getValue(SignalLampBlock.FACING);
        Vec3 side = Vec3.atLowerCornerOf(getSide().getNormal());
        Vec3 up = Vec3.atLowerCornerOf(mount.getNormal());
        return new Vec3(0.5, 0.5, 0.5).add(side.scale(OUT)).add(up.scale(ALONG - 0.5));
    }
}
