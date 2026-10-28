package com.goldskinmc.lightworks.host;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Beams of blocks carried by a Create contraption (bearings, pistons, gantries, minecarts, trains).
 * The block's facing is in contraption space; the contraption entity maps it to the world.
 */
public final class ContraptionHost {
    private ContraptionHost() {
    }

    /** As of the contraption's last tick: Create has just set {@code position} and {@code rotation}. */
    public static BeamPose toWorld(MovementContext context, Direction localFacing) {
        Vec3 direction = context.rotation.apply(Vec3.atLowerCornerOf(localFacing.getNormal())).normalize();
        return new BeamPose(context.position.add(direction.scale(0.5)), direction, true);
    }

    /** Interpolated for the frame being drawn, like the contraption's own rendering. */
    public static BeamPose toWorldForRender(MovementContext context, Direction localFacing, float partialTick) {
        AbstractContraptionEntity entity = context.contraption.entity;
        Vec3 lens = Vec3.atCenterOf(context.localPos).relative(localFacing, 0.5);
        Vec3 origin = entity.toGlobalVector(lens, partialTick, true)
                .lerp(entity.toGlobalVector(lens, partialTick, false), partialTick);
        Vec3 direction = entity.applyRotation(Vec3.atLowerCornerOf(localFacing.getNormal()), partialTick).normalize();
        return new BeamPose(origin, direction, true);
    }
}
