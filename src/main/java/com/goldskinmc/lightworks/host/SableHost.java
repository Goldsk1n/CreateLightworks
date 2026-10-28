package com.goldskinmc.lightworks.host;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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

    private static BeamPose transform(Pose3dc pose, Vec3 origin, Vec3 direction) {
        return new BeamPose(pose.transformPosition(origin), pose.transformNormal(direction).normalize(), true);
    }
}
