package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.host.BeamPose;
import org.jetbrains.annotations.Nullable;

/**
 * A light with a beam in the client world, wherever it lives: a block, a block on a Sable vehicle, or a
 * block carried by a Create contraption. Registered in {@link ClientBeams}.
 */
public interface BeamSource {
    /** The beam in world coordinates as of the last tick. */
    BeamPose tickBeam();

    /** The beam in world coordinates for the frame being drawn. */
    BeamPose renderBeam(float partialTick);

    /** Client state: where the beam landed at the last update. */
    ClientBeam beam();

    /** True once this light no longer exists in the client world. */
    boolean isGone();

    default @Nullable BeamSpot lastSpot() {
        return beam().lastSpot();
    }
}
