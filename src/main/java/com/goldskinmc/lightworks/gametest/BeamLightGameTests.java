package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.searchlight.SearchlightBlock;
import com.goldskinmc.lightworks.light.BeamLightBlock;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server light-block path (the no-client-mods fallback): the light lands where a fixed Searchlight points,
 * follows it, cleans up after it, and stays invisible to redstone. Scenario IDs refer to the tech spike plan.
 */
@GameTestHolder(Lightworks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BeamLightGameTests {
    private static final String EMPTY = "empty";
    /** 24 x 5 x 5 air, for beams of 20 blocks. */
    private static final String EMPTY_LONG = "empty_long";

    private BeamLightGameTests() {
    }

    private static void placeSearchlight(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(SearchlightBlock.FACING, facing));
    }

    private static boolean isBeamLight(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).is(LightworksBlocks.BEAM_LIGHT.get());
    }

    /** S1: aimed at a wall 20 blocks away, the spot in front of the wall reaches light level 12 or more. */
    @GameTest(template = EMPTY_LONG)
    public static void s1SearchlightLightsAWallTwentyBlocksAway(GameTestHelper helper) {
        BlockPos light = new BlockPos(0, 2, 2);
        BlockPos spot = new BlockPos(20, 2, 2);
        placeSearchlight(helper, light, Direction.EAST);
        helper.setBlock(spot.east(), Blocks.STONE);

        helper.succeedWhen(() -> {
            helper.assertTrue(isBeamLight(helper, spot), "no beam light in front of the wall yet");
            int level = helper.getBlockState(spot).getValue(BeamLightBlock.LEVEL);
            int measured = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(spot));
            helper.assertTrue(level >= 12 && measured >= 12,
                    "expected light 12 or more at the spot, block level " + level + ", measured " + measured);
        });
    }

    /** S1: re-aiming moves the light in the same or the next tick. */
    @GameTest(template = EMPTY)
    public static void s1ReAimingMovesTheLightWithinOneTick(GameTestHelper helper) {
        BlockPos light = new BlockPos(0, 2, 2);
        BlockPos eastSpot = new BlockPos(3, 2, 2);
        BlockPos southSpot = new BlockPos(0, 2, 3);
        placeSearchlight(helper, light, Direction.EAST);
        helper.setBlock(eastSpot.east(), Blocks.STONE);
        helper.setBlock(southSpot.south(), Blocks.STONE);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(isBeamLight(helper, eastSpot), "no light on the east wall"))
                .thenExecute(() -> placeSearchlight(helper, light, Direction.SOUTH))
                .thenExecuteAfter(1, () -> {
                    helper.assertTrue(isBeamLight(helper, southSpot), "light did not move to the south wall within 1 tick");
                    helper.assertFalse(isBeamLight(helper, eastSpot), "old light on the east wall was left behind");
                })
                .thenSucceed();
    }

    /** A block placed into the beam takes the light within the re-trace interval. */
    @GameTest(template = EMPTY)
    public static void blockPlacedInTheBeamMovesTheLight(GameTestHelper helper) {
        BlockPos light = new BlockPos(0, 2, 2);
        placeSearchlight(helper, light, Direction.EAST);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.STONE);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(isBeamLight(helper, new BlockPos(3, 2, 2)), "no light at x=3"))
                .thenExecute(() -> helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE))
                .thenWaitUntil(() -> {
                    helper.assertTrue(isBeamLight(helper, new BlockPos(1, 2, 2)), "light did not move in front of the new block");
                    helper.assertFalse(isBeamLight(helper, new BlockPos(3, 2, 2)), "old light was left behind");
                })
                .thenSucceed();
    }

    /** Breaking the Searchlight removes its light. */
    @GameTest(template = EMPTY)
    public static void breakingTheSearchlightRemovesItsLight(GameTestHelper helper) {
        BlockPos light = new BlockPos(0, 2, 2);
        BlockPos spot = new BlockPos(3, 2, 2);
        placeSearchlight(helper, light, Direction.EAST);
        helper.setBlock(spot.east(), Blocks.STONE);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(isBeamLight(helper, spot), "no light yet"))
                .thenExecute(() -> helper.destroyBlock(light))
                .thenExecuteAfter(1, () -> helper.assertFalse(isBeamLight(helper, spot), "light outlived its Searchlight"))
                .thenSucceed();
    }

    /** A beam light whose owner is gone removes itself on its next check. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void abandonedBeamLightRemovesItself(GameTestHelper helper) {
        BlockPos spot = new BlockPos(2, 2, 2);
        helper.setBlock(spot, LightworksBlocks.BEAM_LIGHT.getDefaultState());
        helper.succeedWhen(() -> helper.assertFalse(isBeamLight(helper, spot), "abandoned beam light is still there"));
    }

    /** S10: an observer watching the spot never fires while the light appears there. */
    @GameTest(template = EMPTY)
    public static void s10ObserverDoesNotNoticeTheBeamLight(GameTestHelper helper) {
        BlockPos light = new BlockPos(0, 2, 2);
        BlockPos spot = new BlockPos(3, 2, 2);
        BlockPos observer = spot.above();
        helper.setBlock(spot.east(), Blocks.STONE);
        helper.setBlock(observer, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.DOWN));
        boolean[] watching = {false};
        helper.onEachTick(() -> {
            BlockState state = helper.getBlockState(observer);
            if (watching[0] && state.getValue(ObserverBlock.POWERED)) {
                helper.fail("observer fired when the beam light appeared", observer);
            }
        });

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    watching[0] = true;
                    placeSearchlight(helper, light, Direction.EAST);
                })
                .thenWaitUntil(() -> helper.assertTrue(isBeamLight(helper, spot), "no light yet"))
                .thenIdle(10)
                .thenSucceed();
    }
}
