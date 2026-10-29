package com.goldskinmc.lightworks;

import com.goldskinmc.lightworks.compat.Shaders;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config: how beams look on this computer. */
public final class LightworksClientConfig {
    public enum BeamVisual {
        FULL(1f), FAINT(0.4f), OFF(0f);

        final float alpha;

        BeamVisual(float alpha) {
            this.alpha = alpha;
        }
    }

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<BeamVisual> BEAM_VISUAL;
    private static final ModConfigSpec.EnumValue<BeamVisual> BEAM_VISUAL_WITH_SHADERS;
    private static final ModConfigSpec.BooleanValue COLOURED_LIGHT;
    private static final ModConfigSpec.BooleanValue DEBUG_OVERLAY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        BEAM_VISUAL = builder
                .comment("The visible cone of light in front of a beam: FULL, FAINT or OFF. It never changes light levels.")
                .translation("lightworks.configuration.beamVisual")
                .defineEnum("beamVisual", BeamVisual.FULL);
        BEAM_VISUAL_WITH_SHADERS = builder
                .comment("The same while an Iris shader pack is on; shader packs often add their own glow.")
                .translation("lightworks.configuration.beamVisualWithShaders")
                .defineEnum("beamVisualWithShaders", BeamVisual.FAINT);
        COLOURED_LIGHT = builder
                .comment("Dyed lights and signal lamps tint the blocks they light with their colour. Off while an",
                        "Iris shader pack is on: shader packs light the world their own way.")
                .translation("lightworks.configuration.colouredLight")
                .define("colouredLight", true);
        DEBUG_OVERLAY = builder
                .comment("With the F3 screen open, draw every beam from lens to hit and show details of the light",
                        "under the crosshair.")
                .translation("lightworks.configuration.debugOverlay")
                .define("debugOverlay", false);
        SPEC = builder.build();
    }

    private LightworksClientConfig() {
    }

    /** Opacity factor for the cosmetic beam, 0 when it is switched off. */
    public static float beamAlpha() {
        if (!SPEC.isLoaded()) {
            return BeamVisual.FULL.alpha;
        }
        return (Shaders.inUse() ? BEAM_VISUAL_WITH_SHADERS : BEAM_VISUAL).get().alpha;
    }

    /** Whether dyed lights and signal lamps tint the world (never under a shader pack). */
    public static boolean colouredLight() {
        return (!SPEC.isLoaded() || COLOURED_LIGHT.get()) && !Shaders.inUse();
    }

    public static boolean debugOverlay() {
        return SPEC.isLoaded() && DEBUG_OVERLAY.get();
    }
}
