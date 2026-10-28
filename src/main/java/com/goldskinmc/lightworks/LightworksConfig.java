package com.goldskinmc.lightworks;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (per world, synced to clients). Editable in game from the Mods list in singleplayer. */
public final class LightworksConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue SERVER_LIGHT_BLOCKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SERVER_LIGHT_BLOCKS = builder
                .comment("Place invisible light blocks where fixed beams land. Works without client mods;",
                        "turn off to see only the client dynamic light (LambDynamicLights).")
                .translation("lightworks.configuration.serverLightBlocks")
                .define("serverLightBlocks", true);
        SPEC = builder.build();
    }

    private LightworksConfig() {
    }

    /** True before the config loads, so game tests and early ticks use the default. */
    public static boolean serverLightBlocks() {
        return !SPEC.isLoaded() || SERVER_LIGHT_BLOCKS.get();
    }

    /** Changes the setting in memory only, without saving the file (self-test). */
    public static void setServerLightBlocks(boolean on) {
        if (SPEC.isLoaded()) {
            SERVER_LIGHT_BLOCKS.set(on);
        }
    }
}
