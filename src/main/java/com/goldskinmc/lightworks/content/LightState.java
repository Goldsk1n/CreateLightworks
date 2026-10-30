package com.goldskinmc.lightworks.content;

import com.goldskinmc.lightworks.beam.Aim;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a player can set on a light, saved in its block entity, synced to clients and carried onto
 * contraptions in {@code MovementContext.blockEntityData}: the redstone signal it last read, the brightness
 * that gives, its dye colour, its aim, its pattern and whether it shines only at night.
 */
public final class LightState {
    private static final String SIGNAL = "Signal";
    private static final String BRIGHTNESS = "Brightness";
    private static final String COLOUR = "Colour";
    private static final String YAW = "Yaw";
    private static final String ELEVATION = "Elevation";
    private static final String PATTERN = "Pattern";
    private static final String NIGHT_ONLY = "NightOnly";

    private int signal;
    private int brightness = 15;
    @Nullable
    private DyeColor colour;
    private Aim aim = Aim.STRAIGHT;
    private int pattern;
    private boolean nightOnly;

    /** State read from block-entity data, or defaults when there is none. */
    public static LightState of(@Nullable CompoundTag tag) {
        LightState state = new LightState();
        if (tag != null) {
            state.read(tag);
        }
        return state;
    }

    /** Strongest redstone signal around the light when it last checked; frozen on contraptions. */
    public int signal() {
        return signal;
    }

    public void setSignal(int signal) {
        this.signal = signal;
    }

    /** 0 (off) to 15, from the signal and the {@code invertRedstone} config. */
    public int brightness() {
        return brightness;
    }

    public void setBrightness(int brightness) {
        this.brightness = Math.max(0, Math.min(15, brightness));
    }

    public boolean on() {
        return brightness > 0;
    }

    public @Nullable DyeColor colour() {
        return colour;
    }

    public void setColour(@Nullable DyeColor colour) {
        this.colour = colour;
    }

    /** Lens and beam colour, 0xRRGGBB: the dye's colour, or the light type's own. */
    public int rgb(LightType type) {
        return colour == null ? type.colour() : colour.getTextureDiffuseColor() & 0xFFFFFF;
    }

    public Aim aim() {
        return aim;
    }

    public void setAim(Aim aim) {
        this.aim = aim;
    }

    public int pattern() {
        return pattern;
    }

    public void setPattern(int pattern) {
        this.pattern = pattern;
    }

    /** True for a Warning Light set to shine only at night. */
    public boolean nightOnly() {
        return nightOnly;
    }

    public void setNightOnly(boolean nightOnly) {
        this.nightOnly = nightOnly;
    }

    public void write(CompoundTag tag) {
        tag.putInt(SIGNAL, signal);
        tag.putInt(BRIGHTNESS, brightness);
        // Defaults are left out, and removed when writing over older data
        tag.remove(COLOUR);
        tag.remove(YAW);
        tag.remove(ELEVATION);
        tag.remove(PATTERN);
        tag.remove(NIGHT_ONLY);
        if (colour != null) {
            tag.putString(COLOUR, colour.getSerializedName());
        }
        if (!aim.equals(Aim.STRAIGHT)) {
            tag.putInt(YAW, aim.yaw());
            tag.putInt(ELEVATION, aim.elevation());
        }
        if (pattern != 0) {
            tag.putInt(PATTERN, pattern);
        }
        if (nightOnly) {
            tag.putBoolean(NIGHT_ONLY, true);
        }
    }

    public void read(CompoundTag tag) {
        signal = tag.getInt(SIGNAL);
        setBrightness(tag.contains(BRIGHTNESS) ? tag.getInt(BRIGHTNESS) : 15);
        colour = tag.contains(COLOUR) ? DyeColor.byName(tag.getString(COLOUR), null) : null;
        aim = tag.contains(ELEVATION) ? new Aim(tag.getInt(YAW), tag.getInt(ELEVATION)) : Aim.STRAIGHT;
        pattern = tag.getInt(PATTERN);
        nightOnly = tag.getBoolean(NIGHT_ONLY);
    }
}
