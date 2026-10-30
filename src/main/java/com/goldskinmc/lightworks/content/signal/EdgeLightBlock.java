package com.goldskinmc.lightworks.content.signal;

import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.registry.LightworksBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A low marker for runway, taxiway and platform edges, placed by the hundred. So it costs next to nothing:
 * colour and brightness live in the block state, the lens is a tinted full-bright part of the baked model
 * (contraptions and Sable vehicles draw it like any other block), nothing ticks, and it does not colour the
 * blocks round it. Vehicles roll and players walk over it: it has no collision.
 */
public class EdgeLightBlock extends DirectionalBlock implements EntityBlock, IWrenchable {
    public static final EnumProperty<DyeColor> COLOUR = EnumProperty.create("colour", DyeColor.class);
    public static final IntegerProperty BRIGHTNESS = IntegerProperty.create("brightness", 0, 15);
    /** The block light of a marker at full brightness: enough to see what it stands on, not to light a runway. */
    private static final int FULL_LIGHT = 4;
    private static final VoxelShaper SHAPE = VoxelShaper.forDirectional(Block.box(5, 0, 5, 11, 3, 11), Direction.UP);
    private static final MapCodec<EdgeLightBlock> CODEC = simpleCodec(EdgeLightBlock::new);

    public EdgeLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(COLOUR, DyeColor.WHITE)
                .setValue(BRIGHTNESS, 15));
    }

    /**
     * Create leaves blocks without collision behind when it assembles a contraption. Brittle blocks go along, like
     * carpet does, and this one hangs on the block it is mounted on.
     */
    public static void registerMovementChecks() {
        BlockMovementChecks.registerBrittleCheck(state -> state.getBlock() instanceof EdgeLightBlock
                ? BlockMovementChecks.CheckResult.SUCCESS : BlockMovementChecks.CheckResult.PASS);
        BlockMovementChecks.registerAttachedCheck((state, level, pos, direction) -> {
            if (!(state.getBlock() instanceof EdgeLightBlock)) {
                return BlockMovementChecks.CheckResult.PASS;
            }
            return direction == state.getValue(FACING).getOpposite()
                    ? BlockMovementChecks.CheckResult.SUCCESS : BlockMovementChecks.CheckResult.FAIL;
        });
    }

    /** The block light this state gives: none only when fully off. */
    public static int lightLevel(BlockState state) {
        int brightness = state.getValue(BRIGHTNESS);
        return brightness == 0 ? 0 : Math.max(1, Math.round((float) FULL_LIGHT * brightness / 15));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLOUR, BRIGHTNESS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withSignal(defaultBlockState().setValue(FACING, context.getClickedFace()),
                context.getLevel().getBestNeighborSignal(context.getClickedPos()));
    }

    private static BlockState withSignal(BlockState state, int signal) {
        return state.setValue(BRIGHTNESS, LightworksConfig.brightnessFor(signal));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos neighbourPos,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbour, neighbourPos, movedByPiston);
        if (!level.isClientSide) {
            BlockState next = withSignal(state, level.getBestNeighborSignal(pos));
            if (next != state) {
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Dyes colour the lens. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof DyeItem dye) || dye.getDyeColor() == state.getValue(COLOUR)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(COLOUR, dye.getDyeColor()), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1, 1);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Turning it would only take it off its mount; a sneaking Wrench picks it up as usual. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return LightworksBlockEntities.EDGE_LIGHT.create(pos, state);
    }
}
