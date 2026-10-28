package com.goldskinmc.lightworks.content.signal;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** A Navigation Light's colour: red on the left (port), green on the right (starboard), white at the stern. */
public enum NavigationColour implements StringRepresentable {
    PORT(0xFF2A1F),
    STARBOARD(0x2BE04F),
    STERN(0xFFFFFF);

    private final int rgb;

    NavigationColour(int rgb) {
        this.rgb = rgb;
    }

    public int rgb() {
        return rgb;
    }

    public NavigationColour next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
