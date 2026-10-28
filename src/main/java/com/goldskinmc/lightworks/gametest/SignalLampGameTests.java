package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.signal.NavigationColour;
import com.goldskinmc.lightworks.content.signal.NavigationLightBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlock;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Signal lamps: steady block light when placed, dimmed by redstone; Navigation Light colours from placement. */
@GameTestHolder(Lightworks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SignalLampGameTests {
    private static final String EMPTY = "empty";

    private SignalLampGameTests() {
    }

    /** A placed Warning Light gives block light 12, and a full signal takes it away. */
    @GameTest(template = EMPTY)
    public static void placedWarningLightGivesBlockLight(GameTestHelper helper) {
        BlockPos lamp = new BlockPos(2, 1, 2);
        helper.setBlock(lamp, LightworksBlocks.WARNING_LIGHT.getDefaultState().setValue(SignalLampBlock.FACING, Direction.UP));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    int beside = LightworksGameTests.blockLight(helper, lamp.east());
                    helper.assertTrue(helper.getBlockState(lamp).getValue(SignalLampBlock.LIGHT) == 12 && beside == 11,
                            "expected light 12 at the lamp and 11 beside it, got " + beside);
                })
                .thenExecute(() -> helper.setBlock(lamp.west(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(lamp).getValue(SignalLampBlock.LIGHT) == 0,
                        "powered lamp still gives light"))
                .thenSucceed();
    }

    /** Facing the placer's left is port (red), their right starboard (green), anything else stern (white). */
    @GameTest(template = EMPTY)
    public static void navigationColourFollowsPlacement(GameTestHelper helper) {
        helper.assertTrue(NavigationLightBlock.colourFor(Direction.WEST, Direction.NORTH) == NavigationColour.PORT,
                "facing left of a north-looking placer should be port");
        helper.assertTrue(NavigationLightBlock.colourFor(Direction.EAST, Direction.NORTH) == NavigationColour.STARBOARD,
                "facing right of a north-looking placer should be starboard");
        helper.assertTrue(NavigationLightBlock.colourFor(Direction.SOUTH, Direction.NORTH) == NavigationColour.STERN,
                "facing back at the placer should be stern");
        helper.assertTrue(NavigationLightBlock.colourFor(Direction.UP, Direction.EAST) == NavigationColour.STERN,
                "facing up should be stern");
        helper.assertTrue(NavigationLightBlock.colourFor(Direction.NORTH, Direction.EAST) == NavigationColour.PORT,
                "facing left of an east-looking placer should be port");
        helper.succeed();
    }
}
