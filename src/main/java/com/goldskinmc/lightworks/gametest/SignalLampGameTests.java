package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.signal.EdgeLightBlock;
import com.goldskinmc.lightworks.content.signal.NavigationColour;
import com.goldskinmc.lightworks.content.signal.NavigationLightBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlockEntity;
import com.goldskinmc.lightworks.content.signal.WarningPattern;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Signal lamps: block light when placed, dimmed by redstone and blinking with a Warning Light's pattern;
 * Navigation Light colours from placement.
 */
@GameTestHolder(Lightworks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SignalLampGameTests {
    private static final String EMPTY = "empty";

    private SignalLampGameTests() {
    }

    /** A placed Warning Light gives block light 12, and a full signal takes it away. */
    @GameTest(template = EMPTY, timeoutTicks = 1000)
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

    /** A blinking Warning Light's block light goes off while its lens is off and back on after. */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void blinkingWarningLightBlinksItsLight(GameTestHelper helper) {
        BlockPos lamp = new BlockPos(2, 1, 2);
        helper.setBlock(lamp, LightworksBlocks.WARNING_LIGHT.getDefaultState().setValue(SignalLampBlock.FACING, Direction.UP));
        if (helper.getBlockEntity(lamp) instanceof SignalLampBlockEntity entity) {
            entity.setPattern(WarningPattern.BLINK);
        }
        Set<Integer> seen = new HashSet<>();
        helper.onEachTick(() -> seen.add(helper.getBlockState(lamp).getValue(SignalLampBlock.LIGHT)));
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(seen.contains(0) && seen.contains(12), "expected the light to go 12 and 0, saw " + seen);
            helper.succeed();
        });
    }

    /** Opposite Blink is lit exactly when Blink is not, and a strobe flashes once a second without relighting. */
    @GameTest(template = EMPTY)
    public static void patternsKeepTheirTiming(GameTestHelper helper) {
        for (long t = 0; t < 100; t++) {
            helper.assertTrue(WarningPattern.BLINK.at(t, 0) + WarningPattern.OPPOSITE_BLINK.at(t, 0) == 1,
                    "blink and opposite blink should take turns at tick " + t);
            helper.assertTrue(WarningPattern.STROBE.blockLight(t) == WarningPattern.STROBE.blockLight(0),
                    "a strobe's block light should stay steady");
        }
        int flashes = 0;
        for (long t = 0; t < 200; t++) {
            if (WarningPattern.STROBE.at(t, 0) > 0 && WarningPattern.STROBE.at(t + 19, 0) == 0) {
                flashes++;
            }
        }
        helper.assertTrue(flashes == 10, "expected 10 flashes in 10 seconds, counted " + flashes);
        helper.succeed();
    }

    /** Night only: dark by day, lit by night, and the board's lower row sets it; it is saved and synced. */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void nightOnlyLampFollowsTheClock(GameTestHelper helper) {
        BlockPos lamp = new BlockPos(2, 1, 2);
        helper.setBlock(lamp, LightworksBlocks.WARNING_LIGHT.getDefaultState().setValue(SignalLampBlock.FACING, Direction.UP));
        SignalLampBlockEntity entity = helper.getBlockEntity(lamp);
        entity.getBehaviour(ScrollValueBehaviour.TYPE).setValueSettings(null,
                new ValueSettingsBehaviour.ValueSettings(1, WarningPattern.STEADY.ordinal()), false);
        helper.assertTrue(entity.lightState().nightOnly(), "the lower row should set night only");

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BlockEntity loaded = BlockEntity.loadStatic(entity.getBlockPos(), entity.getBlockState(),
                entity.saveWithFullMetadata(registries), registries);
        helper.assertTrue(loaded instanceof SignalLampBlockEntity copy && copy.lightState().nightOnly(), "night only lost when saved");
        SignalLampBlockEntity client = new SignalLampBlockEntity(entity.getType(), entity.getBlockPos(), entity.getBlockState());
        client.handleUpdateTag(entity.getUpdateTag(registries), registries);
        helper.assertTrue(client.lightState().nightOnly(), "night only missing from the client update");

        long before = helper.getLevel().getDayTime();
        helper.startSequence()
                .thenExecute(() -> helper.getLevel().setDayTime(1000))
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(lamp).getValue(SignalLampBlock.LIGHT) == 0,
                        "a night-only lamp should be dark by day"))
                .thenExecute(() -> helper.getLevel().setDayTime(18000))
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(lamp).getValue(SignalLampBlock.LIGHT) == 12,
                        "a night-only lamp should shine at night"))
                .thenExecute(() -> helper.getLevel().setDayTime(before))
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

    /** Placed by the hundred: an Edge Light never ticks, and nothing collides with it. */
    @GameTest(template = EMPTY)
    public static void edgeLightCostsNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, LightworksBlocks.EDGE_LIGHT.getDefaultState());
        BlockState state = helper.getBlockState(pos);
        BlockEntity entity = helper.getBlockEntity(pos);
        helper.assertTrue(state.getTicker(helper.getLevel(), entity.getType()) == null, "an Edge Light should not tick");
        helper.assertTrue(state.getCollisionShape(helper.getLevel(), helper.absolutePos(pos)).isEmpty(),
                "an Edge Light should not collide");
        helper.assertFalse(state.getShape(helper.getLevel(), helper.absolutePos(pos)).isEmpty(),
                "an Edge Light should still be selectable");
        helper.succeed();
    }

    /** It has no collision, which alone would leave it behind: a contraption takes it along, held by its mount. */
    @GameTest(template = EMPTY)
    public static void edgeLightMovesWithContraptions(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockState state = LightworksBlocks.EDGE_LIGHT.getDefaultState().setValue(EdgeLightBlock.FACING, Direction.EAST);
        helper.setBlock(pos, state);
        BlockPos world = helper.absolutePos(pos);
        helper.assertTrue(BlockMovementChecks.isMovementNecessary(state, helper.getLevel(), world),
                "a contraption should take an Edge Light along");
        helper.assertTrue(BlockMovementChecks.isBlockAttachedTowards(state, helper.getLevel(), world, Direction.WEST),
                "an Edge Light should hang on its mount");
        helper.assertFalse(BlockMovementChecks.isBlockAttachedTowards(state, helper.getLevel(), world, Direction.EAST),
                "an Edge Light should only hang on its mount");
        helper.succeed();
    }

    /** Redstone dims it and a full signal turns it off, in its block state and block light. */
    @GameTest(template = EMPTY)
    public static void edgeLightDimsWithRedstone(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, LightworksBlocks.EDGE_LIGHT.getDefaultState());
        helper.assertTrue(helper.getBlockState(pos).getLightEmission(helper.getLevel(), helper.absolutePos(pos)) == 4,
                "expected block light 4 at full brightness");
        helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(pos).getValue(EdgeLightBlock.BRIGHTNESS) == 0,
                        "a full signal should turn it off"))
                .thenExecute(() -> helper.assertTrue(
                        helper.getBlockState(pos).getLightEmission(helper.getLevel(), helper.absolutePos(pos)) == 0,
                        "an Edge Light that is off should give no block light"))
                .thenExecute(() -> helper.setBlock(pos.north(), Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(pos).getValue(EdgeLightBlock.BRIGHTNESS) == 15,
                        "it should come back on"))
                .thenSucceed();
    }

    /** Dye changes its colour and uses one up; the same colour again changes nothing. */
    @GameTest(template = EMPTY)
    public static void edgeLightTakesDye(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, LightworksBlocks.EDGE_LIGHT.getDefaultState());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLUE_DYE, 2));
        helper.useBlock(pos, player);
        helper.assertTrue(helper.getBlockState(pos).getValue(EdgeLightBlock.COLOUR) == DyeColor.BLUE, "dye did not colour it");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "dyeing should use up one dye");
        helper.useBlock(pos, player);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "the same colour should not use up dye");
        helper.succeed();
    }
}
