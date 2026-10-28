package com.goldskinmc.lightworks.debug;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.searchlight.SearchlightBlock;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dev-only check that runs in a real client, enabled with {@code -Pselftest=true}: on joining a world it
 * builds a Searchlight aimed at a wall next to the player, logs what the server lights, lets the client
 * half log what the dynamic light does ({@link SelfTestClient}), then restores the original blocks.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID)
public final class SelfTest {
    public static final boolean ENABLED = Boolean.getBoolean("lightworks.selftest");
    /** Ticks after placement at which the server logs its light, and later restores the blocks. */
    private static final int LOG_AT = 40;
    private static final int RESTORE_AT = 200;

    private static final Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
    private static ServerLevel level;
    private static BlockPos spot;
    private static int ticks = -1;

    private SelfTest() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || ticks >= 0 || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        level = player.serverLevel();
        BlockPos base = player.blockPosition().offset(0, 1, 3);
        spot = base.east(5);
        // Searchlight, five blocks of air, then a wall: remember everything we overwrite
        set(base, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(SearchlightBlock.FACING, Direction.EAST));
        for (int i = 1; i <= 5; i++) {
            set(base.east(i), Blocks.AIR.defaultBlockState());
        }
        set(base.east(6), Blocks.STONE.defaultBlockState());
        ticks = 0;
        Lightworks.LOGGER.info("[selftest] built Searchlight at {} aimed east at a wall, spot {}", base, spot);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ticks < 0) {
            return;
        }
        ticks++;
        if (ticks == LOG_AT) {
            Lightworks.LOGGER.info("[selftest] server: block at spot {}, block light {}",
                    level.getBlockState(spot).getBlock().getDescriptionId(), level.getBrightness(LightLayer.BLOCK, spot));
        }
        if (ticks == RESTORE_AT) {
            saved.forEach((pos, state) -> level.setBlock(pos, state, 3));
            Lightworks.LOGGER.info("[selftest] restored {} blocks", saved.size());
            saved.clear();
            ticks = -2;
        }
    }

    private static void set(BlockPos pos, BlockState state) {
        saved.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, 3);
    }
}
