package com.goldskinmc.lightworks.content.signal;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import java.util.Locale;
import net.minecraft.util.Mth;

/**
 * How a Warning Light's lens shows, as a function of game time only, so every client flashes in step
 * without packets: on placed lamps, on contraptions and in Ponder scenes alike.
 */
public enum WarningPattern implements INamedIconOptions {
    /** Always on. */
    STEADY(AllIcons.I_PATTERN_SOLID),
    /** Half a second on, half a second off. */
    BLINK(AllIcons.I_PATTERN_CHECKERED),
    /** Blink's other half: a pair of lamps takes turns, like a level crossing. */
    OPPOSITE_BLINK(AllIcons.I_PATTERN_CHECKERED_INVERSED),
    /** Fades up and down every 1.5 seconds. */
    PULSE(AllIcons.I_PATTERN_CHANCE_75),
    /** Two short flashes every 1.5 seconds, like an aircraft's anti-collision light. */
    DOUBLE_FLASH(AllIcons.I_PATTERN_CHANCE_50),
    /** One sharp flash a second: kept this slow for players sensitive to flashing light. */
    STROBE(AllIcons.I_PATTERN_CHANCE_25);

    /** A strobe's steady share of its block light: relighting the area at every flash would cost too much. */
    private static final float STROBE_BLOCK_LIGHT = 0.25f;

    private final AllIcons icon;

    WarningPattern(AllIcons icon) {
        this.icon = icon;
    }

    public static WarningPattern of(int ordinal) {
        WarningPattern[] all = values();
        return all[Math.floorMod(ordinal, all.length)];
    }

    /** Lens brightness 0 to 1 at {@code gameTime + partialTick}. */
    public float at(long gameTime, float partialTick) {
        double t = gameTime + partialTick;
        return switch (this) {
            case STEADY -> 1f;
            case BLINK -> (gameTime % 20) < 10 ? 1f : 0f;
            case OPPOSITE_BLINK -> (gameTime % 20) < 10 ? 0f : 1f;
            case PULSE -> (float) (0.5 - 0.5 * Math.cos(t * Mth.TWO_PI / 30));
            case DOUBLE_FLASH -> {
                long phase = gameTime % 30;
                yield phase < 2 || (phase >= 5 && phase < 7) ? 1f : 0f;
            }
            case STROBE -> (gameTime % 20) < 2 ? 1f : 0f;
        };
    }

    /**
     * Share of a placed lamp's block light at {@code gameTime}: the lens pattern, with the pulse in
     * quarter steps so the light (and the chunk it sits in) only changes a few times per cycle, and a strobe
     * steady and low.
     */
    public float blockLight(long gameTime) {
        if (this == STROBE) {
            return STROBE_BLOCK_LIGHT;
        }
        float lens = at(gameTime, 0);
        return this == PULSE ? Math.round(lens * 4) / 4f : lens;
    }

    @Override
    public AllIcons getIcon() {
        return icon;
    }

    /** Create 6.0.0 looks this up with a "create." prefix and 6.0.10 as it is: the lang has both. */
    @Override
    public String getTranslationKey() {
        return "lightworks.warning_pattern." + name().toLowerCase(Locale.ROOT);
    }
}
