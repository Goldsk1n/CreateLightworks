package com.goldskinmc.lightworks.light;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Invisible, intangible block that emits a beam's light where the beam lands: the server-side fallback
 * path, which works with no client mods. Placed and removed only by its owner, without neighbour or
 * shape updates, so observers and redstone never notice it. Every few seconds it checks that its owner
 * still points at it and removes itself if not, so a light can never be left behind. It goes into air or
 * a water source, waterlogged then like vanilla's Light block, and gives the water back when it goes: placed in
 * air only, a beam shining into water lit nothing under it.
 */
public class BeamLightBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** Block flags for placing and removing beam lights: sync to clients, no neighbour or shape updates. */
    public static final int QUIET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int CHECK_INTERVAL_TICKS = 100;

    public BeamLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 15).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL, WATERLOGGED);
    }

    /** Whether a beam light may go where {@code state} is: air or a water source. */
    public static boolean canHold(BlockState state) {
        return state.isAir() || isWaterSource(state);
    }

    private static boolean isWaterSource(BlockState state) {
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    /** {@code light} at {@code level}, to put where {@code replaced} is: waterlogged if that was water. */
    public static BlockState replacing(BlockState replaced, BlockState light, int level) {
        return light.setValue(LEVEL, level).setValue(WATERLOGGED, isWaterSource(replaced));
    }

    /** What a beam light leaves when it goes: its water, or air. */
    public static BlockState emptied(BlockState light) {
        return light.hasProperty(WATERLOGGED) && light.getValue(WATERLOGGED)
                ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return true;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, CHECK_INTERVAL_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof BeamLightBlockEntity light && light.isOrphaned(level)) {
            level.setBlock(pos, emptied(state), QUIET_FLAGS);
            return;
        }
        level.scheduleTick(pos, this, CHECK_INTERVAL_TICKS);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeamLightBlockEntity(pos, state);
    }
}
