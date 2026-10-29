package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
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

    /** Reference for the light scenarios: block light around a source settles within a few ticks. */
    @GameTest(template = EMPTY)
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
