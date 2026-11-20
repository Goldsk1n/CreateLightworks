package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.registry.LightworksBlocks;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A carried lamp's light with no dynamic lights mod: weaker light blocks in the client's world, one in the air before each
 * place its beam lands ({@link ClientBeam} spreads the rays so they land at most two blocks apart), rather than one bright
 * block that follows the spot. Minecraft's block light moves in whole blocks, so a single block stepped after the lit
 * square, a block at a time (owner, Nov 18-20). A block stays while the beam still lands there, so the light round the
 * square stays put as it glides, and only blocks at the beam's edge come and go. Tried and left: cells of a 2-block grid
 * kept their light where the beam first landed in them, up to a cell from where it lands now, and left the middle of a pit
 * dark and the light off to one side (self-test {@code handheld-pit}). Placed and taken away as {@link ClientLightSpot}'s
 * block is. Client thread only.
 */
final class ClientLightPool {
    /**
     * Ticks a cell keeps its light after the beam last landed in it while the beam is held still, so cells at the edge do
     * not blink; while it moves, a cell the beam left goes at once.
     */
    static final int HOLD_TICKS = 4;
    /** How far back along a ray to look for air when the block before the face is not. */
    private static final int MAX_STEPS_BACK = 2;

    private record Cell(BlockPos pos, int level, long seen) {
    }

    private final Map<Long, Cell> cells = new HashMap<>();

    /** Lights the cells the landings fall in, at {@code level}, and takes away those the beam left. */
    void update(Level level, List<BeamTrace.Landing> landings, int lightLevel, long tick, int holdTicks) {
        // Our own blocks count as room for light: taken for something solid, a ray stepped back past its cell's block,
        // the cell looked left behind and was taken away, then put back once its air was free again, every few ticks; the
        // ground round it brightened and dimmed while the player stood still (owner, Nov 20; self-test handheld-still)
        Set<BlockPos> ours = new HashSet<>();
        for (Cell cell : cells.values()) {
            ours.add(cell.pos());
        }
        for (BeamTrace.Landing landing : landings) {
            Vec3 end = landing.hit().end();
            Vec3 back = landing.direction().normalize();
            BlockPos air = null;
            for (int step = 0; step <= MAX_STEPS_BACK && air == null; step++) {
                BlockPos pos = BlockPos.containing(end.subtract(back.scale(0.3 + step)));
                if (ours.contains(pos) || BeamLightBlock.canHold(level.getBlockState(pos))) {
                    air = pos;
                }
            }
            if (air == null) {
                continue;
            }
            long key = air.asLong();
            Cell cell = cells.get(key);
            if (cell != null && cell.level() == lightLevel) {
                cells.put(key, new Cell(cell.pos(), cell.level(), tick));
                continue;
            }
            if (cell != null) {
                take(level, cell);
            }
            level.setBlock(air, BeamLightBlock.replacing(level.getBlockState(air), LightworksBlocks.BEAM_LIGHT.getDefaultState(),
                    lightLevel), BeamLightBlock.QUIET_FLAGS);
            ClientLightSpot.track(air, true);
            cells.put(key, new Cell(air, lightLevel, tick));
        }
        for (Iterator<Cell> it = cells.values().iterator(); it.hasNext(); ) {
            Cell cell = it.next();
            if (tick - cell.seen() > holdTicks) {
                take(level, cell);
                it.remove();
            }
        }
    }

    void clear(Level level) {
        for (Cell cell : cells.values()) {
            take(level, cell);
        }
        cells.clear();
    }

    int size() {
        return cells.size();
    }

    private static void take(Level level, Cell cell) {
        BlockState there = level.getBlockState(cell.pos());
        // Only ours: the server may have put something there since
        if (there.is(LightworksBlocks.BEAM_LIGHT.get()) && there.getValue(BeamLightBlock.LEVEL) == cell.level()) {
            level.setBlock(cell.pos(), BeamLightBlock.emptied(there), BeamLightBlock.QUIET_FLAGS);
        }
        ClientLightSpot.track(cell.pos(), false);
    }
}
