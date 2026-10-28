package com.goldskinmc.lightworks.content;

import com.goldskinmc.lightworks.beam.Aim;
import com.goldskinmc.lightworks.debug.Timer;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.host.ContraptionHost;
import com.goldskinmc.lightworks.light.BeamSource;
import com.goldskinmc.lightworks.light.ClientBeam;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Client side of one beam of a light carried by a contraption. The beam is fixed in contraption space at
 * {@code (yaw, elevation)} in the mount frame of {@code facing}; the light's state (brightness, colour) is
 * what its block entity had when the contraption assembled: redstone is frozen then, and Contraption
 * Controls switch it instead. Kept in the actor's {@code temporaryData} by the light's movement behaviour.
 */
public final class MovingBeam implements BeamSource {
    /** Blocks per tick along the beam below which a headlight keeps its current front/rear state. */
    private static final double MIN_MOTION = 0.005;
    /** The lens sits this far from the block's centre, along the beam. */
    private static final double LENS_RADIUS = 0.5;

    private final MovementContext context;
    private final LightType type;
    private final LightState state;
    private final ClientBeam beam;
    private final Direction facing;
    private final float yaw;
    private final float elevation;
    /** Beam direction in contraption space. */
    private final Vec3 localDirection;
    /** Headlight: last seen moving the way the light points. Lit until the first motion says otherwise. */
    private boolean leading = true;
    /** Pose at the last tick; Create clears the context's position when the contraption stops. */
    private BeamPose lastPose;

    public MovingBeam(MovementContext context, LightType type, LightState state, Direction facing, float yaw,
            float elevation) {
        this.context = context;
        this.type = type;
        this.state = state;
        this.beam = new ClientBeam(Objects.requireNonNull(type.beam()));
        this.facing = facing;
        this.yaw = yaw;
        this.elevation = elevation;
        double[] d = Aim.direction(facing.getStepX(), facing.getStepY(), facing.getStepZ(), yaw, elevation);
        this.localDirection = new Vec3(d[0], d[1], d[2]);
        this.lastPose = ContraptionHost.toWorld(context, localDirection);
    }

    public void tick() {
        long start = Timer.start();
        update();
        Timer.CLIENT_CONTRAPTION.stop(start);
    }

    private void update() {
        BeamPose pose = ContraptionHost.toWorld(context, localDirection);
        lastPose = pose;
        if (type.followsMotion() && context.motion != null) {
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
            beam.update(context.world, pose, new BeamPose(pose.origin().add(lead), pose.direction(), true),
                    state.brightness());
        }
    }

    /** Lens centre in the block's own coordinates, for drawing inside the contraption. */
    public Vector3f localLens() {
        return new Vector3f(0.5f).add(localAxis().mul((float) LENS_RADIUS));
    }

    public Vector3f localAxis() {
        return new Vector3f((float) localDirection.x, (float) localDirection.y, (float) localDirection.z);
    }

    public int rgb() {
        return state.rgb(type);
    }

    public LightType type() {
        return type;
    }

    public Direction facing() {
        return facing;
    }

    public float yaw() {
        return yaw;
    }

    public float elevation() {
        return elevation;
    }

    @Override
    public BeamPose tickBeam() {
        return lastPose;
    }

    @Override
    public BeamPose renderBeam(float partialTick) {
        return ContraptionHost.toWorldForRender(context, localDirection, partialTick);
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
