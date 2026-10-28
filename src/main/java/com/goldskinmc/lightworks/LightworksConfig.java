package com.goldskinmc.lightworks;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config (per world, synced to clients). Editable in game from the Mods list in singleplayer.
 * Before the config loads (game tests, early ticks) every value is its default, or what a test set.
 */
public final class LightworksConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue SERVER_LIGHT_BLOCKS;
    private static final ModConfigSpec.BooleanValue INVERT_REDSTONE;
    private static final ModConfigSpec.IntValue MAX_BEAM_RANGE;

    // Used while the config is not loaded, so game tests can change settings too
    private static boolean serverLightBlocksUnloaded = true;
    private static boolean invertRedstoneUnloaded = false;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SERVER_LIGHT_BLOCKS = builder
                .comment("Place invisible light blocks where fixed beams land. Works without client mods;",
                        "turn off to see only the client dynamic light (LambDynamicLights).")
                .translation("lightworks.configuration.serverLightBlocks")
                .define("serverLightBlocks", true);
        INVERT_REDSTONE = builder
                .comment("Lights are off unless powered, and brighter the stronger the signal.",
                        "Off (default): lights are on unless powered, and a signal dims them; 15 turns them off.")
                .translation("lightworks.configuration.invertRedstone")
                .define("invertRedstone", false);
        MAX_BEAM_RANGE = builder
                .comment("Longest beam in blocks. Beams end at their own range or here, whichever is shorter.")
                .translation("lightworks.configuration.maxBeamRange")
                .defineInRange("maxBeamRange", 48, 8, 64);
        SPEC = builder.build();
    }

    private LightworksConfig() {
    }

    public static boolean serverLightBlocks() {
        return SPEC.isLoaded() ? SERVER_LIGHT_BLOCKS.get() : serverLightBlocksUnloaded;
    }

    /** Changes the setting in memory only, without saving the file (self-test). */
    public static void setServerLightBlocks(boolean on) {
        if (SPEC.isLoaded()) {
            SERVER_LIGHT_BLOCKS.set(on);
        } else {
            serverLightBlocksUnloaded = on;
        }
    }

    public static boolean invertRedstone() {
        return SPEC.isLoaded() ? INVERT_REDSTONE.get() : invertRedstoneUnloaded;
    }

    /** Changes the setting in memory only (game tests). */
    public static void setInvertRedstone(boolean on) {
        if (SPEC.isLoaded()) {
            INVERT_REDSTONE.set(on);
        } else {
            invertRedstoneUnloaded = on;
        }
    }

    public static int maxBeamRange() {
        return SPEC.isLoaded() ? MAX_BEAM_RANGE.get() : 48;
    }

    /** Brightness 0-15 a light shows for the strongest redstone signal around it. */
    public static int brightnessFor(int signal) {
        return invertRedstone() ? signal : 15 - signal;
    }
}
