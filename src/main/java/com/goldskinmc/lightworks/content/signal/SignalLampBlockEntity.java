package com.goldskinmc.lightworks.content.signal;

import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.content.LightGoggles;
import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.host.SableHost;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A placed signal lamp: its redstone-driven brightness (also the block light it gives), its colour and,
 * for a Warning Light, the pattern picked with its value box. A Tail and Brake Light on a Sable vehicle
 * watches the vehicle's speed on the client and brightens when it slows.
 */
public class SignalLampBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /** How bright a Tail and Brake Light's lens is while not braking. */
    public static final float TAIL_GLOW = 0.45f;

    private final LightState state = new LightState();
    private final BrakeLight brake = new BrakeLight();
    /** Warning Light only; set in addBehaviours, which runs before field initialisers. */
    @Nullable
    private ScrollOptionBehaviour<WarningPattern> pattern;

    public SignalLampBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    private static LightType typeOf(BlockState blockState) {
        return blockState.getBlock() instanceof SignalLampBlock block ? block.lightType() : LightType.WARNING_LIGHT;
    }

    public LightType lightType() {
        return typeOf(getBlockState());
    }

    public LightState lightState() {
        return state;
    }

    public Direction facing() {
        return getBlockState().getValue(SignalLampBlock.FACING);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        if (typeOf(getBlockState()).kind() == LightType.Kind.WARNING) {
            pattern = new ScrollOptionBehaviour<>(WarningPattern.class,
                    Component.translatable("lightworks.warning_light.pattern"), this,
                    new CenteredSideValueBoxTransform((s, d) -> d.getAxis() != s.getValue(SignalLampBlock.FACING).getAxis()));
            behaviours.add(pattern);
        }
    }

    public WarningPattern pattern() {
        return pattern == null ? WarningPattern.STEADY : pattern.get();
    }

    /** Lens colour, 0xRRGGBB: the Navigation Light's side colour, otherwise the dye or the type's own. */
    public int rgb() {
        if (getBlockState().hasProperty(NavigationLightBlock.COLOUR)) {
            return getBlockState().getValue(NavigationLightBlock.COLOUR).rgb();
        }
        return state.rgb(lightType());
    }

    /** Lens brightness 0 to 1 for the frame being drawn. */
    public float intensity(float partialTick) {
        return intensity(lightType(), state.brightness(), pattern(), brake.braking(), level, partialTick);
    }

    /** Lens brightness of a signal lamp, placed or on a contraption. */
    public static float intensity(LightType type, int brightness, WarningPattern pattern, boolean braking,
            @Nullable Level level, float partialTick) {
        float base = brightness / 15f;
        return switch (type.kind()) {
            case WARNING -> base * (level == null ? 1 : pattern.at(level.getGameTime(), partialTick));
            case TAIL -> base * (braking ? 1 : TAIL_GLOW);
            default -> base;
        };
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            updateSignal();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide && lightType().kind() == LightType.Kind.TAIL) {
            // Null when not on a Sable vehicle: a fixed tail light never brakes
            Vec3 velocity = SableHost.velocity(level, worldPosition);
            if (velocity != null) {
                brake.tick(velocity);
            }
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide) {
            refreshBrightness();
        }
    }

    /** Reads the redstone around the lamp (server). */
    public void updateSignal() {
        if (level == null || level.isClientSide) {
            return;
        }
        int signal = level.getBestNeighborSignal(worldPosition);
        if (signal != state.signal()) {
            state.setSignal(signal);
            setChanged();
        }
        refreshBrightness();
    }

    private void refreshBrightness() {
        int brightness = LightworksConfig.brightnessFor(state.signal());
        if (brightness != state.brightness()) {
            state.setBrightness(brightness);
            setChanged();
            sendData();
        }
        // Steady block light, whatever the lens pattern: blinking light levels would rebuild chunks
        int lightLevel = Math.round(lightType().placedLight() * brightness / 15f);
        BlockState blockState = getBlockState();
        if (blockState.getValue(SignalLampBlock.LIGHT) != lightLevel) {
            level.setBlock(worldPosition, blockState.setValue(SignalLampBlock.LIGHT, lightLevel), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LightType type = lightType();
        LightGoggles.title(tooltip, getBlockState().getBlock().getName());
        LightGoggles.brightness(tooltip, state);
        LightGoggles.line(tooltip, "block_light", ChatFormatting.GOLD, getBlockState().getValue(SignalLampBlock.LIGHT));
        switch (type.kind()) {
            case WARNING -> LightGoggles.line(tooltip, "pattern", ChatFormatting.AQUA,
                    Component.translatable("create." + pattern().getTranslationKey()));
            case TAIL -> LightGoggles.line(tooltip, brake.braking() ? "brake_now" : "brake", ChatFormatting.AQUA);
            case NAVIGATION -> LightGoggles.line(tooltip, "side", ChatFormatting.AQUA, Component.translatable(
                    "lightworks.navigation." + getBlockState().getValue(NavigationLightBlock.COLOUR).getSerializedName()));
            default -> {
            }
        }
        if (type.kind() != LightType.Kind.NAVIGATION) {
            LightGoggles.colour(tooltip, state.colour());
        }
        if (isPlayerSneaking) {
            switch (type.kind()) {
                case WARNING -> LightGoggles.hint(tooltip, "pattern");
                case NAVIGATION -> LightGoggles.hint(tooltip, "navigation");
                default -> {
                }
            }
            if (type.kind() != LightType.Kind.NAVIGATION) {
                LightGoggles.hint(tooltip, "dye");
            }
            LightGoggles.hint(tooltip, LightworksConfig.invertRedstone() ? "redstone_inverted" : "redstone");
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        // Mirrors the value box, so contraption copies can read the pattern
        state.setPattern(pattern().ordinal());
        state.write(tag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        state.read(tag);
    }
}
