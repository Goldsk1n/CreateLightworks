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
        add("lightworks.goggles.signal.label", "Redstone signal:");
        add("lightworks.goggles.signal", "%s");
        add("lightworks.goggles.aim.label", "Aim:");
        add("lightworks.goggles.aim", "%s° up, %s° round");
        add("lightworks.goggles.aim_head.label", "Aim:");
        add("lightworks.goggles.aim_head", "%s° up, %s° round, by the Pan-Tilt Head");
        add("lightworks.goggles.beam_hit.label", "Beam:");
        add("lightworks.goggles.beam_hit", "lands on %s %s blocks away, light %s");
        add("lightworks.goggles.beam_miss.label", "Beam:");
        add("lightworks.goggles.beam_miss", "reaches %s blocks without landing");
        add("lightworks.goggles.moving_dynamic.label", "Moving:");
        add("lightworks.goggles.moving_dynamic", "lit by LambDynamicLights");
        add("lightworks.goggles.moving_beam_only.label", "Moving:");
        add("lightworks.goggles.moving_beam_only", "beam only; LambDynamicLights lights the ground");
        add("lightworks.goggles.no_light_path.label", "Light:");
        add("lightworks.goggles.no_light_path", "none: server light blocks are off");
        add("lightworks.goggles.colour.label", "Colour:");
        add("lightworks.goggles.colour", "%s");
        add("lightworks.goggles.colour.default", "default");
        add("lightworks.goggles.headlight_auto.label", "Mode:");
        add("lightworks.goggles.headlight_auto", "automatic, lit while leading");
        add("lightworks.goggles.headlight_manual.label", "Mode:");
        add("lightworks.goggles.headlight_manual", "redstone override, lit both ways");
        add("lightworks.goggles.pan_tilt.label", "Pan and tilt:");
        add("lightworks.goggles.pan_tilt", "%s° round, %s° up");
        add("lightworks.goggles.tilt_signal.label", "Redstone signal:");
        add("lightworks.goggles.tilt_signal", "%s, tilts to %s°");
        add("lightworks.goggles.beacon_turning.label", "Rotor:");
        add("lightworks.goggles.beacon_turning", "turning, beams sweep round");
        add("lightworks.goggles.beacon_stopped.label", "Rotor:");
        add("lightworks.goggles.beacon_stopped", "stopped, lights where the beams land");
        add("lightworks.goggles.block_light.label", "Block light:");
        add("lightworks.goggles.block_light", "%s");
        add("lightworks.goggles.pattern.label", "Pattern:");
        add("lightworks.goggles.pattern", "%s");
        add("lightworks.goggles.brake.label", "Brake light:");
        add("lightworks.goggles.brake", "brightens for a second when the vehicle slows");
        add("lightworks.goggles.brake_now.label", "Brake light:");
        add("lightworks.goggles.brake_now", "on, the vehicle is slowing");
        add("lightworks.goggles.side.label", "Side:");
        add("lightworks.goggles.side", "%s");
        add("lightworks.navigation.port", "port (red)");
        add("lightworks.navigation.starboard", "starboard (green)");
        add("lightworks.navigation.stern", "stern (white)");
        add("lightworks.goggles.hint.aim", "Wrench: aim where you look");
        add("lightworks.goggles.hint.on_head", "Aimed by the Pan-Tilt Head below");
        add("lightworks.goggles.hint.dye", "Dye: colour the lens, beam and light");
        add("lightworks.goggles.hint.redstone", "Redstone dims it; 15 turns it off");
        add("lightworks.goggles.hint.redstone_inverted", "Redstone lights it; brighter the stronger");
        add("lightworks.goggles.hint.pan_tilt", "Shaft pans the light on top; redstone tilts it 6° a level");
        add("lightworks.goggles.hint.beacon", "Shaft speed sets the sweep");
        add("lightworks.goggles.hint.pattern", "Scroll the value box to change the pattern");
        add("lightworks.goggles.hint.navigation", "Wrench: change the side colour");
        add("lightworks.warning_light.pattern", "Pattern");
        add("create.lightworks.warning_pattern.steady", "Steady");
        add("create.lightworks.warning_pattern.blink", "Blink");
        add("create.lightworks.warning_pattern.pulse", "Pulse");
        add("create.lightworks.warning_pattern.flash", "Flash");
        add("lightworks.configuration.invertRedstone", "Invert redstone");
        add("lightworks.configuration.maxBeamRange", "Longest beam");
        add("lightworks.configuration.beamVisual", "Visible beam");
        add("lightworks.configuration.beamVisualWithShaders", "Visible beam with shaders");
        add("lightworks.configuration.colouredLight", "Coloured light");
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
                behaviour("When placed", "Aims where you are looking. Mount it on a _Pan-Tilt Head_ to sweep it round."),
                behaviour("When powered by Redstone", "Dims as the signal rises; a _full signal_ turns it off."),
                action("When R-clicked with a Wrench", "Aims it where you are looking."),
                action("When R-clicked with Dye", "Colours the lens, the beam and the light it casts."));
        tooltip("floodlight", "A wide _cone_ for lighting an area: bright out to _12 blocks_, dark by 20. Mount it high and aim it down.",
                behaviour("When placed", "Aims where you are looking, away from the face it is mounted on."),
                behaviour("When powered by Redstone", "Dims as the signal rises; a _full signal_ turns it off."),
                action("When R-clicked with a Wrench", "Aims it where you are looking."),
                action("When R-clicked with Dye", "Colours the lens, the beam and the light it casts."));
        tooltip("headlight", "A forward _beam_ for vehicles. On a moving contraption, train or Sable vehicle it lights only while _leading_, so Headlights at both ends swap over when it reverses.",
                behaviour("When powered at assembly", "Stays lit _both ways_, dimmed by the signal."),
                behaviour("When placed", "Always lit: a Searchlight with a shorter, wider beam."),
                action("When R-clicked with a Wrench", "Aims it where you are looking."),
                action("When R-clicked with Dye", "Colours the lens, the beam and the light it casts."));
        tooltip("pan_tilt_head", "A kinetic mount for a _Searchlight_ or _Floodlight_ placed on top of it.",
                behaviour("When rotated", "_Pans_ the light round, as fast as the shaft turns."),
                behaviour("When powered by Redstone", "_Tilts_ the light up 6° per signal level, straight up at 15."));
        tooltip("rotating_beacon", "Two opposite _beams_ that sweep round with the shaft: a rotating warning beacon for towers, airships and trains.",
                behaviour("When rotated", "The beams turn _as fast as the shaft_."),
                behaviour("When stopped", "Lights where the beams land, like two fixed Searchlights."),
                behaviour("When powered by Redstone", "Dims as the signal rises; a _full signal_ turns it off."),
                action("When R-clicked with Dye", "Colours the lens, the beams and the light they cast."));
        tooltip("warning_light", "An _amber_ lamp that shines steady, blinks, pulses or flashes, and lights the world round it in the same rhythm.",
                behaviour("When powered by Redstone", "Dims as the signal rises; a _full signal_ turns it off."),
                action("When scrolling the value panel", "Picks the _pattern_."),
                action("When R-clicked with Dye", "Changes its colour."));
        tooltip("tail_light", "A red lamp for the back of a vehicle. On a moving contraption, train or Sable vehicle it _brightens for a second_ whenever the vehicle slows down.",
                behaviour("When placed", "Glows dim red and gives a little block light."),
                behaviour("When powered by Redstone", "Dims as the signal rises; a _full signal_ turns it off."),
                action("When R-clicked with Dye", "Changes its colour."));
        tooltip("navigation_light", "A _red_, _green_ or _white_ marker for a vehicle's port side, starboard side and stern.",
                behaviour("When placed", "Facing your left it turns _red_, facing your right _green_, otherwise _white_."),
                behaviour("When powered by Redstone", "Dims as the signal rises; a _full signal_ turns it off."),
                action("When R-clicked with a Wrench", "Cycles red, green and white."));
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
