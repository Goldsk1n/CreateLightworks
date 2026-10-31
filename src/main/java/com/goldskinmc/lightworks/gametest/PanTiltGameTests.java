package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadBlock;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadBlockEntity;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Pan-Tilt Head turns the light on top with its shaft, and hands it back to the fixed path when stopped. */
@GameTestHolder(Lightworks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PanTiltGameTests {
    private static final String EMPTY = "empty";

    private PanTiltGameTests() {
    }

    private static void setSpeed(GameTestHelper helper, BlockPos motor, int rpm) {
        if (helper.getBlockEntity(motor) instanceof SmartBlockEntity creative) {
            ScrollValueBehaviour speed = creative.getBehaviour(ScrollValueBehaviour.TYPE);
            if (speed != null) {
                speed.setValue(rpm);
            }
        }
    }

    private static int beamLights(GameTestHelper helper) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(0, 0, 0, 4, 4, 4)) {
            if (helper.getBlockState(pos).is(LightworksBlocks.BEAM_LIGHT.get())) {
                count++;
            }
        }
        return count;
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void headTurnsTheLightAndFixesItWhenStopped(GameTestHelper helper) {
        BlockPos motor = new BlockPos(2, 1, 2);
        BlockPos head = motor.above();
        BlockPos light = head.above();
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        helper.setBlock(head, LightworksBlocks.PAN_TILT_HEAD.getDefaultState().setValue(PanTiltHeadBlock.FACING, Direction.UP));
        helper.setBlock(light, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
        setSpeed(helper, motor, 32);
        Vec3[] before = new Vec3[1];

        helper.startSequence()
                .thenWaitUntil(() -> {
                    PanTiltHeadBlockEntity pan = helper.getBlockEntity(head);
                    helper.assertTrue(pan.getSpeed() != 0, "head is not turning");
                })
                .thenExecute(() -> before[0] = ((DirectionalLightBlockEntity) helper.getBlockEntity(light)).beamDirection())
                .thenExecuteAfter(5, () -> {
                    DirectionalLightBlockEntity searchlight = helper.getBlockEntity(light);
                    helper.assertTrue(searchlight.turnedByHead(), "light does not know the head turns it");
                    helper.assertTrue(searchlight.beamDirection().distanceTo(before[0]) > 0.1,
                            "beam did not turn: " + before[0] + " then " + searchlight.beamDirection());
                    // Tilt 0 without a signal: the beam runs along the mount, here level
                    helper.assertTrue(Math.abs(searchlight.beamDirection().y) < 1e-6, "untilted beam is not level");
                    helper.assertTrue(beamLights(helper) == 0, "a turning light left a server light block");
                })
                .thenExecute(() -> setSpeed(helper, motor, 0))
                .thenWaitUntil(() -> helper.assertTrue(beamLights(helper) == 1, "stopped light has no server light block"))
                .thenSucceed();
    }

    /** A light's base turns with a head under it: the block state follows the head coming, going and turning away. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void lightKnowsWhenItIsOnAHead(GameTestHelper helper) {
        BlockPos head = new BlockPos(2, 1, 2);
        BlockPos light = head.above();
        // Placed directly rather than by a player, so the block entity has to find the head by itself
        helper.setBlock(head, LightworksBlocks.PAN_TILT_HEAD.getDefaultState().setValue(PanTiltHeadBlock.FACING, Direction.UP));
        helper.setBlock(light, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));

        helper.startSequence()
                .thenWaitUntil(() -> assertOnHead(helper, light, true))
                .thenExecute(() -> helper.setBlock(head, AllBlocks.BRASS_CASING.getDefaultState()))
                .thenExecute(() -> assertOnHead(helper, light, false))
                .thenExecute(() -> helper.setBlock(head, LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                        .setValue(PanTiltHeadBlock.FACING, Direction.UP)))
                .thenExecute(() -> assertOnHead(helper, light, true))
                // A head facing another way does not carry the light
                .thenExecute(() -> helper.setBlock(head, LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                        .setValue(PanTiltHeadBlock.FACING, Direction.NORTH)))
                .thenExecute(() -> assertOnHead(helper, light, false))
                .thenSucceed();
    }

    private static void assertOnHead(GameTestHelper helper, BlockPos light, boolean expected) {
        helper.assertTrue(helper.getBlockState(light).getValue(DirectionalLightBlock.ON_HEAD) == expected,
                "on_head should be " + expected);
    }

    /** With the box set the pan bounces between the arc's ends; set back to 0 it goes on round. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void headSweepsInsideItsArc(GameTestHelper helper) {
        BlockPos motor = new BlockPos(2, 1, 2);
        BlockPos head = motor.above();
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        helper.setBlock(head, LightworksBlocks.PAN_TILT_HEAD.getDefaultState().setValue(PanTiltHeadBlock.FACING, Direction.UP));
        PanTiltHeadBlockEntity entity = helper.getBlockEntity(head);
        entity.getBehaviour(ScrollValueBehaviour.TYPE).setValue(9);
        setSpeed(helper, motor, 32);
        float[] range = {0, 0};
        float[] round = {0};
        helper.onEachTick(() -> {
            float yaw = Mth.wrapDegrees(entity.yaw(0));
            if (entity.getBehaviour(ScrollValueBehaviour.TYPE).getValue() > 0) {
                range[0] = Math.min(range[0], yaw);
                range[1] = Math.max(range[1], yaw);
            } else {
                round[0] = Math.max(round[0], Math.abs(yaw));
            }
        });
        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(range[0] >= -45.01f && range[1] <= 45.01f,
                            "pan left the arc: " + range[0] + " to " + range[1]);
                    helper.assertTrue(range[0] < -40 && range[1] > 40, "pan missed an end: " + range[0] + " to " + range[1]);
                    entity.getBehaviour(ScrollValueBehaviour.TYPE).setValue(0);
                })
                .thenIdle(120)
                .thenExecute(() -> helper.assertTrue(round[0] > 170, "pan should go round again, got to " + round[0]))
                .thenSucceed();
    }
}
