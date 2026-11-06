package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.compat.DynamicSpot;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import java.util.List;
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

    @Nullable
    private BlockPos placed;
    private int placedLevel;

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
        BlockPos pos = light <= 0 ? null : airBefore(level, spot);
        if (pos == null) {
            clear(level);
            return;
        }
        if (pos.equals(placed) && light == placedLevel) {
            return;
        }
        clear(level);
        level.setBlock(pos, LightworksBlocks.BEAM_LIGHT.getDefaultState().setValue(BeamLightBlock.LEVEL, light),
                BeamLightBlock.QUIET_FLAGS);
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
        BlockState there = level.getBlockState(placed);
        // Only ours: the server may have put something there since, our own light block among them
        if (there.is(LightworksBlocks.BEAM_LIGHT.get()) && there.getValue(BeamLightBlock.LEVEL) == placedLevel) {
            level.setBlock(placed, Blocks.AIR.defaultBlockState(), BeamLightBlock.QUIET_FLAGS);
        }
        PLACED.remove(placed);
        placed = null;
    }

    /** The air block holding the spot's centre, or the nearest one back along the beam. */
    private static @Nullable BlockPos airBefore(ClientLevel level, BeamSpot spot) {
        for (int step = 0; step <= MAX_STEPS_BACK; step++) {
            BlockPos pos = BlockPos.containing(spot.spotX() - spot.dirX() * step, spot.spotY() - spot.dirY() * step,
                    spot.spotZ() - spot.dirZ() * step);
            if (level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }
}
