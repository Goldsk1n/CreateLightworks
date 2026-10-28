package com.goldskinmc.lightworks.content.directional;

import com.goldskinmc.lightworks.content.LightType;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A light with an aimable beam: Searchlight, Floodlight and Headlight, told apart by their
 * {@link LightType}. {@link #FACING} is the side it is mounted on (pointing away from the surface it was
 * placed against); the block entity stores where the beam points relative to that mount.
 * <p>
 * Aim is "point where I look": placing the light, or right-clicking it with a Wrench, aims it along the
 * player's look direction. A sneaking Wrench picks it up as usual.
 */
public class DirectionalLightBlock extends DirectionalBlock implements IBE<DirectionalLightBlockEntity>, IWrenchable {
    private final LightType lightType;
    private final Supplier<? extends BlockEntityType<? extends DirectionalLightBlockEntity>> blockEntityType;
    private final VoxelShaper shape;
    private final MapCodec<DirectionalLightBlock> codec;

    /** @param upShape outline when mounted on the floor; turned for other mounts */
    public DirectionalLightBlock(Properties properties, LightType lightType,
            Supplier<? extends BlockEntityType<? extends DirectionalLightBlockEntity>> blockEntityType, VoxelShape upShape) {
        super(properties);
        this.lightType = lightType;
        this.blockEntityType = blockEntityType;
        this.shape = VoxelShaper.forDirectional(upShape, Direction.UP);
        this.codec = simpleCodec(p -> new DirectionalLightBlock(p, lightType, blockEntityType, upShape));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    public LightType lightType() {
        return lightType;
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    /** Placement aims the light where the player looks, like the Wrench. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null) {
            withBlockEntityDo(level, pos, light -> light.aimAlong(placer.getLookAngle()));
        }
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        withBlockEntityDo(level, context.getClickedPos(), light -> {
            if (light.aimAlong(player.getLookAngle()) && !level.isClientSide) {
                IWrenchable.playRotateSound(level, context.getClickedPos());
            }
        });
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos neighbourPos,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbour, neighbourPos, movedByPiston);
        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, DirectionalLightBlockEntity::updateSignal);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape.get(state.getValue(FACING));
    }

    /** Beams pass through lights, so a tilted beam never stops on its own block. */
    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
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
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<DirectionalLightBlockEntity> getBlockEntityClass() {
        return DirectionalLightBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DirectionalLightBlockEntity> getBlockEntityType() {
        return blockEntityType.get();
    }
}
