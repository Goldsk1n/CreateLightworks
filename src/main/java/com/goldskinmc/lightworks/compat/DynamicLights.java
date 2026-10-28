package com.goldskinmc.lightworks.compat;

import com.goldskinmc.lightworks.compat.ldl.LdlSpots;
import net.neoforged.fml.ModList;

/**
 * Guard for the client dynamic-light path. Only touches {@code compat.ldl} when LambDynamicLights is
 * loaded, so its classes never load without it.
 */
public final class DynamicLights {
    /** Present in both the release jar (nested in "lambdynlights") and the dev runtime. */
    private static final String LDL_RUNTIME_MOD_ID = "lambdynlights_runtime";

    private DynamicLights() {
    }

    public static boolean present() {
        return ModList.get().isLoaded(LDL_RUNTIME_MOD_ID);
    }

    /** A new dynamic spot, or {@link DynamicSpot#NONE} when no dynamic-light mod can take it. */
    public static DynamicSpot createSpot() {
        return present() ? LdlSpots.create() : DynamicSpot.NONE;
    }

    /** Switches every dynamic spot dark (or back on) without removing it: for comparisons in testing. */
    public static void setSuppressed(boolean suppressed) {
        if (present()) {
            LdlSpots.setSuppressed(suppressed);
        }
    }

    /** One-line status for the debug overlay and log. */
    public static String status() {
        return present() ? LdlSpots.status() : "LambDynamicLights not installed";
    }
}
