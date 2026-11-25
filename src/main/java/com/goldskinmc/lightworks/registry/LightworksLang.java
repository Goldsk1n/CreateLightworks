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
        add("lightworks.goggles.sensor_signal.label", "Signal:");
        add("lightworks.goggles.sensor_signal", "%s of 15");
        add("lightworks.goggles.sensor_filter.label", "Senses:");
        add("lightworks.goggles.sensor_filter", "%s beams only");
        add("lightworks.goggles.sensor_filter_any.label", "Senses:");
        add("lightworks.goggles.sensor_filter_any", "any beam");
        add("lightworks.goggles.hint.sensor_dye", "Dye: sense only beams of that colour");
        add("item.lightworks.flashlight.colour", "Dyed %s");
        add("lightworks.headlamp.fitted", "Headlamp fitted, %s");
        add("lightworks.headlamp.on", "on");
        add("lightworks.headlamp.off", "off");
        add("lightworks.headlamp.key_hint", "Press %s to switch it on or off");
        add("key.lightworks.headlamp", "Switch headlamp on or off");
        add("key.categories.lightworks", "Create: Lightworks");
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
        add("lightworks.configuration.movingLampLight", "Light from moving lamps");
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
        itemTooltips();
        // Kept short (owner, Nov 25: long ones go unread)
        String placed = "Points _level_, away from you or out of a wall.";
        String dims = "_Dims_; a full signal turns it off.";
        String aim = "When R-click is held on Pan or Tilt";
        String dye = "When R-clicked with Dye";
        tooltip("searchlight", "A long, narrow _beam_, bright to _32 blocks_. Keeps working on contraptions and vehicles.",
                behaviour("When placed", placed),
                behaviour("When powered by Redstone", dims),
                action(aim, "_Aims_ it."),
                action(dye, "Colours the beam."));
        tooltip("floodlight", "A wide _cone_ that lights an area, bright to _12 blocks_.",
                behaviour("When placed", placed),
                behaviour("When powered by Redstone", dims),
                action(aim, "_Aims_ it."),
                action(dye, "Colours the beam."));
        tooltip("headlight", "A short, wide _beam_ for vehicles. On the move it lights only while _leading_.",
                behaviour("When placed", placed),
                behaviour("When powered by Redstone", "_Dims_, and keeps it lit _both ways_ on a vehicle."),
                action(aim, "_Aims_ it."),
                action(dye, "Colours the beam."));
        tooltip("pan_tilt_head", "Turns a _Searchlight_, _Floodlight_ or _Headlight_ placed on top.",
                behaviour("When rotated", "_Pans_ the light with the shaft."),
                behaviour("When powered by Redstone", "_Tilts_ it up 6° per signal level."),
                action("When R-click is held on the box", "Sets a back-and-forth _sweep_."),
                action("When R-click is held on the light's Pan", "Points the head that way."));
        tooltip("rotating_beacon", "Two opposite _beams_ that turn with the shaft.",
                behaviour("When stopped", "Lights where its beams land."),
                behaviour("When powered by Redstone", dims),
                action(dye, "Colours the beams."));
        tooltip("warning_light", "An _amber_ lamp that blinks, pulses, flashes or strobes.",
                behaviour("When powered by Redstone", dims),
                action("When R-click is held on the box", "Picks the _pattern_; the lower row runs _only at night_."),
                action(dye, "Changes its colour."));
        tooltip("tail_light", "A red lamp that _brightens_ when its vehicle slows.",
                behaviour("When powered by Redstone", dims),
                action(dye, "Changes its colour."));
        tooltip("beam_sensor", "Gives a _redstone signal_ while a Lightworks beam hits its lens.",
                behaviour("When lit", "_Stronger_ for brighter beams. Powers its mount like a lever."),
                action(dye, "Senses only that _colour_."));
        tooltip("edge_light", "A low _marker_ for runway and platform edges, cheap by the hundred.",
                behaviour("When powered by Redstone", dims),
                action(dye, "Changes its colour."));
    }

    private record Line(boolean action, String condition, String text) {
    }

    private static void itemTooltips() {
        itemTooltip("flashlight", "A hand lamp with a narrow _beam_, bright to _20 blocks_.",
                action("When R-clicked", "Switches it _on_ or _off_."),
                behaviour("When crafted with a dye", "Colours the beam. _Wash_ it to undo."),
                behaviour("When crafted with goggles or a diving helmet", "Fits it as a _headlamp_. Craft the headgear alone to take it off."));
    }

    private static Line behaviour(String condition, String text) {
        return new Line(false, condition, text);
    }

    private static Line action(String control, String text) {
        return new Line(true, control, text);
    }

    private static void tooltip(String block, String summary, Line... lines) {
        tooltipFor("block." + Lightworks.MOD_ID + "." + block + ".tooltip", summary, lines);
    }

    private static void itemTooltip(String item, String summary, Line... lines) {
        tooltipFor("item." + Lightworks.MOD_ID + "." + item + ".tooltip", summary, lines);
    }

    private static void tooltipFor(String key, String summary, Line... lines) {
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
