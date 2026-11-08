package com.goldskinmc.lightworks.content.signal;

import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.beam.DayCycle;
import com.goldskinmc.lightworks.content.LightClipboard;
import com.goldskinmc.lightworks.content.LightGoggles;
import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.host.SableHost;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.goldskinmc.lightworks.light.ProjectedTint;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
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
public class SignalLampBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, ClipboardCloneable {
    /** How bright a Tail and Brake Light's lens is while not braking. */
    public static final float TAIL_GLOW = 0.45f;

    private final LightState state = new LightState();
    private final BrakeLight brake = new BrakeLight();
    /** Client ticks seen, the pattern clock outside the real world (Ponder pauses game time). */
    private long previewTicks;
    /** The lamp's colour painted on the blocks round it; client only, made on the first tick. */
    @Nullable
    private ProjectedTint glow;
    private int glowRgb;
    @Nullable
    private Vec3 glowAt;
    private boolean glowDue = true;
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
            pattern = new PatternBoard(this);
            behaviours.add(pattern);
        }
    }

    public WarningPattern pattern() {
        return pattern == null ? WarningPattern.STEADY : pattern.get();
    }

    /** Warning Light only: sets the pattern, as its value box does. */
    public void setPattern(WarningPattern next) {
        if (pattern != null) {
            pattern.setValue(next.ordinal());
        }
    }

    @Override
    public String getClipboardKey() {
        return LightClipboard.KEY;
    }

    /** The colour; a Warning Light's pattern is on the Clipboard through its value box. */
    @Override
    public boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side) {
        LightClipboard.writeColour(tag, state);
        return true;
    }

    @Override
    public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player, Direction side,
            boolean simulate) {
        if (!LightClipboard.hasColour(tag)) {
            return false;
        }
        if (simulate) {
            return true;
        }
        boolean pasted = LightClipboard.readColour(tag, state, player);
        notifyUpdate();
        return pasted;
    }

    /** Lights the brake light for a second, as if the vehicle had slowed (Ponder scenes). */
    public void showBrake() {
        brake.trigger();
    }

    /** Lens colour, 0xRRGGBB: the dye or the type's own. */
    public int rgb() {
        return state.rgb(lightType());
    }

    /** Lens brightness 0 to 1 for the frame being drawn. Client only. */
    public float intensity(float partialTick) {
        // Game time keeps every player's lamps in step; a Ponder scene runs on its own clock
        long time = level == null ? 0 : ClientBeams.isRealWorld(level) ? level.getGameTime() : previewTicks;
        boolean shining = !state.nightOnly() || level == null || !ClientBeams.isRealWorld(level) || isDark(level);
        return intensity(lightType(), state.brightness(), pattern(), shining, brake.braking(), time, partialTick);
    }

    /** True at night, and always where the clock stands still (the Nether, the End). */
    public static boolean isDark(Level level) {
        return level.dimensionType().hasFixedTime() || DayCycle.isNight(level.getDayTime());
    }

    /** Lens brightness of a signal lamp, placed or on a contraption, at game time {@code time}. */
    public static float intensity(LightType type, int brightness, WarningPattern pattern, boolean shining,
            boolean braking, long time, float partialTick) {
        float base = brightness / 15f;
        return switch (type.kind()) {
            case WARNING -> shining ? base * pattern.at(time, partialTick) : 0;
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
        if (level != null && !level.isClientSide && lightType().kind() == LightType.Kind.WARNING) {
            // A blinking lamp lights the world only while its lens is lit
            updateBlockLight();
        }
        if (level != null && level.isClientSide) {
            previewTicks++;
            updateGlow();
        }
        if (level != null && level.isClientSide && lightType().kind() == LightType.Kind.TAIL) {
            // Null when not on a Sable vehicle: a fixed tail light never starts braking, but a brake
            // shown in a Ponder scene still runs out
            brake.tick(SableHost.velocity(level, worldPosition));
        }
    }

    /**
     * Colours the blocks round the lamp like its lens. The lit faces are found again now and then (blocks
     * placed or broken nearby) and when the colour changes; a pattern only changes the strength.
     */
    private void updateGlow() {
        if (!ClientBeams.isRealWorld(level)) {
            return;
        }
        if (!LightworksClientConfig.colouredLight()) {
            // Off under shader packs or by config: nothing would draw it. Found again once it is back on
            if (glow != null) {
                glow.clear();
            }
            glowAt = null;
            return;
        }
        if (glow == null) {
            glow = new ProjectedTint();
        }
        int rgb = rgb();
        // On a Sable vehicle the block sits in Sable's plot: the glow goes where the vehicle shows it
        Vec3 at = SableHost.toWorld(level, worldPosition, Vec3.atCenterOf(worldPosition), new Vec3(0, 1, 0)).origin();
        // Moved with its vehicle: faces found again every block (finding them is the costly part)
        if (glowDue || rgb != glowRgb || glowAt == null || glowAt.distanceToSqr(at) > 1) {
            glowDue = false;
            glowRgb = rgb;
            glowAt = at;
            glow.glow(level, at, glowRadius(lightType()), rgb, intensity(0));
        } else {
            glow.setBrightness(intensity(0));
        }
    }

    /** How far a lamp's coloured glow reaches, in blocks: further for a brighter lamp. */
    public static double glowRadius(LightType type) {
        return 1 + type.placedLight() * 0.4;
    }

    /** SmartBlockEntity calls this when the block is removed and when its chunk unloads. */
    @Override
    public void invalidate() {
        super.invalidate();
        if (glow != null) {
            glow.clear();
            glow = null;
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        glowDue = true;
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
        updateBlockLight();
    }

    /**
     * The {@code LIGHT} level the lamp gives: its brightness, and for a Warning Light its pattern, like a
     * redstone lamp on a clock (each change relights the area and rebuilds the chunk section, so the
     * pulse moves in quarter steps).
     */
    private void updateBlockLight() {
        float pattern = 1;
        if (lightType().kind() == LightType.Kind.WARNING) {
            pattern = state.nightOnly() && !isDark(level) ? 0 : pattern().blockLight(level.getGameTime());
        }
        int lightLevel = Math.round(lightType().placedLight() * state.brightness() / 15f * pattern);
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
        if (type.kind() == LightType.Kind.WARNING) {
            LightGoggles.line(tooltip, "pattern", ChatFormatting.AQUA,
                    PatternBoard.named(Component.translatable(pattern().getTranslationKey()), state.nightOnly()));
        }
        if (isPlayerSneaking) {
            if (type.kind() == LightType.Kind.WARNING) {
                LightGoggles.hint(tooltip, "pattern");
            }
            LightGoggles.hint(tooltip, "dye");
            LightGoggles.hint(tooltip, "clipboard");
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
