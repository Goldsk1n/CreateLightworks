package com.goldskinmc.lightworks.content.sensor;

import com.goldskinmc.lightworks.content.LightGoggles;
import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.light.BeamSensors;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Beam Sensor's dye filter, saved and synced, and its goggle lines. It ticks nothing: while loaded it is on the server's
 * list ({@link BeamSensors}), which lights shine at and which sets its signal after each tick.
 */
public class BeamSensorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /** Only its colour is used: the dye it answers to, none for any beam. */
    private final LightState state = new LightState();
    private DyeColor registeredFilter;
    private boolean registered;

    public BeamSensorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public LightState lightState() {
        return state;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        register();
    }

    @Override
    public void tick() {
        super.tick();
        // A dye click changes the filter: the list follows
        if (registered && registeredFilter != state.colour()) {
            register();
        }
    }

    private void register() {
        if (level != null && !level.isClientSide) {
            BeamSensors.put(level, worldPosition, getBlockState().getValue(BeamSensorBlock.FACING), state.colour());
            registeredFilter = state.colour();
            registered = true;
        }
    }

    /** SmartBlockEntity calls this when the block is removed and when its chunk unloads. */
    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null && !level.isClientSide) {
            BeamSensors.remove(level, worldPosition);
        }
        registered = false;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LightGoggles.title(tooltip, getBlockState().getBlock().getName());
        int power = getBlockState().getValue(BeamSensorBlock.POWER);
        LightGoggles.line(tooltip, "sensor_signal", power > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY, power);
        DyeColor filter = state.colour();
        if (filter == null) {
            LightGoggles.line(tooltip, "sensor_filter_any", ChatFormatting.AQUA);
        } else {
            LightGoggles.line(tooltip, "sensor_filter", ChatFormatting.AQUA,
                    Component.translatable("color.minecraft." + filter.getName()));
        }
        if (isPlayerSneaking) {
            LightGoggles.hint(tooltip, "sensor_dye");
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        state.write(tag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        state.read(tag);
    }
}
