package com.goldskinmc.lightworks.host;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;

/**
 * Turns a beam defined in a block entity's own coordinates into world coordinates. Blocks on a Sable
 * vehicle live in plot coordinates far from where the vehicle is; Sable Companion's pose maps them back.
 * Without Sable, or for a block not on a vehicle, the beam is returned unchanged.
 */
public final class SableHost {
    private SableHost() {
    }

    /** Uses the vehicle's logical (physics) pose: for ticks, on either side. */
    public static BeamPose toWorld(Level level, BlockPos blockPos, Vec3 origin, Vec3 direction) {
        SubLevelAccess vehicle = SableCompanion.INSTANCE.getContaining(level, blockPos);
        if (vehicle == null) {
            return new BeamPose(origin, direction, false);
        }
        return transform(vehicle.logicalPose(), origin, direction);
    }

    /** Uses the vehicle's interpolated render pose: for per-frame drawing, client only. */
    public static BeamPose toWorldForRender(BlockPos blockPos, Vec3 origin, Vec3 direction, float partialTick) {
        ClientSubLevelAccess vehicle = SableCompanion.INSTANCE.getContainingClient(blockPos);
        if (vehicle == null) {
            return new BeamPose(origin, direction, false);
        }
        return transform(vehicle.renderPose(partialTick), origin, direction);
    }

    /**
     * How far the block moved over the last physics tick, in world blocks, or null when it is not on a
     * Sable vehicle.
     */
    public static @Nullable Vec3 velocity(Level level, BlockPos blockPos) {
        SubLevelAccess vehicle = SableCompanion.INSTANCE.getContaining(level, blockPos);
        if (vehicle == null) {
            return null;
        }
        Vec3 centre = Vec3.atCenterOf(blockPos);
        return vehicle.logicalPose().transformPosition(centre).subtract(vehicle.lastPose().transformPosition(centre));
    }

    /**
     * Where a point is in the world. Sable's ray-casts hit vehicles too, but report such a hit in the vehicle's plot
     * coordinates; this brings it back. A point not on a vehicle, or any point without Sable, is returned as it is.
     */
    public static Vec3 outOfVehicle(Level level, Vec3 point) {
        if (SableCompanion.INSTANCE.getContaining(level, point) == null) {
            return point;
        }
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, point);
    }

    /**
     * A world point in the plot coordinates of the vehicle holding {@code plotBlock}, by its logical pose; null when that
     * block is on no vehicle.
     */
    public static @Nullable Vec3 intoVehicle(Level level, BlockPos plotBlock, Vec3 world) {
        SubLevelAccess vehicle = SableCompanion.INSTANCE.getContaining(level, plotBlock);
        return vehicle == null ? null : vehicle.logicalPose().transformPositionInverse(world);
    }

    /** As {@link #intoVehicle}, for a direction: unit length, or null. */
    public static @Nullable Vec3 intoVehicleDirection(Level level, BlockPos plotBlock, Vec3 direction) {
        SubLevelAccess vehicle = SableCompanion.INSTANCE.getContaining(level, plotBlock);
        return vehicle == null ? null : vehicle.logicalPose().transformNormalInverse(direction).normalize();
    }

    /**
     * The matrix taking the plot coordinates of the vehicle holding {@code plotBlock} to the world, as it is drawn this
     * frame; null when that block is on no vehicle any more. Client only.
     */
    public static @Nullable Matrix4d renderMatrix(BlockPos plotBlock, float partialTick) {
        ClientSubLevelAccess vehicle = SableCompanion.INSTANCE.getContainingClient(plotBlock);
        if (vehicle == null) {
            return null;
        }
        Pose3dc pose = vehicle.renderPose(partialTick);
        // world = orientation * ((local - rotationPoint) * scale) + position
        return new Matrix4d().translation(pose.position()).rotate(pose.orientation()).scale(pose.scale())
                .translate(-pose.rotationPoint().x(), -pose.rotationPoint().y(), -pose.rotationPoint().z());
    }

    private static BeamPose transform(Pose3dc pose, Vec3 origin, Vec3 direction) {
        return new BeamPose(pose.transformPosition(origin), pose.transformNormal(direction).normalize(), true);
    }
}
