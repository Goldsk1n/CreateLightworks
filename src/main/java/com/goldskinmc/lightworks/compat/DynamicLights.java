package com.goldskinmc.lightworks.compat;

import com.goldskinmc.lightworks.compat.ldl.LdlSpots;
import com.goldskinmc.lightworks.compat.sdl.SdlSpots;
import net.neoforged.fml.ModList;

/**
 * Guard for the client dynamic-light path. Only touches {@code compat.ldl} when LambDynamicLights is
 * loaded and {@code compat.sdl} when Sodium Dynamic Lights is, so their classes never load without them.
 * The two refuse each other, so one at most is here.
 */
public final class DynamicLights {
    /** Present in both the release jar (nested in "lambdynlights") and the dev runtime. */
    private static final String LDL_RUNTIME_MOD_ID = "lambdynlights_runtime";
    private static final String SDL_MOD_ID = "sodiumdynamiclights";

    private DynamicLights() {
    }

    /** True when a dynamic-light mod can take spots. */
    public static boolean present() {
        return ldl() || (sdl() && SdlSpots.enabled());
    }

    private static boolean ldl() {
        return ModList.get().isLoaded(LDL_RUNTIME_MOD_ID);
    }

    private static boolean sdl() {
        return ModList.get().isLoaded(SDL_MOD_ID);
    }

    /** A new dynamic spot, or {@link DynamicSpot#NONE} when no dynamic-light mod can take it. */
    public static DynamicSpot createSpot() {
        if (ldl()) {
            return LdlSpots.create();
        }
        return sdl() ? SdlSpots.create() : DynamicSpot.NONE;
    }

    /** Switches every dynamic spot dark (or back on) without removing it: for comparisons in testing. */
    public static void setSuppressed(boolean suppressed) {
        if (ldl()) {
            LdlSpots.setSuppressed(suppressed);
        } else if (sdl()) {
            SdlSpots.setSuppressed(suppressed);
        }
    }

    /** One-line status for the debug overlay and log. */
    public static String status() {
        if (ldl()) {
            return LdlSpots.status();
        }
        return sdl() ? SdlSpots.status() : "No dynamic light mod installed";
    }
}
