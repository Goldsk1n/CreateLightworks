package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.light.BeamLightBlock;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server light-block path (the no-client-mods fallback): the light lands where a fixed Searchlight points,
 * follows it, cleans up after it, and stays invisible to redstone.
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
        helper.setBlock(pos, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, facing));
    }

    private static boolean isBeamLight(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).is(LightworksBlocks.BEAM_LIGHT.get());
    }

    /**
     * Aimed at a wall 20 blocks away, the spot in front of the wall reaches light level 12 or more.
     * The game test server does not wait between ticks (4-7 ms each, not 50) while the light engine works
     * in real time on its own thread: measured up to 639 ms, 66 ticks. So the default 100 ticks are too few.
     */
    @GameTest(template = EMPTY_LONG, timeoutTicks = 1000)
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

    /** Re-aiming moves the light in the same or the next tick. */
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

    /**
     * A beam shining through water onto a wall lights the water in front of it: the light block goes into the water source,
     * waterlogged, and keeps it a source. A channel of stone, one block wide, keeps the water from spreading: the lamp
     * closes one end, the wall the other.
     */
    @GameTest(template = EMPTY, timeoutTicks = 1000)
    public static void beamThroughWaterLightsInFrontOfTheWall(GameTestHelper helper) {
        BlockPos spot = waterChannel(helper);
        helper.succeedWhen(() -> {
            BlockState state = helper.getBlockState(spot);
            helper.assertTrue(state.is(LightworksBlocks.BEAM_LIGHT.get()), "no beam light in the water yet");
            helper.assertTrue(state.getValue(BeamLightBlock.WATERLOGGED), "the beam light is not waterlogged");
            helper.assertTrue(state.getFluidState().isSource(), "the water there is no longer a source");
            int measured = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(spot));
            helper.assertTrue(measured >= 12, "expected light 12 or more in the water, measured " + measured);
        });
    }

    /** Breaking the Searchlight gives the water its light block stood in back, rather than leaving air. */
    @GameTest(template = EMPTY, timeoutTicks = 1000)
    public static void breakingTheSearchlightGivesTheWaterBack(GameTestHelper helper) {
        BlockPos spot = waterChannel(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(isBeamLight(helper, spot), "no beam light in the water yet"))
                .thenExecute(() -> helper.destroyBlock(new BlockPos(0, 2, 2)))
                .thenExecuteAfter(1, () -> {
                    BlockState state = helper.getBlockState(spot);
                    helper.assertTrue(state.is(Blocks.WATER) && state.getFluidState().isSource(),
                            "expected a water source where the light was, found " + state);
                })
                .thenSucceed();
    }

    /** A Searchlight at (0, 2, 2) shining east down a stone channel onto a wall at x = 4, a water source in front of it. */
    private static BlockPos waterChannel(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 3), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.STONE);
        BlockPos spot = new BlockPos(3, 2, 2);
        helper.setBlock(spot, Blocks.WATER);
        placeSearchlight(helper, new BlockPos(0, 2, 2), Direction.EAST);
        return spot;
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

    /**
     * A wide beam through a hole in a wall: the wall round the hole stays lit at full strength, and the light
     * through the hole lands behind it, as a real floodlight's would. Before, the beam's one light left the wall.
     */
    @GameTest(template = EMPTY_LONG, timeoutTicks = 200)
    public static void floodlightLightsARoundAHole(GameTestHelper helper) {
        BlockPos light = new BlockPos(0, 2, 2);
        for (int y = 0; y < 5; y++) {
            for (int z = 0; z < 5; z++) {
                if (y != 2 || z != 2) {
                    helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
                }
            }
        }
        BlockPos behind = new BlockPos(12, 2, 2);
        helper.setBlock(behind, Blocks.STONE);
        helper.setBlock(light, LightworksBlocks.FLOODLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.EAST));
        helper.succeedWhen(() -> {
            helper.assertTrue(isBeamLight(helper, behind.west()), "no light behind the hole");
            int onWall = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(5, 0, 0), new BlockPos(5, 4, 4))) {
                if (isBeamLight(helper, pos) && helper.getBlockState(pos).getValue(BeamLightBlock.LEVEL) == 15) {
                    onWall++;
                }
            }
            helper.assertTrue(onWall >= 2, "expected the wall round the hole to stay lit at full strength, found " + onWall);
        });
    }

    /** A level wide beam: its lower rays stop on the ground early, yet a block off every ray is lit where it stands. */
    @GameTest(template = EMPTY_LONG, timeoutTicks = 1000)
    public static void blockBetweenAWideBeamsRaysIsLit(GameTestHelper helper) {
        BlockPos light = levelFloodlightOverFloor(helper);
        BlockPos stone = new BlockPos(11, 1, 0);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(stone.south(), Blocks.AIR);
        helper.succeedWhen(() -> {
            int level = LightworksGameTests.blockLight(helper, stone.west());
            helper.assertTrue(level >= 6, "a block in the beam off its rays has light " + level);
            helper.assertTrue(helper.getBlockState(light).is(LightworksBlocks.FLOODLIGHT.get()), "the light went");
        });
    }

    /** The light goes only on what the lens sees: the ground behind a wall inside the cone stays dark. */
    @GameTest(template = EMPTY_LONG, timeoutTicks = 200)
    public static void wideBeamPlacesNoLightBehindAWall(GameTestHelper helper) {
        levelFloodlightOverFloor(helper);
        for (int y = 1; y < 5; y++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(isBeamLight(helper, new BlockPos(3, 2, 2))
                        || isBeamLight(helper, new BlockPos(3, 1, 2)) || isBeamLight(helper, new BlockPos(3, 3, 2)),
                        "no light on the wall"))
                .thenExecuteAfter(60, () -> {
                    for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(5, 1, 0), new BlockPos(14, 4, 4))) {
                        helper.assertFalse(isBeamLight(helper, pos), "light behind the wall at " + pos);
                    }
                })
                .thenSucceed();
    }

    /** With nothing changing, every search of the area must keep the lights where they are: moving them shows as a flicker. */
    @GameTest(template = EMPTY_LONG, timeoutTicks = 300)
    public static void wideBeamsLightsStayPutWhenNothingChanges(GameTestHelper helper) {
        levelFloodlightOverFloor(helper);
        Set<BlockPos> first = new HashSet<>();
        helper.startSequence()
                .thenExecuteAfter(60, () -> first.addAll(lightsOverFloor(helper)))
                .thenExecuteAfter(40, () -> helper.assertTrue(first.equals(lightsOverFloor(helper)), "lights moved at a search"))
                .thenExecuteAfter(40, () -> helper.assertTrue(first.equals(lightsOverFloor(helper)), "lights moved at a search"))
                .thenExecuteAfter(40, () -> helper.assertTrue(first.equals(lightsOverFloor(helper)), "lights moved at a search"))
                .thenExecute(() -> helper.assertTrue(first.size() > 1, "no lights to watch"))
                .thenSucceed();
    }

    private static Set<BlockPos> lightsOverFloor(GameTestHelper helper) {
        Set<BlockPos> found = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(14, 4, 4))) {
            if (isBeamLight(helper, pos)) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    private static BlockPos levelFloodlightOverFloor(GameTestHelper helper) {
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        BlockPos light = new BlockPos(0, 1, 2);
        helper.setBlock(light, LightworksBlocks.FLOODLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
        ((DirectionalLightBlockEntity) helper.getBlockEntity(light)).aimAlong(new Vec3(1, 0, 0));
        return light;
    }

    /** All of a wide beam's lights go when it is broken, and are saved with it. */
    @GameTest(template = EMPTY_LONG, timeoutTicks = 200)
    public static void floodlightKeepsAndRemovesAllItsLights(GameTestHelper helper) {
        BlockPos light = new BlockPos(0, 2, 2);
        for (int y = 0; y < 5; y++) {
            for (int z = 0; z < 5; z++) {
                // Solid, this near a wall the rays land too close together and share one light
                if (y != 2 || z != 2) {
                    helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(light, LightworksBlocks.FLOODLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.EAST));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(beamLights(helper).size() >= 2, "expected several lights"))
                .thenExecute(() -> {
                    DirectionalLightBlockEntity floodlight = helper.getBlockEntity(light);
                    HolderLookup.Provider registries = helper.getLevel().registryAccess();
                    BlockEntity loaded = BlockEntity.loadStatic(floodlight.getBlockPos(), floodlight.getBlockState(),
                            floodlight.saveWithFullMetadata(registries), registries);
                    for (BlockPos pos : beamLights(helper)) {
                        helper.assertTrue(loaded instanceof DirectionalLightBlockEntity copy
                                && copy.ownsBeamLight(helper.absolutePos(pos)), "light at " + pos + " forgotten when saved");
                    }
                })
                .thenExecute(() -> helper.destroyBlock(light))
                .thenExecuteAfter(1, () -> helper.assertTrue(beamLights(helper).isEmpty(), "lights outlived their Floodlight"))
                .thenSucceed();
    }

    private static List<BlockPos> beamLights(GameTestHelper helper) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 0, 0), new BlockPos(5, 4, 4))) {
            if (isBeamLight(helper, pos)) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    /** A beam light whose owner is gone removes itself on its next check. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void abandonedBeamLightRemovesItself(GameTestHelper helper) {
        BlockPos spot = new BlockPos(2, 2, 2);
        helper.setBlock(spot, LightworksBlocks.BEAM_LIGHT.getDefaultState());
        helper.succeedWhen(() -> helper.assertFalse(isBeamLight(helper, spot), "abandoned beam light is still there"));
    }

    /** An observer watching the spot never fires while the light appears there. */
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
