package com.goldskinmc.lightworks.content.signal;

import com.goldskinmc.lightworks.content.LightType;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import java.util.function.Supplier;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lamp whose job is to be seen: Warning Light, Tail and Brake Light, Navigation Light. Mounted on the
 * {@link #FACING} side; placed, it gives block light ({@link #LIGHT}, from its brightness, and for a
 * Warning Light its pattern), while its lens shows the pattern or brake state. Signal lamps add no dynamic light on vehicles: each moving
 * spot costs chunk rebuilds, and their light is for looking at, not for lighting the ground.
 */
public class SignalLampBlock extends DirectionalBlock implements IBE<SignalLampBlockEntity>, IWrenchable {
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);

    private final LightType lightType;
    private final Supplier<? extends BlockEntityType<? extends SignalLampBlockEntity>> blockEntityType;
    private final VoxelShaper shape;
    private final MapCodec<SignalLampBlock> codec;

    /** @param upShape outline when mounted on the floor; turned for other mounts */
    public SignalLampBlock(Properties properties, LightType lightType,
            Supplier<? extends BlockEntityType<? extends SignalLampBlockEntity>> blockEntityType, VoxelShape upShape) {
        super(properties);
        this.lightType = lightType;
        this.blockEntityType = blockEntityType;
        this.shape = VoxelShaper.forDirectional(upShape, Direction.UP);
        this.codec = simpleCodec(p -> new SignalLampBlock(p, lightType, blockEntityType, upShape));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(LIGHT, lightType.placedLight()));
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
        builder.add(FACING, LIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
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
            withBlockEntityDo(level, pos, SignalLampBlockEntity::updateSignal);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape.get(state.getValue(FACING));
    }

    /** Beams pass through lights. */
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
    public Class<SignalLampBlockEntity> getBlockEntityClass() {
        return SignalLampBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SignalLampBlockEntity> getBlockEntityType() {
        return blockEntityType.get();
    }
}
