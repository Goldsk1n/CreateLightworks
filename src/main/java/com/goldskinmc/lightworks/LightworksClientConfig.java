package com.goldskinmc.lightworks;

import com.goldskinmc.lightworks.compat.Shaders;
import java.util.List;
import java.util.Locale;
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
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SHADER_PACKS_WITHOUT_BEAMS;
    private static final ModConfigSpec.BooleanValue COLOURED_LIGHT;
    private static final ModConfigSpec.BooleanValue LENS_GLOW;
    private static final ModConfigSpec.BooleanValue INSIDE_HAZE;
    private static final ModConfigSpec.BooleanValue SHADER_HINT;
    private static final ModConfigSpec.BooleanValue CLIENT_LIGHT_BLOCKS;
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
        SHADER_PACKS_WITHOUT_BEAMS = builder
                .comment("Shader packs that draw the visible beam and lens glow wrong (dark, see-through or not at all): under a",
                        "pack whose name contains one of these, they are left out. Lights still light the world.",
                        "Checked Nov 2026: Bliss draws beams black, Solas nearly invisible, Photon not at all.")
                .translation("lightworks.configuration.shaderPacksWithoutBeams")
                .defineListAllowEmpty("shaderPacksWithoutBeams", List.of("bliss", "solas", "photon"), () -> "",
                        value -> value instanceof String);
        COLOURED_LIGHT = builder
                .comment("Dyed lights and signal lamps tint the blocks they light with their colour. Off while an",
                        "Iris shader pack is on: shader packs light the world their own way.")
                .translation("lightworks.configuration.colouredLight")
                .define("colouredLight", true);
        LENS_GLOW = builder
                .comment("A soft glow round a lamp's lens while you look into its beam. Never strong enough to hide anything.")
                .translation("lightworks.configuration.lensGlow")
                .define("lensGlow", true);
        INSIDE_HAZE = builder
                .comment("A faint haze over the view while you stand inside a beam, stronger looking towards its lamp.")
                .translation("lightworks.configuration.insideHaze")
                .define("insideHaze", true);
        SHADER_HINT = builder
                .comment("Once a session, a chat tip when a light on a vehicle, a contraption or a turning head shines under a",
                        "shader pack with no dynamic lights mod installed: without one it lights nothing there.")
                .translation("lightworks.configuration.shaderHint")
                .define("shaderHint", true);
        CLIENT_LIGHT_BLOCKS = builder
                .comment("With no dynamic lights mod, lights on vehicles, contraptions and turning heads light the world with",
                        "light blocks placed in your own world only, which shader packs light up too.")
                .translation("lightworks.configuration.clientLightBlocks")
                .define("clientLightBlocks", true);
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
        if (Shaders.inUse() && packDrawsBeamsWrong()) {
            return 0;
        }
        return (Shaders.inUse() ? BEAM_VISUAL_WITH_SHADERS : BEAM_VISUAL).get().alpha;
    }

    /** True while the selected shader pack is one of {@code shaderPacksWithoutBeams}. */
    public static boolean packDrawsBeamsWrong() {
        if (!SPEC.isLoaded()) {
            return false;
        }
        String pack = Shaders.packName().toLowerCase(Locale.ROOT);
        if (pack.isEmpty()) {
            return false;
        }
        for (String name : SHADER_PACKS_WITHOUT_BEAMS.get()) {
            if (!name.isBlank() && pack.contains(name.toLowerCase(Locale.ROOT).trim())) {
                return true;
            }
        }
        return false;
    }

    /** Whether dyed lights and signal lamps tint the world (never under a shader pack). */
    public static boolean colouredLight() {
        return (!SPEC.isLoaded() || COLOURED_LIGHT.get()) && !Shaders.inUse();
    }

    public static boolean lensGlow() {
        return (!SPEC.isLoaded() || LENS_GLOW.get()) && !(Shaders.inUse() && packDrawsBeamsWrong());
    }

    public static boolean insideHaze() {
        return !SPEC.isLoaded() || INSIDE_HAZE.get();
    }

    public static boolean clientLightBlocks() {
        return !SPEC.isLoaded() || CLIENT_LIGHT_BLOCKS.get();
    }

    public static boolean shaderHint() {
        return !SPEC.isLoaded() || SHADER_HINT.get();
    }

    public static boolean debugOverlay() {
        return SPEC.isLoaded() && DEBUG_OVERLAY.get();
    }
}
