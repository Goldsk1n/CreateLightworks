package com.goldskinmc.lightworks.content.pantilt;

import com.goldskinmc.lightworks.registry.LightworksBlockEntities;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mount for a Searchlight or Floodlight placed on its {@link #FACING} side. The shaft comes in from the
 * opposite side, the one it was placed against.
 */
public class PanTiltHeadBlock extends DirectionalKineticBlock implements IBE<PanTiltHeadBlockEntity> {
    public PanTiltHeadBlock(Properties properties) {
        super(properties);
    }

    /** Facing away from the clicked surface, like the lights it carries. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos neighbourPos,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbour, neighbourPos, movedByPiston);
        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, PanTiltHeadBlockEntity::updateSignal);
        }
    }

    @Override
    public Class<PanTiltHeadBlockEntity> getBlockEntityClass() {
        return PanTiltHeadBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PanTiltHeadBlockEntity> getBlockEntityType() {
        return LightworksBlockEntities.PAN_TILT_HEAD.get();
    }
}
