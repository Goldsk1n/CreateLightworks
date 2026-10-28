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
}
