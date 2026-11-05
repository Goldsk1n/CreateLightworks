package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.beam.BeamFrame;
import com.goldskinmc.lightworks.beam.BeamRays;
import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.host.SableHost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Finds where a beam stops: the first block whose visual shape it meets, or the end of its range.
 * Lives here rather than in {@code beam/} because it needs the world; the beam maths stays pure.
 */
public final class BeamTrace {
    /** Start the ray just past the lens, so it never hits the light's own block. */
    private static final double START_OFFSET = 0.01;
    /** Edge rays landing nearer than this (blocks) do not count as where the beam lands. */
    private static final double MIN_LANDING = 0.6;

    private BeamTrace() {
    }

    /**
     * @param end      where the beam stops, on the hit face or at full range
     * @param block    the block it hit, or null if it ran out of range
     * @param distance from {@code origin} to {@code end}, in blocks
     */
    /**
     * @param onVehicle where the beam stopped in a Sable vehicle's own (plot) coordinates when it stopped on one, where
     *                  its faces are found to paint; null otherwise
     */
    public record Hit(Vec3 end, @Nullable BlockPos block, double distance, @Nullable Vec3 onVehicle) {
        public Hit(Vec3 end, @Nullable BlockPos block, double distance) {
            this(end, block, distance, null);
        }

        public boolean hitBlock() {
            return block != null;
        }
    }

    /**
     * The lit spot for a dynamic light, or null when the beam hit nothing or is too dim to show.
     * The spot centre sits half a block in front of the lit surface, like the server light block.
     */
    public static @Nullable BeamSpot spotFor(Hit hit, Vec3 direction, double peak) {
        if (!hit.hitBlock() || peak <= 0) {
            return null;
        }
        Vec3 dir = direction.normalize();
        Vec3 centre = hit.end().subtract(dir.scale(0.5));
        return new BeamSpot(centre.x, centre.y, centre.z, hit.end().x, hit.end().y, hit.end().z,
                dir.x, dir.y, dir.z, peak);
    }

    /** How far a beam of this shape reaches: its own range, capped by the server's {@code maxBeamRange}. */
    public static double range(ConeFalloff shape) {
        return Math.min(shape.range(), LightworksConfig.maxBeamRange());
    }

    /** Where part of a beam landed: the ray that hit, its direction and the light there (0 to 15 at full brightness). */
    public record Landing(Hit hit, Vec3 direction, double light) {
    }

    /** Half-angle of the visible square beam, in radians: halfway into the soft edge, where the lit spot visibly ends. */
    public static double visibleHalfAngle(ConeFalloff shape) {
        return Math.toRadians(shape.coreDegrees() + shape.edgeDegrees() / 2);
    }

    /**
     * Start and direction of the beam's corner or edge ray {@code index} ({@link BeamFrame#corner}): from the
     * lens's own corner, spreading at the visible half-angle.
     *
     * @param u the beam's side axis and {@code v} its other cross axis ({@link BeamFrame})
     */
    public static Vec3[] cornerRay(int index, Vec3 origin, Vec3 axis, double[] u, double[] v, double spread,
            double lensHalf) {
        return ray(BeamFrame.cornerU(index), BeamFrame.cornerV(index), origin, axis, u, v, spread, lensHalf);
    }

    /** As {@link #cornerRay}, for the ray at offsets (cu, cv), each -1 to 1, across the square ({@link BeamFrame#rimU}). */
    public static Vec3[] ray(double cu, double cv, Vec3 origin, Vec3 axis, double[] u, double[] v, double spread,
            double lensHalf) {
        Vec3 offset = new Vec3(cu * u[0] + cv * v[0], cu * u[1] + cv * v[1], cu * u[2] + cv * v[2]);
        return new Vec3[] {origin.add(offset.scale(lensHalf)), axis.add(offset.scale(spread)).normalize()};
    }

    /**
     * Light where a corner or edge ray lands {@code distance} blocks out, 0 to 15 at full brightness: the
     * beam's strength at its visible edge (halfway into the soft edge). Measured on the ray itself, not from
     * the lens centre, because the rays start at the lens's own corners and a narrow beam's would otherwise
     * fall just outside its light cone near the lens.
     */
    public static double edgeLight(ConeFalloff shape, double distance) {
        return rayLight(shape, distance, 1);
    }

    /** As {@link #edgeLight}, for a ray {@code fraction} of the way from the centre to the visible edge. */
    private static double rayLight(ConeFalloff shape, double distance, double fraction) {
        double across = fraction * Math.tan(visibleHalfAngle(shape));
        return shape.onAxis(distance) * shape.lightAt(across, 0, 1, 0, 0, 1) / shape.onAxis(1e-3);
    }

    /**
     * Where a fixed light's beam places light: one landing per ray of {@code rays} that hits something, or,
     * when none does, the single landing of {@link #edgeLanding}. Empty in the open. Light is 0 to 15 at full
     * brightness. A wide beam's rays sit halfway to its visible edge, in its full-strength core.
     */
    public static List<Landing> landings(BlockGetter level, Vec3 origin, Vec3 axis, ConeFalloff shape,
            double lensHalf, double range, BeamRays rays) {
        List<Landing> landings = new ArrayList<>();
        if (rays.single()) {
            Hit hit = trace(level, origin, axis, range);
            if (hit.hitBlock()) {
                landings.add(new Landing(hit, axis, shape.onAxis(hit.distance())));
            }
        } else {
            double[] u = BeamFrame.side(axis.x, axis.y, axis.z);
            double[] v = BeamFrame.up(axis.x, axis.y, axis.z, u);
            double spread = Math.tan(visibleHalfAngle(shape)) / 2;
            for (int[] offset : rays.offsets()) {
                Vec3[] ray = cornerRay(BeamFrame.corner(offset[0], offset[1]), origin, axis, u, v, spread, lensHalf / 2);
                Hit hit = trace(level, ray[0], ray[1], range);
                if (landsUsefully(hit)) {
                    landings.add(new Landing(hit, ray[1], rayLight(shape, hit.distance(), 0.5)));
                }
            }
        }
        if (landings.isEmpty()) {
            Landing edge = edgeLanding(level, origin, axis, shape, lensHalf, range);
            if (edge != null) {
                landings.add(edge);
            }
        }
        return landings;
    }

    /**
     * Where a beam whose centre ray hit nothing still lands: along its full-strength inner rays
     * ({@link #halfLanding}), or else the nearest of its corner and edge rays that hits a block, with the
     * (soft-edge, so weaker) light there; null when none does (open sky).
     */
    public static @Nullable Landing edgeLanding(BlockGetter level, Vec3 origin, Vec3 axis, ConeFalloff shape,
            double lensHalf, double range) {
        double[] u = BeamFrame.side(axis.x, axis.y, axis.z);
        double[] v = BeamFrame.up(axis.x, axis.y, axis.z, u);
        double spread = Math.tan(visibleHalfAngle(shape));
        Landing inner = halfLanding(level, origin, axis, shape, lensHalf, range);
        if (inner != null) {
            return inner;
        }
        Landing nearest = null;
        for (int i = 0; i < BeamFrame.GRID * BeamFrame.GRID; i++) {
            if (i == BeamFrame.corner(0, 0)) {
                continue;
            }
            Vec3[] ray = cornerRay(i, origin, axis, u, v, spread, lensHalf);
            Hit hit = trace(level, ray[0], ray[1], range);
            if (landsUsefully(hit) && (nearest == null || hit.distance() < nearest.hit().distance())) {
                nearest = new Landing(hit, ray[1], edgeLight(shape, hit.distance()));
            }
        }
        return nearest;
    }

    /**
     * Where a beam lands along the four rays halfway between its centre and its edges (in its full-strength
     * core): the furthest that lands, so a beam going through a gap in a wall stays on the wall, not on the
     * ground at its foot. Null when none lands.
     */
    public static @Nullable Landing halfLanding(BlockGetter level, Vec3 origin, Vec3 axis, ConeFalloff shape,
            double lensHalf, double range) {
        double[] u = BeamFrame.side(axis.x, axis.y, axis.z);
        double[] v = BeamFrame.up(axis.x, axis.y, axis.z, u);
        double spread = Math.tan(visibleHalfAngle(shape)) / 2;
        Landing furthest = null;
        for (int i : new int[] {BeamFrame.corner(-1, 0), BeamFrame.corner(1, 0), BeamFrame.corner(0, -1),
                BeamFrame.corner(0, 1)}) {
            Vec3[] ray = cornerRay(i, origin, axis, u, v, spread, lensHalf / 2);
            Hit hit = trace(level, ray[0], ray[1], range);
            if (landsUsefully(hit) && (furthest == null || hit.distance() > furthest.hit().distance())) {
                furthest = new Landing(hit, ray[1], rayLight(shape, hit.distance(), 0.5));
            }
        }
        return furthest;
    }

    /**
     * True for a ray that hit something at least {@value #MIN_LANDING} blocks out: nearer, the light would sit
     * inside the light's own block (a wide beam's edge touching the block it stands on).
     */
    public static boolean landsUsefully(Hit hit) {
        return hit.hitBlock() && hit.distance() >= MIN_LANDING;
    }

    public static Hit trace(BlockGetter level, Vec3 origin, Vec3 direction, double range) {
        Vec3 dir = direction.normalize();
        Vec3 start = origin.add(dir.scale(START_OFFSET));
        Vec3 limit = origin.add(dir.scale(range));
        BlockHitResult result = level.clip(new ClipContext(start, limit,
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (result.getType() == HitResult.Type.MISS) {
            return new Hit(limit, null, range);
        }
        Vec3 end = result.getLocation();
        // clip() can hand back a mutable position that it reuses; keep our own copy
        BlockPos block = result.getBlockPos().immutable();
        Vec3 onVehicle = null;
        if (level instanceof Level world) {
            Vec3 inWorld = SableHost.outOfVehicle(world, end);
            if (inWorld != end) {
                onVehicle = end;
                // The ray hit a Sable vehicle, reported in the vehicle's own coordinates: the beam stops where that is
                // in the world, and the block it lands on is the world block just inside the vehicle's surface (air
                // in the world, so light goes in front of it and nothing there is painted)
                end = inWorld;
                block = BlockPos.containing(end.add(dir.scale(0.01)));
            }
        }
        return new Hit(end, block, end.distanceTo(origin), onVehicle);
    }
}
