package com.goldskinmc.lightworks.debug.selftest;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What the server-side scripts ask of the client half. In singleplayer both halves run in one JVM, so
 * plain volatile fields are enough. On a dedicated server the client never sees these requests: shots
 * time out and are logged as skipped.
 */
final class SelfTestBridge {
    /** A scenario is running: the client hides the GUI and keeps the game running without focus. */
    static volatile boolean active;
    /** The client has been in the world long enough to render it (shaders compiled, chunks built). */
    static volatile boolean clientReady;
    /** Every scenario has run and the world is restored. */
    static volatile boolean finished;
    /** Point the camera looks at every frame, or null to leave the camera alone. */
    @Nullable
    static volatile Vec3 focus;
    /** Darkens every dynamic spot, to compare the server light path alone. */
    static volatile boolean dynamicLightsOff;
    /** Switches every client beam off (no dynamic light, no cosmetic beam): the baseline for load tests. */
    static volatile boolean beamsOff;
    /** While set, the client records frame times under this name and reports them when it changes. */
    @Nullable
    static volatile String frameStats;

    /** Screenshot requests: the client takes shot {@code shotRequested} and then sets {@code shotDone}. */
    static volatile int shotRequested;
    static volatile int shotDone;
    static volatile String shotName = "";

    /** While set, the client saves a frame every tick under this name (a clip). */
    @Nullable
    static volatile String recording;

    /** While set, the client measures every moving light's spot against the ground at {@code probeGroundY}. */
    @Nullable
    static volatile String probe;
    static volatile double probeGroundY;

    private SelfTestBridge() {
    }

    static int requestShot(String name) {
        shotName = name;
        return ++shotRequested;
    }

    static void reset() {
        active = false;
        focus = null;
        dynamicLightsOff = false;
        beamsOff = false;
        frameStats = null;
        recording = null;
        probe = null;
    }
}
