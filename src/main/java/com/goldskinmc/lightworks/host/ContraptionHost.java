package com.goldskinmc.lightworks.host;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.world.phys.Vec3;

/**
 * Beams of blocks carried by a Create contraption (bearings, pistons, gantries, minecarts, trains).
 * A beam's direction is given in contraption space (the block's own coordinates when it was assembled);
 * the contraption entity maps it to the world. The lens sits half a block from the block's centre along
 * the beam, like a placed light's.
 */
public final class ContraptionHost {
    private static final double LENS_RADIUS = 0.5;

    private ContraptionHost() {
    }

    /** As of the contraption's last tick: Create has just set {@code position} and {@code rotation}. */
    public static BeamPose toWorld(MovementContext context, Vec3 localDirection) {
        Vec3 direction = context.rotation.apply(localDirection).normalize();
        return new BeamPose(context.position.add(direction.scale(LENS_RADIUS)), direction, true);
    }

    /** Interpolated for the frame being drawn, like the contraption's own rendering. */
    public static BeamPose toWorldForRender(MovementContext context, Vec3 localDirection, float partialTick) {
        AbstractContraptionEntity entity = context.contraption.entity;
        Vec3 lens = Vec3.atCenterOf(context.localPos).add(localDirection.scale(LENS_RADIUS));
        Vec3 origin = entity.toGlobalVector(lens, partialTick, true)
                .lerp(entity.toGlobalVector(lens, partialTick, false), partialTick);
        Vec3 direction = entity.applyRotation(localDirection, partialTick).normalize();
        return new BeamPose(origin, direction, true);
    }
}
