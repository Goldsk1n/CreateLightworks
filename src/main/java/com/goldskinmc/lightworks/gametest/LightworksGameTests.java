package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
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

    static int blockLight(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(relative));
    }
}
