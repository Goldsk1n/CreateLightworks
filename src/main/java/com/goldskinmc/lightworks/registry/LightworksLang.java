package com.goldskinmc.lightworks.registry;

import static com.goldskinmc.lightworks.Lightworks.REGISTRATE;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.ponder.LightworksPonder;
import com.tterrag.registrate.providers.ProviderType;

/**
 * English text that is not a block name: item tooltips (Create's "Hold Shift" summary), goggle lines,
 * config titles, value-box options and the creative tab. Ponder scene text is written by the scenes
 * themselves and collected here at datagen.
 */
public final class LightworksLang {
    private LightworksLang() {
    }

    public static void register() {
        add("itemGroup.lightworks", "Create: Lightworks");
        tooltips();
        add("lightworks.configuration.serverLightBlocks", "Server light blocks");
        add("lightworks.goggles.brightness.label", "Brightness:");
        add("lightworks.goggles.brightness", "%s of 15");
        add("lightworks.goggles.off_powered.label", "Off:");
        add("lightworks.goggles.off_powered", "full redstone signal");
        add("lightworks.goggles.off_unpowered.label", "Off:");
        add("lightworks.goggles.off_unpowered", "no redstone signal (inverted redstone)");
        add("lightworks.aim.pan", "Pan");
        add("lightworks.aim.tilt", "Tilt");
        add("lightworks.goggles.moving_beam_only.label", "Moving:");
        add("lightworks.goggles.moving_beam_only", "beam only; LambDynamicLights lights the ground");
        add("lightworks.goggles.no_light_path.label", "Light:");
        add("lightworks.goggles.no_light_path", "none: server light blocks are off");
        add("lightworks.goggles.headlight_auto.label", "Mode:");
        add("lightworks.goggles.headlight_auto", "automatic, lit while leading");
        add("lightworks.goggles.headlight_manual.label", "Mode:");
        add("lightworks.goggles.headlight_manual", "redstone override, lit both ways");
        add("lightworks.goggles.pan_tilt.label", "Pan and tilt:");
        add("lightworks.goggles.pan_tilt", "%s° round, %s° up");
        add("lightworks.goggles.tilt_signal.label", "Redstone signal:");
        add("lightworks.goggles.tilt_signal", "%s, tilts to %s°");
        add("lightworks.goggles.sweep.label", "Sweep:");
        add("lightworks.goggles.sweep", "%s");
        add("lightworks.goggles.pattern.label", "Pattern:");
        add("lightworks.goggles.pattern", "%s");
        add("lightworks.goggles.hint.aim", "Hold R-click on Pan or Tilt to aim");
        add("lightworks.goggles.hint.on_head", "Hold R-click on Pan to turn the head below; it sweeps round that");
        add("lightworks.goggles.hint.dye", "Dye: colour the lens, beam and light");
        add("lightworks.clipboard.needs_dye", "Needs %s to paste the colour");
        add("lightworks.goggles.hint.clipboard", "Clipboard: R-click copies its settings, L-click pastes them");
        add("lightworks.goggles.hint.redstone", "Redstone dims it; 15 turns it off");
        add("lightworks.goggles.hint.redstone_inverted", "Redstone lights it; brighter the stronger");
        add("lightworks.goggles.hint.pan_tilt", "Shaft pans the light on top; redstone tilts it 6° a level");
        add("lightworks.goggles.hint.sweep", "Hold R-click on the box to set how far it sweeps back and forth");
        add("lightworks.pan_tilt_head.sweep", "Sweep");
        add("lightworks.pan_tilt_head.arc_row", "Arc");
        add("lightworks.pan_tilt_head.sweep_off", "Off");
        add("lightworks.pan_tilt_head.arc", "±%s°");
        add("lightworks.goggles.hint.beacon", "Shaft speed sets the sweep");
        add("lightworks.goggles.hint.pattern", "Hold R-click on the box to pick the pattern, and always or night only");
        add("lightworks.warning_light.pattern", "Pattern");
        add("lightworks.warning_light.always", "Always: %s");
        add("lightworks.warning_light.night", "Night only: %s");
        // Create 6.0.0 looks option names up with a "create." prefix, 6.0.10 without: both must exist
        for (String prefix : new String[] {"", "create."}) {
            add(prefix + "lightworks.warning_pattern.steady", "Steady");
            add(prefix + "lightworks.warning_pattern.blink", "Blink");
            add(prefix + "lightworks.warning_pattern.opposite_blink", "Opposite Blink");
            add(prefix + "lightworks.warning_pattern.pulse", "Pulse");
            add(prefix + "lightworks.warning_pattern.double_flash", "Double Flash");
            add(prefix + "lightworks.warning_pattern.strobe", "Strobe");
        }
        add("lightworks.configuration.invertRedstone", "Invert redstone");
        add("lightworks.configuration.maxBeamRange", "Longest beam");
        add("lightworks.configuration.beamVisual", "Visible beam");
        add("lightworks.configuration.beamVisualWithShaders", "Visible beam with shaders");
        add("lightworks.configuration.shaderPacksWithoutBeams", "Shader packs without visible beams");
        add("lightworks.configuration.beamsAfterShaderPack", "Beams after the shader pack");
        add("lightworks.configuration.shaderPacksWithParticleBeams", "Shader packs with particle-style beams");
        add("lightworks.configuration.shaderPacksWithBeaconBeams", "Shader packs with beacon-style beams");
        add("lightworks.configuration.colouredLight", "Coloured light");
        add("lightworks.configuration.lensGlow", "Lens glow");
        add("lightworks.configuration.insideHaze", "Haze inside a beam");
        add("lightworks.configuration.boardsOnlyWithWrench", "Boards only with a Wrench");
        add("lightworks.configuration.shaderHint", "Shader tip");
        add("lightworks.configuration.clientLightBlocks", "Client light blocks");
        add("lightworks.hint.moving_lights_shaders", "Lightworks: under a shader pack, lights on vehicles, contraptions and "
                + "turning heads light the world only with LambDynamicLights or Sodium Dynamic Lights installed. This tip "
                + "shows once; turn it off in Lightworks' client config.");
        add("lightworks.configuration.debugOverlay", "Debug overlay (F3)");
        // Scene text lives in the scenes; Ponder hands it over once every scene is built
        REGISTRATE.addDataGenerator(ProviderType.LANG, provider -> LightworksPonder.provideLang(provider::add));
    }

    /**
     * Keys read by Create's {@code ItemDescription}: {@code <block key>.tooltip.summary}, then numbered
     * condition and behaviour pairs, then numbered control and action pairs. Words in _underscores_ are
     * highlighted.
     */
    private static void tooltips() {
        tooltip("searchlight", "A long, narrow _beam_ that lights the block it points at: full out to _32 blocks_, dark by 48. Keeps landing where it points on contraptions, trains and Sable vehicles.",
                behaviour("When placed", "Points _level_, away from you. Mount it on a _Pan-Tilt Head_ to sweep it round."),
                behaviour("When powered by Redstone", "Dims as the signal rises; a _full signal_ turns it off."),
                action("When R-click is held on Pan or Tilt", "Turns it round, or tilts it up and down."),
                action("When R-clicked with Dye", "Colours the lens, the beam and the light it casts."));
        tooltip("floodlight", "A wide _cone_ for lighting an area: bright out to _12 blocks_, dark by 20. Mount it high and aim it down.",
                behaviour("When placed", "Points _level_: away from you on a floor or ceiling, straight out of a wall."),
                behaviour("When powered by Redstone", "Dims as the signal rises; a _full signal_ turns it off."),
                action("When R-click is held on Pan or Tilt", "Turns it round, or tilts it up and down."),
                action("When R-clicked with Dye", "Colours the lens, the beam and the light it casts."));
        tooltip("headlight", "A forward _beam_ for vehicles. On a moving contraption, train or Sable vehicle it lights only while _leading_, so Headlights at both ends swap over when it reverses.",
                behaviour("When powered at assembly", "Stays lit _both ways_, dimmed by the signal."),
                behaviour("When placed", "Always lit: a Searchlight with a shorter, wider beam, pointing _level_."),
                action("When R-click is held on Pan or Tilt", "Turns it round, or tilts it up and down."),
                action("When R-clicked with Dye", "Colours the lens, the beam and the light it casts."));
        tooltip("pan_tilt_head", "A kinetic mount for a _Searchlight_, _Floodlight_ or _Headlight_ placed on top of it.",
                behaviour("When rotated", "_Pans_ the light round, as fast as the shaft turns."),
                behaviour("When powered by Redstone", "_Tilts_ the light up 6° per signal level, straight up at 15."),
                action("When R-click is held on the box", "Makes it _sweep_ back and forth, this far either way of where it points now."),
                action("When R-click is held on the light's Pan", "Turns it to face that way, where it stays; a sweep centres there."));
        tooltip("rotating_beacon", "Two opposite _beams_ that sweep round with the shaft: a rotating warning beacon for towers, airships and trains.",
                behaviour("When rotated", "The beams turn _as fast as the shaft_."),
                behaviour("When stopped", "Lights where the beams land, like two fixed Searchlights."),
                behaviour("When powered by Redstone", "Dims as the signal rises; a _full signal_ turns it off."),
                action("When R-clicked with Dye", "Colours the lens, the beams and the light they cast."));
        tooltip("warning_light", "An _amber_ lamp that shines steady, blinks, pulses, flashes or strobes, always or only at night, and lights the world round it in the same rhythm.",
                behaviour("When powered by Redstone", "Dims as the signal rises; a _full signal_ turns it off."),
                action("When R-click is held on the box", "Picks the _pattern_, and the lower row runs it _only at night_."),
                action("When R-clicked with Dye", "Changes its colour."));
        tooltip("tail_light", "A red lamp for the back of a vehicle. On a moving contraption, train or Sable vehicle it _brightens for a second_ whenever the vehicle slows down.",
                behaviour("When placed", "Glows dim red and gives a little block light."),
                behaviour("When powered by Redstone", "Dims as the signal rises; a _full signal_ turns it off."),
                action("When R-clicked with Dye", "Changes its colour."));
        tooltip("edge_light", "A low _marker_ for runway, taxiway and platform edges, bright enough to read from the air. Vehicles and players pass over it, and hundreds cost next to nothing.",
                behaviour("When placed", "Glows _white_ and gives a little block light."),
                behaviour("When powered by Redstone", "Dims as the signal rises; a _full signal_ turns it off."),
                action("When R-clicked with Dye", "Changes its colour. It does not colour the blocks round it."));
    }

    private record Line(boolean action, String condition, String text) {
    }

    private static Line behaviour(String condition, String text) {
        return new Line(false, condition, text);
    }

    private static Line action(String control, String text) {
        return new Line(true, control, text);
    }

    private static void tooltip(String block, String summary, Line... lines) {
        String key = "block." + Lightworks.MOD_ID + "." + block + ".tooltip";
        add(key + ".summary", summary);
        int behaviours = 0;
        int actions = 0;
        for (Line line : lines) {
            if (line.action()) {
                actions++;
                add(key + ".control" + actions, line.condition());
                add(key + ".action" + actions, line.text());
            } else {
                behaviours++;
                add(key + ".condition" + behaviours, line.condition());
                add(key + ".behaviour" + behaviours, line.text());
            }
        }
    }

    private static void add(String key, String text) {
        REGISTRATE.addRawLang(key, text);
    }
}
