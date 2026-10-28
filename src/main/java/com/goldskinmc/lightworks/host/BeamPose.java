package com.goldskinmc.lightworks.host;

import net.minecraft.world.phys.Vec3;

/**
 * A beam's origin and unit direction in world coordinates.
 *
 * @param moving true when the light rides a Sable vehicle or a Create contraption, so its pose can change
 *               every tick
 */
public record BeamPose(Vec3 origin, Vec3 direction, boolean moving) {
}
