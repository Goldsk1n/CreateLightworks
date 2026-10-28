package com.goldskinmc.lightworks.debug;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.searchlight.SearchlightBlock;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dev-only check that runs in a real client, enabled with {@code -Pselftest=true}. On joining a world:
 * <ol>
 *     <li>Fixed light: a Searchlight aimed at a wall next to the player; the server logs its light block.</li>
 *     <li>Vehicle (needs Sable): a deck with a Searchlight hanging below it, turned into a Sable vehicle
 *     with Sable's own commands and flown 10 blocks east; {@link SelfTestClient} checks the lit spot
 *     follows it on the ground.</li>
 * </ol>
 * Every block it changes is restored afterwards.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID)
public final class SelfTest {
    public static final boolean ENABLED = Boolean.getBoolean("lightworks.selftest");
    private static final int FIXED_LOG_AT = 40;
    private static final int FIXED_RESTORE_AT = 200;
    static final int VEHICLE_BUILD_AT = 220;
    static final int VEHICLE_MOVE_FROM = 240;
    static final int VEHICLE_MOVE_TO = 340;
    private static final int VEHICLE_REMOVE_AT = 360;
    /** Blocks per tick: 2 blocks a second, 10 blocks over the flight. */
    static final double VEHICLE_SPEED = 0.1;

    private static final Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
    private static ServerLevel level;
    private static BlockPos playerPos;
    private static int ticks = -1;

    private SelfTest() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || ticks != -1 || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        level = player.serverLevel();
        playerPos = player.blockPosition();
        BlockPos base = playerPos.offset(0, 1, 3);
        set(base, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(SearchlightBlock.FACING, Direction.EAST));
        for (int i = 1; i <= 5; i++) {
            set(base.east(i), Blocks.AIR.defaultBlockState());
        }
        set(base.east(6), Blocks.STONE.defaultBlockState());
        ticks = 0;
        Lightworks.LOGGER.info("[selftest] fixed: built Searchlight at {} aimed east at a wall", base);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ticks < 0) {
            return;
        }
        ticks++;
        BlockPos spot = playerPos.offset(5, 1, 3);
        if (ticks == FIXED_LOG_AT) {
            Lightworks.LOGGER.info("[selftest] fixed: server block at spot {}, block light {}",
                    level.getBlockState(spot).getBlock().getDescriptionId(), level.getBrightness(LightLayer.BLOCK, spot));
        } else if (ticks == FIXED_RESTORE_AT) {
            restore();
            if (!ModList.get().isLoaded("sable")) {
                Lightworks.LOGGER.info("[selftest] vehicle: skipped, Sable is not installed");
                finish();
            }
        } else if (ticks == VEHICLE_BUILD_AT) {
            buildVehicle();
        } else if (ticks >= VEHICLE_MOVE_FROM && ticks < VEHICLE_MOVE_TO) {
            run("sable physics translation @l add " + VEHICLE_SPEED + " 0 0 global");
        } else if (ticks == VEHICLE_REMOVE_AT) {
            run("sable remove @l");
            run("sable paused false");
            restore();
            Lightworks.LOGGER.info("[selftest] vehicle: removed");
            finish();
        }
    }

    /** A 3x3 deck eight blocks up with a Searchlight hanging under its centre, pointing down. */
    private static void buildVehicle() {
        BlockPos deck = playerPos.offset(0, 8, 6);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                set(deck.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
            }
        }
        set(deck.below(), LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(SearchlightBlock.FACING, Direction.DOWN));
        BlockPos from = deck.offset(-1, -1, -1);
        BlockPos to = deck.offset(1, 0, 1);
        // Freeze physics so the vehicle hangs still and moves only when we move it
        run("sable paused true");
        run("sable assemble area " + from.getX() + " " + from.getY() + " " + from.getZ() + " "
                + to.getX() + " " + to.getY() + " " + to.getZ());
        Lightworks.LOGGER.info("[selftest] vehicle: assembled deck at {}, ground at y={}", deck, playerPos.getY() - 1);
    }

    private static void run(String command) {
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void set(BlockPos pos, BlockState state) {
        saved.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, 3);
    }

    private static void restore() {
        saved.forEach((pos, state) -> level.setBlock(pos, state, 3));
        Lightworks.LOGGER.info("[selftest] restored {} blocks", saved.size());
        saved.clear();
    }

    private static void finish() {
        ticks = -2;
        Lightworks.LOGGER.info("[selftest] done");
    }
}
