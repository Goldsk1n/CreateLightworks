package com.goldskinmc.lightworks.render;

import com.goldskinmc.lightworks.beam.Aim;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Turns parts modelled pointing up (mount at the bottom, pivot at the block centre) into a light's mount
 * frame and aim, matching {@link Aim}: model Y becomes the mount facing, model Z the frame's yaw-0
 * direction, so a yaw turn is a turn about model Y and the tilt a turn about model X.
 */
public final class MountFrame {
    private static final Quaternionf[] ROTATIONS = new Quaternionf[6];

    static {
        for (Direction facing : Direction.values()) {
            double[] r = Aim.reference(facing.getStepY());
            double[] s = Aim.side(facing.getStepX(), facing.getStepY(), facing.getStepZ());
            Matrix3f basis = new Matrix3f(
                    new Vector3f((float) s[0], (float) s[1], (float) s[2]),
                    new Vector3f(facing.getStepX(), facing.getStepY(), facing.getStepZ()),
                    new Vector3f((float) r[0], (float) r[1], (float) r[2]));
            ROTATIONS[facing.ordinal()] = new Quaternionf().setFromNormalized(basis);
        }
    }

    private MountFrame() {
    }

    /** Moves the pose to the block centre, turned into the mount frame of {@code facing}. */
    public static void enter(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(new Quaternionf(ROTATIONS[facing.ordinal()]));
    }

    /** Turns about the mount normal by {@code yaw} degrees. */
    public static void yaw(PoseStack poseStack, float yaw) {
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
    }

    /** Tilts a part pointing straight out of the mount down to {@code elevation} degrees above it. */
    public static void tilt(PoseStack poseStack, float elevation) {
        poseStack.mulPose(Axis.XP.rotationDegrees(90 - elevation));
    }

    /** Back from the block centre to the block corner, where models are drawn from. */
    public static void toCorner(PoseStack poseStack) {
        poseStack.translate(-0.5, -0.5, -0.5);
    }
}
