package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.compat.DynamicSpot;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A moving light's spot as one of our invisible light blocks placed in the client's own world only, for players with no
 * dynamic lights mod. To Minecraft's light engine, and so to a shader pack, it is ordinary block light: under a shader
 * pack a moving light otherwise lit nothing, our painted spots being off there. The server never hears of it, and a
 * block the server sends to that place simply replaces it. Client thread only.
 */
public final class ClientLightSpot implements DynamicSpot {
    /** How far back along the beam to look for air when the spot's own block is not. */
    private static final int MAX_STEPS_BACK = 2;
    /** Where client light blocks stand now: written on the client thread, read by a test on the server thread. */
    private static final Set<BlockPos> PLACED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger MOVES = new AtomicInteger();

    /** How many spots hold the light block at a place and the level it was set to. */
    private record Owned(int count, int level) {
    }

    /**
     * Light blocks two spots share: a moved light's handoff stands where the light was a moment ago, and the light can
     * come back to the same block. Each place is taken away only once no spot holds it; before, a spot moving off a block
     * removed the one the other had just put there, and the light went out for a tick. Client thread only.
     */
    private static final Map<BlockPos, Owned> OWNED = new HashMap<>();

    @Nullable
    private BlockPos placed;
    private int placedLevel;

    /** A client light block placed or taken away by another holder ({@link ClientLightPool}), for the status and tests. */
    static void track(BlockPos pos, boolean placed) {
        if (placed) {
            PLACED.add(pos);
            MOVES.incrementAndGet();
        } else {
            PLACED.remove(pos);
        }
    }

    public static String status() {
        return String.format("client light blocks %d, moved %d times", PLACED.size(), MOVES.get());
    }

    /** Where client light blocks stand now (for testing). */
    public static List<BlockPos> placedPositions() {
        return List.copyOf(PLACED);
    }

    /** The client world's block at {@code pos}, or null without one (for testing). */
    public static @Nullable BlockState clientStateAt(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.getBlockState(pos);
    }

    @Override
    public void set(@Nullable BeamSpot spot) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            placed = null;
            return;
        }
        int light = spot == null ? 0 : (int) Math.round(Math.min(15, spot.peak()));
        if (light > 0 && placed != null && Math.abs(light - placedLevel) <= 1 && near(placed, spot)
                && level.getBlockState(placed).is(LightworksBlocks.BEAM_LIGHT.get())) {
            // Still within half a block of where it stands and within a level of its light: stays as it is. A spot
            // wobbling across a block's edge, or a light hovering between two levels, placed it again and again, each
            // time re-meshing the sections round it, and the lit patch blinked every other tick (ship clip, Nov 21)
            return;
        }
        BlockPos pos = light <= 0 ? null : airBefore(level, spot);
        if (pos == null) {
            clear(level);
            return;
        }
        if (pos.equals(placed) && light == placedLevel) {
            return;
        }
        clear(level);
        BlockState there = level.getBlockState(pos);
        Owned owned = OWNED.get(pos);
        if (owned != null && there.is(LightworksBlocks.BEAM_LIGHT.get()) && there.getValue(BeamLightBlock.LEVEL) == owned.level()) {
            // Another spot's light block already stands here: shared, at the brighter of the two
            int shared = Math.max(owned.level(), light);
            if (shared != owned.level()) {
                level.setBlock(pos, there.setValue(BeamLightBlock.LEVEL, shared), BeamLightBlock.QUIET_FLAGS);
            }
            OWNED.put(pos, new Owned(owned.count() + 1, shared));
        } else {
            level.setBlock(pos, BeamLightBlock.replacing(there, LightworksBlocks.BEAM_LIGHT.getDefaultState(), light),
                    BeamLightBlock.QUIET_FLAGS);
            OWNED.put(pos, new Owned(1, light));
        }
        placed = pos;
        placedLevel = light;
        PLACED.add(pos);
        MOVES.incrementAndGet();
    }

    @Override
    public void remove() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            clear(level);
        }
        placed = null;
    }

    private void clear(ClientLevel level) {
        if (placed == null) {
            return;
        }
        Owned owned = OWNED.get(placed);
        if (owned != null && owned.count() > 1) {
            // Another spot still holds it
            OWNED.put(placed, new Owned(owned.count() - 1, owned.level()));
            placed = null;
            return;
        }
        OWNED.remove(placed);
        int level0 = owned == null ? placedLevel : owned.level();
        BlockState there = level.getBlockState(placed);
        // Only ours: the server may have put something there since, our own light block among them
        if (there.is(LightworksBlocks.BEAM_LIGHT.get()) && there.getValue(BeamLightBlock.LEVEL) == level0) {
            level.setBlock(placed, BeamLightBlock.emptied(there), BeamLightBlock.QUIET_FLAGS);
        }
        PLACED.remove(placed);
        placed = null;
    }

    /** True when the spot's centre lies within half a block outside {@code pos}'s own block, or in it. */
    private static boolean near(BlockPos pos, BeamSpot spot) {
        return Math.abs(spot.spotX() - (pos.getX() + 0.5)) < STAY
                && Math.abs(spot.spotY() - (pos.getY() + 0.5)) < STAY
                && Math.abs(spot.spotZ() - (pos.getZ() + 0.5)) < STAY;
    }

    /** How far from a light block's centre, along each axis, a spot may drift before the block moves. */
    private static final double STAY = 1.0;

    /** The air or water block holding the spot's centre, or the nearest one back along the beam. */
    private static @Nullable BlockPos airBefore(ClientLevel level, BeamSpot spot) {
        for (int step = 0; step <= MAX_STEPS_BACK; step++) {
            BlockPos pos = BlockPos.containing(spot.spotX() - spot.dirX() * step, spot.spotY() - spot.dirY() * step,
                    spot.spotZ() - spot.dirZ() * step);
            if (BeamLightBlock.canHold(level.getBlockState(pos))) {
                return pos;
            }
        }
        return null;
    }
}
