package com.goldskinmc.lightworks.content.signal;

import com.goldskinmc.lightworks.content.LightType;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Red, green or white marker. The colour comes from placement, with no vehicle "forward" to detect: a
 * light facing the placer's left is red (port), facing their right green (starboard), anything else white
 * (stern). A Wrench cycles the colour.
 */
public class NavigationLightBlock extends SignalLampBlock {
    public static final EnumProperty<NavigationColour> COLOUR = EnumProperty.create("colour", NavigationColour.class);

    private final MapCodec<NavigationLightBlock> codec;

    public NavigationLightBlock(Properties properties, LightType lightType,
            Supplier<? extends BlockEntityType<? extends SignalLampBlockEntity>> blockEntityType, VoxelShape upShape) {
        super(properties, lightType, blockEntityType, upShape);
        this.codec = simpleCodec(p -> new NavigationLightBlock(p, lightType, blockEntityType, upShape));
        registerDefaultState(defaultBlockState().setValue(COLOUR, NavigationColour.STERN));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOUR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(COLOUR, colourFor(context.getClickedFace(),
                context.getHorizontalDirection()));
    }

    /** Colour for a light facing {@code facing}, placed by a player looking {@code looking}. */
    public static NavigationColour colourFor(Direction facing, Direction looking) {
        if (facing == looking.getCounterClockWise()) {
            return NavigationColour.PORT;
        }
        if (facing == looking.getClockWise()) {
            return NavigationColour.STARBOARD;
        }
        return NavigationColour.STERN;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        level.setBlock(context.getClickedPos(), state.cycle(COLOUR), Block.UPDATE_ALL);
        if (!level.isClientSide) {
            IWrenchable.playRotateSound(level, context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }
}
