package com.goldskinmc.lightworks.debug.selftest;

import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
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
    /** Shows the GUI during a scenario (goggle overlay shots); it is hidden otherwise. */
    static volatile boolean showGui;
    /** Shows the player from the front during a scenario (items in the hand). */
    static volatile boolean thirdPerson;
    /**
     * Comma-separated item ids whose Ponder scenes the client walks through, photographing each. Ponder's
     * screen pauses a singleplayer game, so the client runs this alone and sets {@code ponderDone}.
     */
    @Nullable
    static volatile String ponderPlan;
    static volatile boolean ponderDone;
    /** The game's Brightness at "Bright" while set, for mod-page shots; restored when cleared. */
    static volatile boolean bright;
    /** Darkens every dynamic spot, to compare the server light path alone. */
    static volatile boolean dynamicLightsOff;
    /** Switches every client beam off (no dynamic light, no cosmetic beam): the baseline for load tests. */
    static volatile boolean beamsOff;
    /** While set, the client records frame times under this name and reports them when it changes. */
    @Nullable
    static volatile String frameStats;

    /** While set, the client shows the value board of the block entity here, as a player holding its box does. */
    @Nullable
    static volatile BlockPos valueBoard;

    /** Screenshot requests: the client takes shot {@code shotRequested} and then sets {@code shotDone}. */
    static volatile int shotRequested;
    static volatile int shotDone;
    static volatile String shotName = "";

    /** While set, the client saves a frame every tick under this name (a clip). */
    @Nullable
    static volatile String recording;
    /**
     * While set, the client flies the player at this speed (blocks a tick) every tick: smooth movement, drawn between
     * ticks, which a teleport each tick is not. Each frame then also checks that the camera our layers are drawn from is
     * where the terrain was drawn from.
     */
    @Nullable
    static volatile Vec3 velocity;

    /** While set, the client measures every moving light's spot against the ground at {@code probeGroundY}. */
    @Nullable
    static volatile String probe;
    static volatile double probeGroundY;
    /** While set, the probe also measures how far each spot is from this vertical axis and how far round it goes. */
    @Nullable
    static volatile Vec3 probeAxis;

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
        bright = false;
        showGui = false;
        thirdPerson = false;
        ponderPlan = null;
        beamsOff = false;
        frameStats = null;
        recording = null;
        velocity = null;
        probe = null;
        probeAxis = null;
    }
}
