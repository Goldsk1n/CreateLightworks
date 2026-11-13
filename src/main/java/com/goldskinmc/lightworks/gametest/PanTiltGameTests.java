package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.directional.AimBoard;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadBlock;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadBlockEntity;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
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

    /** A signal into the head tilts the light; it does not reach through the head and dim it as well. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void headSignalTiltsWithoutDimmingTheLight(GameTestHelper helper) {
        BlockPos head = new BlockPos(2, 1, 2);
        BlockPos light = head.above();
        helper.setBlock(head, LightworksBlocks.PAN_TILT_HEAD.getDefaultState().setValue(PanTiltHeadBlock.FACING, Direction.UP));
        helper.setBlock(light, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
        // A lever on the head's side powers the head hard, as dust aimed at it does
        helper.setBlock(head.east(), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.EAST).setValue(LeverBlock.POWERED, true));

        helper.startSequence()
                .thenWaitUntil(() -> {
                    PanTiltHeadBlockEntity pan = helper.getBlockEntity(head);
                    helper.assertTrue(pan.signal() == 15, "head reads signal " + pan.signal());
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    DirectionalLightBlockEntity searchlight = helper.getBlockEntity(light);
                    helper.assertTrue(searchlight.lightState().signal() == 0,
                            "light reads signal " + searchlight.lightState().signal() + " through the head");
                })
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

    /** Compass bearing of a level beam: clockwise from north, in degrees. */
    private static float bearing(DirectionalLightBlockEntity light) {
        Vec3 d = light.beamDirection();
        return (float) Math.toDegrees(Math.atan2(d.x, -d.z));
    }

    /** Heads placed by a player facing east, on the floor and on a ceiling, point their lights east; Pan reads 90. */
    @GameTest(template = EMPTY)
    public static void placedHeadPointsAwayFromThePlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setYRot(-90);
        for (Direction facing : new Direction[] {Direction.UP, Direction.DOWN}) {
            BlockPos head = facing == Direction.UP ? new BlockPos(1, 1, 1) : new BlockPos(3, 3, 3);
            BlockPos light = head.relative(facing);
            BlockState state = LightworksBlocks.PAN_TILT_HEAD.getDefaultState().setValue(PanTiltHeadBlock.FACING, facing);
            helper.setBlock(head, state);
            state.getBlock().setPlacedBy(helper.getLevel(), helper.absolutePos(head), state, player, ItemStack.EMPTY);
            helper.setBlock(light, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, facing));
            DirectionalLightBlockEntity searchlight = helper.getBlockEntity(light);
            PanTiltHeadBlockEntity entity = helper.getBlockEntity(head);
            helper.assertTrue(searchlight.beamDirection().distanceTo(new Vec3(1, 0, 0)) < 1e-6,
                    facing + " head placed facing east points " + searchlight.beamDirection());
            helper.assertTrue(entity.boardPan() == 90, facing + " head's Pan reads " + entity.boardPan());
        }
        helper.succeed();
    }

    /**
     * The light's Pan box turns a stopped head to its value at once, a sweep then centres there, and a head whose
     * shaft stops stays where it is.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void panBoxCentresTheSweep(GameTestHelper helper) {
        BlockPos motor = new BlockPos(2, 1, 2);
        BlockPos head = motor.above();
        BlockPos light = head.above();
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        helper.setBlock(head, LightworksBlocks.PAN_TILT_HEAD.getDefaultState().setValue(PanTiltHeadBlock.FACING, Direction.UP));
        helper.setBlock(light, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
        PanTiltHeadBlockEntity entity = helper.getBlockEntity(head);
        DirectionalLightBlockEntity searchlight = helper.getBlockEntity(light);
        float[] range = {Float.MAX_VALUE, -Float.MAX_VALUE};
        boolean[] sweeping = {false};
        float[] stoppedAt = {0};
        helper.onEachTick(() -> {
            if (sweeping[0]) {
                float b = bearing(searchlight);
                range[0] = Math.min(range[0], b);
                range[1] = Math.max(range[1], b);
            }
        });
        // A Creative Motor starts at 16 rpm: stop it, so the Pan box turns a stopped head
        setSpeed(helper, motor, 0);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(entity.getSpeed() == 0, "head still has speed"))
                .thenExecute(() -> {
                    searchlight.getBehaviour(AimBoard.PAN).setValueSettings(null, new ValueSettings(1, 90), false);
                    helper.assertFalse(entity.turning(), "a head set by its Pan box counts as turning");
                    helper.assertTrue(Math.abs(bearing(searchlight) - 90) < 0.01, "head turned to " + bearing(searchlight));
                })
                .thenExecute(() -> {
                    entity.getBehaviour(ScrollValueBehaviour.TYPE).setValue(9);
                    setSpeed(helper, motor, 32);
                    sweeping[0] = true;
                })
                .thenIdle(120)
                .thenExecute(() -> {
                    sweeping[0] = false;
                    helper.assertTrue(range[0] >= 44.99f && range[1] <= 135.01f,
                            "sweep left its arc round east: " + range[0] + " to " + range[1]);
                    helper.assertTrue(range[0] < 50 && range[1] > 130, "sweep missed an end: " + range[0] + " to " + range[1]);
                    setSpeed(helper, motor, 0);
                })
                .thenWaitUntil(() -> helper.assertTrue(entity.getSpeed() == 0, "head still has speed"))
                // At partial tick 0 a head that has ticked reads its previous angle: let the last step land first
                .thenExecuteAfter(2, () -> stoppedAt[0] = bearing(searchlight))
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(Math.abs(bearing(searchlight) - stoppedAt[0]) < 0.01,
                        "stopped head moved from " + stoppedAt[0] + " to " + bearing(searchlight)))
                .thenSucceed();
    }
}
