package com.goldskinmc.lightworks.debug;

import java.util.concurrent.atomic.LongAdder;

/**
 * Wall-clock time spent in our own code, for performance checks. Thread-safe:
 * LambDynamicLights calls {@code lightAtPos} from chunk-meshing threads.
 */
public enum Timer {
    /** Searchlight and Headlight block entity ticks on the server. */
    SERVER_BLOCK,
    /** The same block entity ticks on the client: beam trace and dynamic spot update. */
    CLIENT_BLOCK,
    /** Lights on Create contraptions, client side. */
    CLIENT_CONTRAPTION,
    /** LambDynamicLights asking our spots for light, during chunk meshing. */
    LDL_LIGHT_AT_POS,
    /** Mixing and drawing the coloured light, once a frame. */
    TINT,
    /** Searchlight, Floodlight and Headlight renderers in the world, once a frame each unless something skips them. */
    LIGHT_RENDER;

    private final LongAdder nanos = new LongAdder();
    private final LongAdder calls = new LongAdder();

    public static long start() {
        return System.nanoTime();
    }

    public void stop(long startNanos) {
        nanos.add(System.nanoTime() - startNanos);
        calls.increment();
    }

    public long nanos() {
        return nanos.sum();
    }

    public long calls() {
        return calls.sum();
    }
}
