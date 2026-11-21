package com.goldskinmc.lightworks.content.sensor;

import com.goldskinmc.lightworks.content.Dyeing;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import java.util.function.Supplier;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Beam Sensor: a lens on the {@link #FACING} side of its mount that gives a redstone signal while one of our beams lights
 * it, as strong as the light there ({@link com.goldskinmc.lightworks.light.BeamSensors}). Dyed, it answers only beams of
 * that colour. Like a lever, it powers the block it hangs on strongly and its other neighbours weakly.
 */
public class BeamSensorBlock extends DirectionalBlock implements IBE<BeamSensorBlockEntity>, IWrenchable {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    private final Supplier<? extends BlockEntityType<? extends BeamSensorBlockEntity>> blockEntityType;
    private final VoxelShaper shape;
    private final MapCodec<BeamSensorBlock> codec;

    /** @param upShape outline when mounted on the floor; turned for other mounts */
    public BeamSensorBlock(Properties properties,
            Supplier<? extends BlockEntityType<? extends BeamSensorBlockEntity>> blockEntityType, VoxelShape upShape) {
        super(properties);
        this.blockEntityType = blockEntityType;
        this.shape = VoxelShaper.forDirectional(upShape, Direction.UP);
        this.codec = simpleCodec(p -> new BeamSensorBlock(p, blockEntityType, upShape));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    /** A dye sets the colour it answers to, as on the lights. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        return onBlockEntityUseItemOn(level, pos, sensor -> Dyeing.use(stack, level, pos, player, sensor, sensor.lightState()));
    }

    /** Shows {@code power}, updating the redstone round it when it changed (server). */
    public void setPower(Level level, BlockPos pos, BlockState state, int power) {
        if (state.getValue(POWER) == power) {
            return;
        }
        level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_ALL);
        updateNeighbours(level, pos, state);
    }

    private void updateNeighbours(Level level, BlockPos pos, BlockState state) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), this);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    /** Strongly into the block it hangs on, as a lever does, so redstone behind a wall works. */
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == state.getValue(FACING) ? state.getValue(POWER) : 0;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape.get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean powered = state.getValue(POWER) > 0 && !state.is(newState.getBlock());
        IBE.onRemove(state, level, pos, newState);
        if (powered) {
            updateNeighbours(level, pos, state);
        }
    }

    @Override
    public Class<BeamSensorBlockEntity> getBlockEntityClass() {
        return BeamSensorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BeamSensorBlockEntity> getBlockEntityType() {
        return blockEntityType.get();
    }
}
