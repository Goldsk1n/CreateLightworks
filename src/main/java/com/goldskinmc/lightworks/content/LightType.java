package com.goldskinmc.lightworks.content;

import com.goldskinmc.lightworks.beam.ConeFalloff;
import org.jetbrains.annotations.Nullable;

/**
 * What kind of light a block is: its beam shape (none for omni lamps), how it behaves, its default
 * colour and how much block light it gives when placed. Blocks, block entities, movement behaviours and
 * renderers read it instead of hard-coded constants.
 *
 * @param beam        the cone of a directional light, null for lamps that only glow
 * @param colour      default lens and beam colour, 0xRRGGBB
 * @param placedLight  block light at full brightness for a placed lamp; beams light where they land instead
 * @param lensDistance how far the lens glass is from the block centre (the head's pivot), in blocks: the
 *                     beam starts there, flush on the glass
 * @param lensHalf     half the lens's width, in blocks: the beam starts this wide
 */
public record LightType(String name, @Nullable ConeFalloff beam, Kind kind, int colour, int placedLight,
        double lensDistance, double lensHalf) {
    public enum Kind {
        /** Points where it is aimed (Searchlight, Floodlight). */
        BEAM,
        /** A beam that on a moving vehicle lights only while leading. */
        HEADLIGHT,
        /** Two opposite beams turned by a shaft. */
        BEACON,
        /** Omni lamp with a steady, blink, pulse or flash pattern. */
        WARNING,
        /** Dim red, brighter for a second when the vehicle slows. */
        TAIL,
        /** Red, green or white marker. */
        NAVIGATION
    }

    public static final int WARM_WHITE = 0xFFEDC7;

    // Lens positions and sizes match the block models (pixels / 16, from the centre)

    /** Long narrow beam: full brightness to 32 blocks, dark at 48. */
    public static final LightType SEARCHLIGHT = new LightType("searchlight",
            new ConeFalloff(6, 4, 32, 48, 15), Kind.BEAM, WARM_WHITE, 0, 7.4 / 16, 3 / 16.0);
    /** Wide short cone: 30 degree core, full brightness to 12 blocks, dark at 20. */
    public static final LightType FLOODLIGHT = new LightType("floodlight",
            new ConeFalloff(30, 15, 12, 20, 15), Kind.BEAM, WARM_WHITE, 0, 3.8 / 16, 5.5 / 16);
    /** Vehicle beam: wide and short, full brightness to 12 blocks, dark at 20. */
    public static final LightType HEADLIGHT = new LightType("headlight",
            new ConeFalloff(18, 12, 12, 20, 15), Kind.HEADLIGHT, WARM_WHITE, 0, 4.8 / 16, 3 / 16.0);
    /** Each of the beacon's two beams: medium width, full brightness to 20 blocks, dark at 32. */
    public static final LightType ROTATING_BEACON = new LightType("rotating_beacon",
            new ConeFalloff(5, 4, 20, 32, 15), Kind.BEACON, 0xFFF4DC, 0, 4.8 / 16, 3 / 16.0);
    public static final LightType WARNING_LIGHT = new LightType("warning_light",
            null, Kind.WARNING, 0xFFA21A, 12, 0, 0);
    public static final LightType TAIL_LIGHT = new LightType("tail_light",
            null, Kind.TAIL, 0xFF2418, 6, 0, 0);
    public static final LightType NAVIGATION_LIGHT = new LightType("navigation_light",
            null, Kind.NAVIGATION, 0xFFFFFF, 6, 0, 0);

    public boolean hasBeam() {
        return beam != null;
    }

    /** Lights only while leading on a moving vehicle (Headlight). */
    public boolean followsMotion() {
        return kind == Kind.HEADLIGHT;
    }
}
