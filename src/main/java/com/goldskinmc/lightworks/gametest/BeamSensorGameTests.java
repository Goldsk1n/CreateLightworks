package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.content.carried.CarriedSensing;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.content.sensor.BeamSensorBlock;
import com.goldskinmc.lightworks.content.sensor.BeamSensorBlockEntity;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.light.BeamSensors;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Beam Sensor: a Searchlight aimed at its lens gives a signal, and the block it hangs on is powered; a block in
 * between, a lens turned away, or a dye of another colour gives none; it goes dark once the beam is gone; a Flashlight
 * pointed at it lights it too.
 */
@GameTestHolder(Lightworks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BeamSensorGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos LIGHT = new BlockPos(0, 2, 2);
    private static final BlockPos SENSOR = new BlockPos(4, 2, 2);

    private BeamSensorGameTests() {
    }

    /** A Searchlight at the west end shining east, level. */
    private static DirectionalLightBlockEntity searchlight(GameTestHelper helper, DyeColor colour) {
        helper.setBlock(LIGHT, LightworksBlocks.SEARCHLIGHT.getDefaultState()
                .setValue(DirectionalLightBlock.FACING, Direction.EAST));
        DirectionalLightBlockEntity light = helper.getBlockEntity(LIGHT);
        if (colour != null) {
            light.lightState().setColour(colour);
            light.setChanged();
        }
        return light;
    }

    private static void sensor(GameTestHelper helper, Direction lens, DyeColor colour) {
        helper.setBlock(SENSOR, LightworksBlocks.BEAM_SENSOR.getDefaultState().setValue(BeamSensorBlock.FACING, lens));
        if (colour != null) {
            BeamSensorBlockEntity sensor = helper.getBlockEntity(SENSOR);
            sensor.lightState().setColour(colour);
            sensor.setChanged();
        }
    }

    private static int power(GameTestHelper helper) {
        return helper.getBlockState(SENSOR).getValue(BeamSensorBlock.POWER);
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aSearchlightOnTheLensGivesASignal(GameTestHelper helper) {
        searchlight(helper, null);
        sensor(helper, Direction.WEST, null);
        helper.succeedWhen(() -> {
            helper.assertTrue(power(helper) >= 10, "signal " + power(helper) + " from a Searchlight 4 blocks off");
            helper.assertTrue(helper.getLevel().getBestNeighborSignal(helper.absolutePos(SENSOR.west())) > 0,
                    "the sensor does not power its neighbour");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aBlockInBetweenCutsTheBeam(GameTestHelper helper) {
        searchlight(helper, null);
        sensor(helper, Direction.WEST, null);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(power(helper) == 0, "signal " + power(helper) + " through a stone");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aLensTurnedAwaySensesNothing(GameTestHelper helper) {
        searchlight(helper, null);
        sensor(helper, Direction.EAST, null);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(power(helper) == 0, "signal " + power(helper) + " on a lens facing away");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aDyedSensorSensesOnlyItsColour(GameTestHelper helper) {
        searchlight(helper, null);
        sensor(helper, Direction.WEST, DyeColor.RED);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(power(helper) == 0, "a red sensor gave " + power(helper) + " for a white beam");
            DirectionalLightBlockEntity light = helper.getBlockEntity(LIGHT);
            light.lightState().setColour(DyeColor.RED);
            light.setChanged();
            helper.succeedWhen(() -> helper.assertTrue(power(helper) > 0, "a red sensor gave nothing for a red beam"));
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void itGoesDarkOnceTheBeamIsGone(GameTestHelper helper) {
        searchlight(helper, null);
        sensor(helper, Direction.WEST, null);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(power(helper) > 0, "no signal to start with");
            helper.setBlock(LIGHT, Blocks.AIR);
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(power(helper) == 0, "signal " + power(helper) + " after the light was broken");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aFlashlightPointedAtItLightsIt(GameTestHelper helper) {
        sensor(helper, Direction.WEST, null);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 eye = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2)));
        // Facing east (yaw -90), level
        player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, -90, 0);
        // Where a player looks is where its head turns
        player.setYHeadRot(-90);
        player.yHeadRotO = -90;
        // A mock player does not tick: the shine by hand each tick, as the player tick does for a lit lamp
        helper.succeedWhen(() -> {
            CarriedSensing.shine(player, LightType.FLASHLIGHT, null);
            helper.assertTrue(power(helper) > 0, "no signal from a Flashlight pointed at the lens");
        });
    }

    /**
     * A beam turning fast sweeps past the sensor between two ticks, pointing 15 degrees to one side of it, then 15 to the
     * other: the arc between is checked, so the sensor lights (a beacon at 128 RPM was caught half the time, Nov 22).
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aBeamSweepingPastBetweenTicksIsSensed(GameTestHelper helper) {
        sensor(helper, Direction.WEST, null);
        Object light = new Object();
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(LIGHT));
        long[] start = new long[1];
        helper.runAfterDelay(5, () -> start[0] = helper.getLevel().getGameTime());
        helper.onEachTick(() -> {
            long tick = helper.getLevel().getGameTime() - start[0];
            if (start[0] == 0 || tick < 1 || tick > 2) {
                return;
            }
            double yaw = Math.toRadians(tick == 1 ? 15 : -15);
            Vec3 direction = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            BeamSensors.shine(helper.getLevel(), light, new BeamPose(origin, direction, true),
                    LightType.SEARCHLIGHT.beam(), LightType.SEARCHLIGHT.lensHalf(), 15, null);
        });
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(power(helper) > 0, "a beam sweeping past between two ticks was not sensed");
            helper.succeed();
        });
    }
}
