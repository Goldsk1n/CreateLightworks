package com.goldskinmc.lightworks.host;

import net.minecraft.world.phys.Vec3;

/**
 * A beam's origin and unit direction in world coordinates.
 *
 * @param onVehicle true when the light sits on a Sable vehicle, so its pose changes every tick
 */
public record BeamPose(Vec3 origin, Vec3 direction, boolean onVehicle) {
}
