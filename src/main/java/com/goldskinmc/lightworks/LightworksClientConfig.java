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
    private static final ModConfigSpec.BooleanValue BEAMS_AFTER_SHADER_PACK;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SHADER_PACKS_WITHOUT_BEAMS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SHADER_PACKS_WITH_PARTICLE_BEAMS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SHADER_PACKS_WITH_BEACON_BEAMS;
    private static final ModConfigSpec.BooleanValue COLOURED_LIGHT;
    private static final ModConfigSpec.BooleanValue LENS_GLOW;
    private static final ModConfigSpec.BooleanValue INSIDE_HAZE;
    private static final ModConfigSpec.BooleanValue BOARDS_ONLY_WITH_WRENCH;
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
                .comment("The same while an Iris shader pack draws the beams itself (beamsAfterShaderPack off).")
                .translation("lightworks.configuration.beamVisualWithShaders")
                .defineEnum("beamVisualWithShaders", BeamVisual.FAINT);
        BEAMS_AFTER_SHADER_PACK = builder
                .comment("Under an Iris shader pack, draw visible beams, lens glows and coloured light after the pack has",
                        "finished the picture, as without a pack: they look alike in every pack and keep their colour.",
                        "Turn it off if beams show through walls with your pack; the lists below then pick how the pack draws them.")
                .translation("lightworks.configuration.beamsAfterShaderPack")
                .define("beamsAfterShaderPack", true);
        SHADER_PACKS_WITH_PARTICLE_BEAMS = builder
                .comment("Shader packs that draw the usual visible beam wrong but draw it well the way they draw particles:",
                        "under a pack whose name contains one of these, the beam and lens glow are drawn that way.",
                        "Checked Nov 2026: Bliss drew the usual beam black, Photon not at all.")
                .translation("lightworks.configuration.shaderPacksWithParticleBeams")
                .defineListAllowEmpty("shaderPacksWithParticleBeams", List.of("bliss", "photon"), () -> "",
                        value -> value instanceof String);
        SHADER_PACKS_WITH_BEACON_BEAMS = builder
                .comment("Shader packs that draw the usual visible beam wrong but draw it well the way they draw beacon beams:",
                        "under a pack whose name contains one of these, the beam and lens glow are drawn that way.",
                        "Checked Nov 2026: Solas drew the usual beam nearly invisible.")
                .translation("lightworks.configuration.shaderPacksWithBeaconBeams")
                .defineListAllowEmpty("shaderPacksWithBeaconBeams", List.of("solas"), () -> "",
                        value -> value instanceof String);
        SHADER_PACKS_WITHOUT_BEAMS = builder
                .comment("Shader packs that draw the visible beam wrong every way: under a pack whose name contains one of",
                        "these, the beam and lens glow are left out. Lights still light the world.")
                .translation("lightworks.configuration.shaderPacksWithoutBeams")
                .defineListAllowEmpty("shaderPacksWithoutBeams", List.of(), () -> "",
                        value -> value instanceof String);
        COLOURED_LIGHT = builder
                .comment("Dyed lights and signal lamps tint the blocks they light with their colour. Works under Iris",
                        "shader packs too, drawn after the pack (beamsAfterShaderPack); with that off it is left out under a pack.")
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
        BOARDS_ONLY_WITH_WRENCH = builder
                .comment("Show the Pan, Tilt, sweep and pattern boards, and let them take clicks, only while you hold a",
                        "Wrench (or a Clipboard), as some of Create's own boards do. Off: they show whenever you look at the light.")
                .translation("lightworks.configuration.boardsOnlyWithWrench")
                .define("boardsOnlyWithWrench", false);
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
        // Drawn after the pack's final picture, the beam is drawn as without a pack
        boolean shaders = Shaders.inUse() && !com.goldskinmc.lightworks.light.LateBeams.active();
        if (shaders && packDrawsBeamsWrong()) {
            return 0;
        }
        return (shaders ? BEAM_VISUAL_WITH_SHADERS : BEAM_VISUAL).get().alpha;
    }

    /** True while the selected shader pack is one of {@code shaderPacksWithoutBeams}. */
    public static boolean packDrawsBeamsWrong() {
        return SPEC.isLoaded() && packListed(SHADER_PACKS_WITHOUT_BEAMS.get());
    }

    /** True while the selected shader pack is one of {@code shaderPacksWithParticleBeams}. */
    public static boolean packWantsParticleBeams() {
        return SPEC.isLoaded() && packListed(SHADER_PACKS_WITH_PARTICLE_BEAMS.get());
    }

    /** True while the selected shader pack is one of {@code shaderPacksWithBeaconBeams}. */
    public static boolean packWantsBeaconBeams() {
        return SPEC.isLoaded() && packListed(SHADER_PACKS_WITH_BEACON_BEAMS.get());
    }

    private static boolean packListed(List<? extends String> names) {
        String pack = Shaders.packName().toLowerCase(Locale.ROOT);
        if (pack.isEmpty()) {
            return false;
        }
        for (String name : names) {
            if (!name.isBlank() && pack.contains(name.toLowerCase(Locale.ROOT).trim())) {
                return true;
            }
        }
        return false;
    }

    public static boolean beamsAfterShaderPack() {
        return !SPEC.isLoaded() || BEAMS_AFTER_SHADER_PACK.get();
    }

    /** Whether dyed lights and signal lamps tint the world (under a shader pack, only drawn after it). */
    public static boolean colouredLight() {
        return (!SPEC.isLoaded() || COLOURED_LIGHT.get())
                && (!Shaders.inUse() || com.goldskinmc.lightworks.light.LateBeams.active());
    }

    public static boolean lensGlow() {
        return (!SPEC.isLoaded() || LENS_GLOW.get()) && !(Shaders.inUse() && packDrawsBeamsWrong());
    }

    public static boolean boardsOnlyWithWrench() {
        return SPEC.isLoaded() && BOARDS_ONLY_WITH_WRENCH.get();
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
