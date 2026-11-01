package com.goldskinmc.lightworks.ponder;

import net.minecraft.core.BlockPos;

/**
 * Where things stand in each Ponder scene, shared by the schematic generator ({@link PonderSchematics})
 * and the scenes ({@code LightworksScenes}) so the two never drift apart. Layer 0 is the base plate; the
 * default Ponder camera looks from the north-west (east is up-left on screen, south up-right), so
 * controls point at north and west faces.
 */
final class Layouts {
    /** 7x7 plate: a Searchlight with a wall to light and a lever beside it. */
    static final class Searchlight {
        static final int SIZE = 7;
        static final BlockPos LIGHT = new BlockPos(1, 1, 3);
        static final BlockPos LEVER = new BlockPos(1, 1, 4);
        static final BlockPos WALL_FROM = new BlockPos(6, 1, 2);
        static final BlockPos WALL_TO = new BlockPos(6, 3, 4);
        static final BlockPos TARGET = new BlockPos(6, 2, 3);
        static final BlockPos TARGET_AFTER_AIM = new BlockPos(6, 3, 2);
    }

    /** 7x7 plate: a Floodlight high on the side of a pillar, aimed at the ground. */
    static final class Floodlight {
        static final int SIZE = 7;
        static final BlockPos PILLAR_FROM = new BlockPos(0, 1, 3);
        static final BlockPos PILLAR_TO = new BlockPos(0, 3, 3);
        static final BlockPos LIGHT = new BlockPos(1, 3, 3);
        static final BlockPos TARGET = new BlockPos(4, 0, 3);
        static final BlockPos TARGET_AFTER_AIM = new BlockPos(4, 0, 5);
    }

    /** 7x7 plate: a cart of brass casing with a lamp at each end; it travels east and back. */
    static final class Cart {
        static final int SIZE = 7;
        static final BlockPos BODY_FROM = new BlockPos(2, 1, 3);
        static final BlockPos BODY_TO = new BlockPos(4, 1, 3);
        static final BlockPos FRONT = new BlockPos(5, 1, 3);
        static final BlockPos REAR = new BlockPos(1, 1, 3);
    }

    /** 5x5 plate: creative motor, Pan-Tilt Head, Searchlight, with an analog lever beside the head. */
    static final class PanTilt {
        static final int SIZE = 5;
        static final BlockPos MOTOR = new BlockPos(2, 1, 2);
        static final BlockPos HEAD = new BlockPos(2, 2, 2);
        static final BlockPos LIGHT = new BlockPos(2, 3, 2);
        static final BlockPos LEVER_BASE = new BlockPos(2, 1, 3);
        static final BlockPos LEVER = new BlockPos(2, 2, 3);
    }

    /** 5x5 plate: creative motor under a Rotating Beacon. */
    static final class Beacon {
        static final int SIZE = 5;
        static final BlockPos MOTOR = new BlockPos(2, 1, 2);
        static final BlockPos BEACON = new BlockPos(2, 2, 2);
    }

    /** 5x5 plate: one Warning Light. */
    static final class Warning {
        static final int SIZE = 5;
        static final BlockPos LAMP = new BlockPos(2, 1, 2);
    }

    /** 7x7 plate: a strip of road between two rows of markers, with a lever beside one. */
    static final class Edge {
        static final int SIZE = 7;
        static final BlockPos STRIP_FROM = new BlockPos(2, 1, 0);
        static final BlockPos STRIP_TO = new BlockPos(4, 1, 6);
        static final BlockPos[] LEFT = {new BlockPos(1, 1, 1), new BlockPos(1, 1, 3), new BlockPos(1, 1, 5)};
        static final BlockPos[] RIGHT = {new BlockPos(5, 1, 1), new BlockPos(5, 1, 3), new BlockPos(5, 1, 5)};
    }

    private Layouts() {
    }
}
