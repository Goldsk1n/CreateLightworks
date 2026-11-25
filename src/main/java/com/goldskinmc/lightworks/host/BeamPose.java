package com.goldskinmc.lightworks.host;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A beam's origin and unit direction in world coordinates.
 *
 * @param moving true when the light rides a Sable vehicle or a Create contraption, so its pose can change
 *               every tick
 * @param side   which way the side of the beam's square should face ({@code BeamFrame.side} with a hint), or null
 *               for the usual level side: a carried lamp's is the player's right, so its square stays square to the
 *               view looking straight up or down
 */
public record BeamPose(Vec3 origin, Vec3 direction, boolean moving, @Nullable Vec3 side) {
    public BeamPose(Vec3 origin, Vec3 direction, boolean moving) {
        this(origin, direction, moving, null);
    }

    /** {@link #side} as an array for {@code BeamFrame.side}, or null. */
    public double @Nullable [] sideHint() {
        return side == null ? null : new double[] {side.x, side.y, side.z};
    }
}
