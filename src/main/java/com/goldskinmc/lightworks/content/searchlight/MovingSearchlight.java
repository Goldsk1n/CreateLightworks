package com.goldskinmc.lightworks.content.searchlight;

import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.debug.Timer;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.host.ContraptionHost;
import com.goldskinmc.lightworks.light.BeamSource;
import com.goldskinmc.lightworks.light.ClientBeam;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Client side of a light on a contraption, kept in the actor's {@code temporaryData}. */
final class MovingSearchlight implements BeamSource {
    /** Blocks per tick along the beam below which a headlight keeps its current front/rear state. */
    private static final double MIN_MOTION = 0.005;

    private final MovementContext context;
    private final ClientBeam beam;
    private final boolean followsMotion;
    /** Headlight: last seen moving the way the light points. Lit until the first motion says otherwise. */
    private boolean leading = true;
    /** Pose at the last tick; Create clears the context's position when the contraption stops. */
    private BeamPose lastPose;

    private MovingSearchlight(MovementContext context, ConeFalloff shape, boolean followsMotion) {
        this.context = context;
        this.beam = new ClientBeam(shape);
        this.followsMotion = followsMotion;
        this.lastPose = ContraptionHost.toWorld(context, localFacing());
    }

    static MovingSearchlight of(MovementContext context, ConeFalloff shape, boolean followsMotion) {
        if (context.temporaryData instanceof MovingSearchlight light) {
            return light;
        }
        MovingSearchlight light = new MovingSearchlight(context, shape, followsMotion);
        context.temporaryData = light;
        ClientBeams.add(light);
        return light;
    }

    Direction localFacing() {
        return context.state.getValue(SearchlightBlock.FACING);
    }

    void tick() {
        long start = Timer.start();
        update();
        Timer.CLIENT_CONTRAPTION.stop(start);
    }

    private void update() {
        BeamPose pose = ContraptionHost.toWorld(context, localFacing());
        lastPose = pose;
        if (followsMotion && context.motion != null) {
            // Create sets motion to this tick's change in world position, on the client too
            double along = context.motion.dot(pose.direction());
            if (Math.abs(along) > MIN_MOTION) {
                leading = along > 0;
            }
        }
        if (context.disabled || !leading) {
            beam.off();
        } else {
            // When an actor ticks, the bearing or piston has not moved the contraption for this tick yet,
            // and frames then draw it ahead of this pose: lead the spot by half a tick of motion
            Vec3 lead = context.motion == null ? Vec3.ZERO : context.motion.scale(0.5);
            beam.update(context.world, pose, new BeamPose(pose.origin().add(lead), pose.direction(), true));
        }
    }

    @Override
    public BeamPose tickBeam() {
        return lastPose;
    }

    @Override
    public BeamPose renderBeam(float partialTick) {
        return ContraptionHost.toWorldForRender(context, localFacing(), partialTick);
    }

    @Override
    public ClientBeam beam() {
        return beam;
    }

    /** The contraption entity was discarded or unloaded without a stopMoving call. */
    @Override
    public boolean isGone() {
        return context.contraption.entity == null || context.contraption.entity.isRemoved()
                || context.contraption.entity.level() != context.world;
    }
}
