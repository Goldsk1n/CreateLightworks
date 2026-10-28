package com.goldskinmc.lightworks.debug;

import com.goldskinmc.lightworks.content.searchlight.SearchlightBlockEntity;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Client-side searchlights currently loaded, for the debug overlay. Weak references, so a block entity
 * that unloads without a removal callback cannot leak.
 */
public final class TrackedLights {
    private static final Set<SearchlightBlockEntity> LIGHTS = Collections.newSetFromMap(new WeakHashMap<>());

    private TrackedLights() {
    }

    public static synchronized void add(SearchlightBlockEntity light) {
        LIGHTS.add(light);
    }

    public static synchronized void remove(SearchlightBlockEntity light) {
        LIGHTS.remove(light);
    }

    /** A copy, safe to iterate while lights load and unload. */
    public static synchronized List<SearchlightBlockEntity> snapshot() {
        return List.copyOf(LIGHTS);
    }
}
