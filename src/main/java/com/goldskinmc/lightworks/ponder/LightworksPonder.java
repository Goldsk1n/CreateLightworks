package com.goldskinmc.lightworks.ponder;

import com.goldskinmc.lightworks.Lightworks;
import java.util.function.BiConsumer;
import net.createmod.ponder.foundation.PonderIndex;

/**
 * Hooks our Ponder plugin in: on the client at setup, and at datagen to collect the scene text (client
 * setup does not run there). Client and datagen only.
 */
public final class LightworksPonder {
    private static boolean registered;

    private LightworksPonder() {
    }

    public static void registerPlugin() {
        if (!registered) {
            registered = true;
            PonderIndex.addPlugin(new LightworksPonderPlugin());
        }
    }

    /** English scene titles, text, shared text and tag names, for the lang provider. */
    public static void provideLang(BiConsumer<String, String> consumer) {
        registerPlugin();
        PonderIndex.getLangAccess().provideLang(Lightworks.MOD_ID, consumer);
    }
}
