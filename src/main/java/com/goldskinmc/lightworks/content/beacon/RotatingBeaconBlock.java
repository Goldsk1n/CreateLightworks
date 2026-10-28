package com.goldskinmc.lightworks.content.beacon;

import com.goldskinmc.lightworks.registry.LightworksBlockEntities;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import com.goldskinmc.lightworks.content.Dyeing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A beacon whose rotor turns with the shaft coming in from behind its mount; each turn sweeps two
 * opposite beams round it.
 */
public class RotatingBeaconBlock extends DirectionalKineticBlock implements IBE<RotatingBeaconBlockEntity> {
    private static final VoxelShaper SHAPE = VoxelShaper.forDirectional(Block.box(1, 0, 1, 15, 13.5, 15), Direction.UP);

    public RotatingBeaconBlock(Properties properties) {
        super(properties);
    }

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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }

    /** Beams pass through lights. */
    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Dyes tint the lens and beam. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        return onBlockEntityUseItemOn(level, pos, light -> Dyeing.use(stack, level, pos, player, light, light.lightState()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos neighbourPos,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbour, neighbourPos, movedByPiston);
        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, RotatingBeaconBlockEntity::updateSignal);
        }
    }

    @Override
    public Class<RotatingBeaconBlockEntity> getBlockEntityClass() {
        return RotatingBeaconBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RotatingBeaconBlockEntity> getBlockEntityType() {
        return LightworksBlockEntities.ROTATING_BEACON.get();
    }
}
