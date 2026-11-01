package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.beam.BeamFrame;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where a fixed beam's light goes besides its rays. Block light comes from single points, and a wide beam's rays land
 * far apart, or stop early on the ground when it is level, so a block between them would stay dark inside the beam.
 * This spreads from where the rays landed over every surface in the beam, and asks for one light per
 * {@value #CELL} blocks of it, close enough for their light to meet.
 */
final class BeamArea {
    /** Block light fades one level a block; shader packs make that steeper, so farther apart shows as separate pools. */
    private static final int CELL = 4;
    /** Surfaces looked at per beam; well past what a Floodlight lights at its full range. */
    private static final int MAX_BLOCKS = 1600;
    /** Less than this is no light at all once it is a whole light level. */
    private static final double MIN_LIGHT = 0.5;
    private static final double AIR_ABOVE_FACE = 0.25;

    private BeamArea() {
    }

    /**
     * @param landed where the beam's rays landed, to spread from
     * @return one landing per cell of the lit surface that the lens sees, with its light at full brightness
     */
    static List<BeamTrace.Landing> landings(Level level, Vec3 origin, Vec3 axis, ConeFalloff shape, double lensHalf,
            double range, List<BeamTrace.Landing> landed) {
        double[] u = BeamFrame.side(axis.x, axis.y, axis.z);
        double[] v = BeamFrame.up(axis.x, axis.y, axis.z, u);
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (BeamTrace.Landing landing : landed) {
            enqueueAround(BlockPos.containing(landing.hit().end()), visited, queue);
        }
        // Rays that land apart from the rest, such as on a block standing clear of the ground
        double spread = Math.tan(BeamTrace.visibleHalfAngle(shape));
        for (int i = 0; i < BeamFrame.GRID * BeamFrame.GRID; i++) {
            Vec3[] ray = BeamTrace.cornerRay(i, origin, axis, u, v, spread, lensHalf);
            BeamTrace.Hit hit = BeamTrace.trace(level, ray[0], ray[1], range);
            if (hit.hitBlock()) {
                enqueueAround(BlockPos.containing(hit.end()), visited, queue);
            }
        }

        Long2ObjectOpenHashMap<Candidate> best = new Long2ObjectOpenHashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        int blocks = 0;
        while (!queue.isEmpty() && blocks < MAX_BLOCKS) {
            pos.set(queue.dequeueLong());
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(LightworksBlocks.BEAM_LIGHT.get()) || state.getShape(level, pos).isEmpty()) {
                continue;
            }
            blocks++;
            boolean lit = false;
            for (Direction side : Direction.values()) {
                Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
                Vec3 face = Vec3.atCenterOf(pos).add(normal.scale(0.5));
                if (origin.subtract(face).dot(normal) <= 0) {
                    continue;
                }
                double light = shape.lightInBeam(face.x - origin.x, face.y - origin.y, face.z - origin.z, axis.x, axis.y, axis.z,
                        u[0], u[1], u[2], v[0], v[1], v[2], lensHalf);
                // Our own lights count as air, or the next search would avoid every place this one chose
                BlockState inFront = level.getBlockState(neighbour.setWithOffset(pos, side));
                if (light < MIN_LIGHT || !(inFront.isAir() || inFront.is(LightworksBlocks.BEAM_LIGHT.get()))) {
                    continue;
                }
                lit = true;
                long cell = BlockPos.asLong(Math.floorDiv(neighbour.getX(), CELL), Math.floorDiv(neighbour.getY(), CELL),
                        Math.floorDiv(neighbour.getZ(), CELL));
                Candidate held = best.get(cell);
                if (held == null || light > held.light()) {
                    best.put(cell, new Candidate(pos.immutable(), face, normal, light));
                }
            }
            if (lit) {
                enqueueAround(pos, visited, queue);
            }
        }

        List<Candidate> strongestFirst = new ArrayList<>(best.values());
        strongestFirst.sort(Comparator.comparingDouble(candidate -> -candidate.light()));
        List<BeamTrace.Landing> landings = new ArrayList<>(strongestFirst.size());
        for (Candidate candidate : strongestFirst) {
            Vec3 toFace = candidate.face().subtract(origin);
            // A surface behind a wall can be inside the cone: only what the lens sees is lit. Aimed a little off the
            // surface, or a ray along the ground would stop on the next block and call the rest of the ground unseen
            Vec3 toAir = candidate.face().add(candidate.normal().scale(AIR_ABOVE_FACE)).subtract(origin);
            BeamTrace.Hit hit = BeamTrace.trace(level, origin, toAir, toAir.length());
            if (!hit.hitBlock() || candidate.block().equals(hit.block())) {
                landings.add(new BeamTrace.Landing(new BeamTrace.Hit(candidate.face(), candidate.block(), toFace.length()),
                        candidate.normal().scale(-1), candidate.light()));
            }
        }
        return landings;
    }

    private static void enqueueAround(BlockPos centre, LongOpenHashSet visited, LongArrayFIFOQueue queue) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    long next = BlockPos.asLong(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
                    if (visited.add(next)) {
                        queue.enqueue(next);
                    }
                }
            }
        }
    }

    private record Candidate(BlockPos block, Vec3 face, Vec3 normal, double light) {
    }
}
