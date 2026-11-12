package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.beam.Aim;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.light.BeamTrace;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless game tests, run by {@code ./gradlew runGameTestServer}. Positions are relative to the
 * test's structure; {@code empty} is a 5x5x5 air template in data/lightworks/structure.
 */
@GameTestHolder(Lightworks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightworksGameTests {
    private static final String EMPTY = "empty";

    private LightworksGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void createIsLoaded(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create"), "Create is not loaded");
        helper.succeed();
    }

    /** Sable Companion must answer safely whether or not Sable is installed. */
    @GameTest(template = EMPTY)
    public static void sableCompanionFindsNoSubLevelInPlainWorld(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.assertTrue(SableCompanion.INSTANCE.getContaining(helper.getLevel(), pos) == null,
                "a plain test structure should not be inside a Sable sub-level");
        helper.succeed();
    }

    /**
     * Reference for the light tests: block light around a source settles. It takes real time, and game
     * test ticks are short (see {@link BeamLightGameTests}), hence the long timeout.
     */
    @GameTest(template = EMPTY, timeoutTicks = 1000)
    public static void blockLightProbeReadsLightLevels(GameTestHelper helper) {
        BlockPos source = new BlockPos(2, 2, 2);
        helper.setBlock(source, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        helper.succeedWhen(() -> {
            int next = blockLight(helper, source.east());
            int twoAway = blockLight(helper, source.east(2));
            helper.assertTrue(next == 14 && twoAway == 13,
                    "expected light 14 then 13 next to a level-15 source, got " + next + " and " + twoAway);
        });
    }

    /**
     * A beam whose centre ray goes into the open still lights where its lower edge lands: a Floodlight just
     * above a stone floor, tilted 30 degrees up, places its light block over the floor (weaker than on axis).
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void beamEdgeLightsWhereTheCentreMisses(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        BlockPos light = new BlockPos(0, 1, 2);
        helper.setBlock(light.west(), Blocks.STONE);
        helper.setBlock(light, LightworksBlocks.FLOODLIGHT.getDefaultState()
                .setValue(DirectionalLightBlock.FACING, Direction.EAST));
        DirectionalLightBlockEntity floodlight = helper.getBlockEntity(light);
        // Yaw 0 leans toward the mount frame's R, which is up for a side mount
        floodlight.lightState().setAim(new Aim(0, 60));
        floodlight.setChanged();
        helper.succeedWhen(() -> {
            BeamTrace.Hit centre = BeamTrace.trace(helper.getLevel(), floodlight.beamOrigin(), floodlight.beamDirection(),
                    BeamTrace.range(LightType.FLOODLIGHT.beam()));
            helper.assertFalse(centre.hitBlock() && helper.absolutePos(new BlockPos(0, 0, 0)).getY() == centre.block().getY(),
                    "the centre ray should not land on the floor");
            int found = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(4, 3, 4))) {
                if (helper.getBlockState(pos).is(LightworksBlocks.BEAM_LIGHT.get())) {
                    found = helper.getBlockState(pos).getValue(LightBlock.LEVEL);
                }
            }
            helper.assertTrue(found > 0, "expected a light block over the floor where the beam's edge lands");
        });
    }

    /** The beam leaves the lens glass and stops at the first block in its way, not before or after. */
    @GameTest(template = EMPTY)
    public static void searchlightBeamStopsAtFirstSolidBlock(GameTestHelper helper) {
        BlockPos light = new BlockPos(0, 2, 2);
        BlockPos wall = new BlockPos(4, 2, 2);
        helper.setBlock(light, LightworksBlocks.SEARCHLIGHT.getDefaultState()
                .setValue(DirectionalLightBlock.FACING, Direction.EAST));
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(new BlockPos(4, 3, 2), Blocks.GLASS);

        DirectionalLightBlockEntity searchlight = helper.getBlockEntity(light);
        BeamTrace.Hit hit = BeamTrace.trace(helper.getLevel(), searchlight.beamOrigin(), searchlight.beamDirection(), 48);

        helper.assertTrue(helper.absolutePos(wall).equals(hit.block()),
                "beam should hit the wall at " + helper.absolutePos(wall) + " but hit " + hit.block());
        // The lens glass is lensDistance east of the block centre (x = 0.5), the wall's west face at x = 4.0
        double expected = 4.0 - (0.5 + LightType.SEARCHLIGHT.lensDistance());
        helper.assertTrue(Math.abs(hit.distance() - expected) < 1e-6,
                "expected " + expected + " blocks to the wall, got " + hit.distance());
        helper.succeed();
    }

    /**
     * With nothing in the way, the beam runs to its full range. The range stays inside the test area:
     * game tests wall the area in with barriers, which the beam would otherwise hit.
     */
    @GameTest(template = EMPTY)
    public static void searchlightBeamRunsFullRangeWhenNothingIsInTheWay(GameTestHelper helper) {
        BlockPos light = new BlockPos(2, 1, 2);
        helper.setBlock(light, LightworksBlocks.SEARCHLIGHT.getDefaultState()
                .setValue(DirectionalLightBlock.FACING, Direction.UP));
        DirectionalLightBlockEntity searchlight = helper.getBlockEntity(light);
        BeamTrace.Hit hit = BeamTrace.trace(helper.getLevel(), searchlight.beamOrigin(), searchlight.beamDirection(), 2);
        helper.assertFalse(hit.hitBlock(), "beam should not hit anything, hit " + hit.block());
        helper.assertTrue(Math.abs(hit.distance() - 2) < 1e-6, "expected the full 2-block range, got " + hit.distance());
        helper.succeed();
    }

    static int blockLight(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(relative));
    }
}
