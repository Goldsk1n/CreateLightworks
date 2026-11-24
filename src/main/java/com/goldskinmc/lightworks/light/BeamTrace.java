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
    /**
     * @param normal    unit normal of the face it struck, out of the face; null when not known (out of range, or on a Sable
     *                  vehicle, whose faces turn with it)
     */
    public record Hit(Vec3 end, @Nullable BlockPos block, double distance, @Nullable Vec3 onVehicle, @Nullable Vec3 normal) {
        public Hit(Vec3 end, @Nullable BlockPos block, double distance) {
            this(end, block, distance, null, null);
        }

        public Hit(Vec3 end, @Nullable BlockPos block, double distance, @Nullable Vec3 normal) {
            this(end, block, distance, null, normal);
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
        Vec3 normal = hit.normal() == null ? Vec3.ZERO : hit.normal();
        return new BeamSpot(centre.x, centre.y, centre.z, hit.end().x, hit.end().y, hit.end().z,
                dir.x, dir.y, dir.z, peak, normal.x, normal.y, normal.z);
    }

    /**
     * A moving light's spot at the middle of where its rays landed, each weighted by its light: the centre's landing and
     * its corner and edge rays'. It drifts across terraced ground as the light moves, where the centre's landing alone
     * jumped from step to step. The struck faces' normals are averaged the same way (any unknown, and the spot is cut by
     * a plane square to the beam, as {@link #spotFor} does without one), and the cut lies at the deepest landing. Each
     * landing counts by its light and by how near its depth is to the centre's ({@link #weight}). Null when nothing has any light.
     */
    public static @Nullable BeamSpot middleSpot(Landing centre, List<Landing> edges, double peak) {
        if (peak <= 0) {
            return null;
        }
        double total = 0;
        double sx = 0, sy = 0, sz = 0, dx = 0, dy = 0, dz = 0, nx = 0, ny = 0, nz = 0;
        boolean normals = true;
        List<Landing> all = new java.util.ArrayList<>(edges.size() + 1);
        all.add(centre);
        all.addAll(edges);
        for (Landing landing : all) {
            double w = weight(centre, landing);
            if (w <= 0 || !landing.hit().hitBlock()) {
                continue;
            }
            Vec3 dir = landing.direction().normalize();
            Vec3 end = landing.hit().end();
            total += w;
            sx += w * (end.x - dir.x * 0.5);
            sy += w * (end.y - dir.y * 0.5);
            sz += w * (end.z - dir.z * 0.5);
            dx += w * dir.x;
            dy += w * dir.y;
            dz += w * dir.z;
            Vec3 normal = landing.hit().normal();
            if (normal == null) {
                normals = false;
            } else {
                nx += w * normal.x;
                ny += w * normal.y;
                nz += w * normal.z;
            }
        }
        if (total <= 0) {
            return null;
        }
        Vec3 dir = new Vec3(dx, dy, dz).normalize();
        Vec3 normal = normals ? new Vec3(nx, ny, nz) : Vec3.ZERO;
        normal = normal.lengthSqr() < 1e-6 ? Vec3.ZERO : normal.normalize();
        // The light is cut at the deepest landing, not the middle one: on terraced ground the steps lying more than a
        // block behind the middle went dark, and the lit patch dipped for a tick whenever the middle moved (ship clip)
        Vec3 cut = normal == Vec3.ZERO ? dir.scale(-1) : normal;
        Vec3 deepest = null;
        for (Landing landing : all) {
            // Every lit landing, not only those near the centre's depth: cut at the centre's, the wall behind a block the
            // beam pointed at went dark round it (owner, Nov 24)
            if (landing.light() > 0 && landing.hit().hitBlock()
                    && (deepest == null || landing.hit().end().dot(cut) < deepest.dot(cut))) {
                deepest = landing.hit().end();
            }
        }
        return new BeamSpot(sx / total, sy / total, sz / total, deepest.x, deepest.y, deepest.z,
                dir.x, dir.y, dir.z, peak, normal.x, normal.y, normal.z);
    }

    /**
     * How much a landing counts towards a moving light's middle: its light, faded by how far its depth along the beam lies
     * from the centre landing's ({@link BeamSpot#depthWeight}). The centre's own counts by its light alone. From the
     * centre's depth, not the landings' weighted middle depth: that left the hall's floor in, and the ship clip's patch
     * dipped more often (Nov 24).
     */
    public static double weight(Landing centre, Landing landing) {
        if (landing == centre) {
            return landing.light();
        }
        double depth = landing.hit().distance() * landing.direction().normalize().dot(centre.direction().normalize());
        return landing.light() * BeamSpot.depthWeight(centre.hit().distance(), depth);
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
        Vec3 normal = Vec3.atLowerCornerOf(result.getDirection().getNormal());
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
                normal = null;
            }
        }
        return new Hit(end, block, end.distanceTo(origin), onVehicle, normal);
    }
}
