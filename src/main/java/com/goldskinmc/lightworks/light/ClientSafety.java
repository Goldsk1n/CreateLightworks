package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import java.util.HashSet;
import java.util.Set;

/**
 * What to do when our drawing goes wrong next to some other mod: a lamp effect that fails turns itself off for the
 * session and says so once in the log, rather than taking the player's game down. The light blocks and the rest of the
 * game carry on. Render thread only.
 */
final class ClientSafety {
    /** The colour and spot layers. */
    static final String LAYERS = "coloured light and spots";
    /** The visible beams. */
    static final String BEAMS = "visible beams";
    /** The dynamic light that follows moving beams. */
    static final String DYNAMIC_LIGHT = "dynamic light";

    private static final Set<String> FAILED = new HashSet<>();

    private ClientSafety() {
    }

    static boolean works(String effect) {
        return !FAILED.contains(effect);
    }

    static void failed(String effect, RuntimeException cause) {
        if (FAILED.add(effect)) {
            Lightworks.LOGGER.error("Lightworks turned off its {} until the game restarts: they failed, likely next to another mod. "
                    + "Please report this with your mod list.", effect, cause);
        }
    }
}
