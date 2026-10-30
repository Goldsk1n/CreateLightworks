package com.goldskinmc.lightworks.content.signal;

import com.goldskinmc.lightworks.content.LightGoggles;
import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.LightworksConfig;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Holds no data and never ticks: an Edge Light has one so Engineer's Goggles can read its state. */
public class EdgeLightBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public EdgeLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        BlockState state = getBlockState();
        LightState shown = new LightState();
        shown.setBrightness(state.getValue(EdgeLightBlock.BRIGHTNESS));
        LightGoggles.title(tooltip, state.getBlock().getName());
        LightGoggles.brightness(tooltip, shown);
        LightGoggles.line(tooltip, "block_light", ChatFormatting.GOLD, EdgeLightBlock.lightLevel(state));
        LightGoggles.colour(tooltip, state.getValue(EdgeLightBlock.COLOUR));
        if (isPlayerSneaking) {
            LightGoggles.hint(tooltip, "dye");
            LightGoggles.hint(tooltip, LightworksConfig.invertRedstone() ? "redstone_inverted" : "redstone");
        }
        return true;
    }
}
