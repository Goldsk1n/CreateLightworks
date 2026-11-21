package com.goldskinmc.lightworks.content;

import com.goldskinmc.lightworks.beam.Aim;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.host.ContraptionHost;
import com.goldskinmc.lightworks.light.BeamSensors;
import com.goldskinmc.lightworks.light.BeamTrace;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * A beam on a contraption or train as the server sees it: only where it points, for Beam Sensors in its reach
 * ({@link BeamSensors}). The client's {@link MovingBeam} does the lighting; this traces nothing unless a sensor is near.
 * A Headlight shines only while leading, as on the client.
 */
public final class SensedMovingBeam {
    private final MovementContext context;
    private final LightType type;
    private final LightState state;
    private final ConeFalloff shape;
    private final Vec3 localDirection;
    private boolean leading = true;

    public SensedMovingBeam(MovementContext context, LightType type, LightState state, Direction facing, float yaw,
            float elevation) {
        this.context = context;
        this.type = type;
        this.state = state;
        this.shape = Objects.requireNonNull(type.beam());
        double[] d = Aim.direction(facing.getStepX(), facing.getStepY(), facing.getStepZ(), yaw, elevation);
        this.localDirection = new Vec3(d[0], d[1], d[2]);
    }

    /** A server tick: tells sensors in reach where the beam is. */
    public void tick() {
        BeamPose pose = ContraptionHost.toWorld(context, localDirection, type.lensDistance());
        if (type.followsMotion() && state.signal() == 0 && context.motion != null) {
            double along = context.motion.dot(pose.direction());
            if (Math.abs(along) > MovingBeam.MIN_MOTION) {
                leading = along > 0;
            }
        }
        if (context.disabled || !leading || state.brightness() <= 0
                || !BeamSensors.anyWithin(context.world, pose.origin(), BeamTrace.range(shape))) {
            return;
        }
        BeamSensors.shine(context.world, pose, shape, type.lensHalf(), state.brightness(), state.colour());
    }
}
