package com.goldskinmc.lightworks.debug.selftest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.content.beacon.RotatingBeaconBlock;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadBlock;
import com.goldskinmc.lightworks.content.signal.NavigationLightBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlock;
import com.goldskinmc.lightworks.content.signal.WarningPattern;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.light.BeamTrace;
import com.goldskinmc.lightworks.debug.Timer;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.IControlContraption.MovementMode;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.PistonState;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** The self-test scenarios, by name. Each builds relative to where the player joined, at night. */
final class Scenarios {
    static final Map<String, Supplier<Script>> ALL = new LinkedHashMap<>();
    /** What {@code -Pselftest=true} runs. */
    static final String DEFAULT =
            "fixed,aim,floodlight,pantilt,beacon,warning,navigation,goggles,vehicle,bearing,piston,vehicle-fast";

    static {
        ALL.put("fixed", Scenarios::fixed);
        ALL.put("aim", Scenarios::aim);
        ALL.put("floodlight", Scenarios::floodlight);
        ALL.put("pantilt", Scenarios::panTilt);
        ALL.put("beacon", Scenarios::beacon);
        ALL.put("warning", Scenarios::warning);
        ALL.put("navigation", Scenarios::navigation);
        ALL.put("goggles", Scenarios::goggles);
        ALL.put("vehicle", () -> vehicle("vehicle", 0.1, 100, true));
        // Create's default train top speed, 28 blocks a second: how far the spot trails at train speeds
        ALL.put("vehicle-fast", () -> vehicle("vehicle-fast", 1.4, 40, false));
        ALL.put("bearing", Scenarios::bearing);
        ALL.put("piston", Scenarios::piston);
        // Not in the default set: heavier, or one-off
        ALL.put("load", Scenarios::load);
        ALL.put("cleanup", Scenarios::cleanup);
        ALL.put("ponder", Scenarios::ponder);
        ALL.put("gallery", Scenarios::gallery);
    }

    private Scenarios() {
    }

    static BlockState searchlight(Direction facing) {
        return LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, facing);
    }

    static BlockState headlight(Direction facing) {
        return LightworksBlocks.HEADLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, facing);
    }

    /**
     * S1 and S5, fixed light: a Searchlight shining 10 blocks east onto a stone wall, photographed dark,
     * with both light paths, with the dynamic light alone and with the server light blocks alone.
     */
    static Script fixed() {
        BlockPos[] light = new BlockPos[1];
        Vec3[] spot = new Vec3[1];
        return new Script("fixed")
                .run(stage -> {
                    stage.stand(0, 0);
                    stage.night();
                    light[0] = stage.at(1, 1, 2);
                    for (int x = 2; x < 12; x++) {
                        stage.set(stage.at(x, 1, 2), Blocks.AIR.defaultBlockState());
                    }
                    for (int y = 0; y <= 4; y++) {
                        for (int z = -1; z <= 5; z++) {
                            stage.set(stage.at(12, y, z), Blocks.STONE.defaultBlockState());
                        }
                    }
                    spot[0] = Vec3.atLowerCornerOf(stage.at(12, 1, 2)).add(0, 0.5, 0.5);
                    SelfTestBridge.focus = spot[0];
                })
                .waitTicks(40)
                .shot("fixed-0-dark")
                .run(stage -> stage.set(light[0], searchlight(Direction.EAST)))
                .waitTicks(40)
                .run(stage -> {
                    BlockPos air = stage.at(11, 1, 2);
                    stage.log("fixed: server block at spot %s, block light %d",
                            stage.level.getBlockState(air).getBlock().getDescriptionId(),
                            stage.level.getBrightness(LightLayer.BLOCK, air));
                })
                .shot("fixed-1-both-paths")
                .run(stage -> LightworksConfig.setServerLightBlocks(false))
                .waitTicks(40)
                .run(stage -> stage.log("fixed: server light blocks off, block light at spot %d",
                        stage.level.getBrightness(LightLayer.BLOCK, stage.at(11, 1, 2))))
                .shot("fixed-2-dynamic-only")
                .run(stage -> {
                    LightworksConfig.setServerLightBlocks(true);
                    SelfTestBridge.dynamicLightsOff = true;
                })
                .waitTicks(40)
                .shot("fixed-3-server-only")
                .run(stage -> {
                    SelfTestBridge.dynamicLightsOff = false;
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * Wrench aim: a Searchlight on the floor aimed by a Wrench click at a point high on a wall 12 blocks
     * away. The beam should land within a block of that point and light it.
     */
    static Script aim() {
        BlockPos[] light = new BlockPos[1];
        Vec3[] target = new Vec3[1];
        return new Script("aim")
                .run(stage -> {
                    stage.night();
                    stage.stand(-3, 2);
                    light[0] = stage.at(0, 0, 8);
                    for (int y = 0; y <= 6; y++) {
                        for (int z = 5; z <= 11; z++) {
                            stage.set(stage.at(12, y, z), Blocks.STONE.defaultBlockState());
                        }
                    }
                    stage.set(light[0], searchlight(Direction.UP));
                    // West face of the wall, four blocks up and two to the side
                    target[0] = Vec3.atLowerCornerOf(stage.at(12, 4, 10)).add(0, 0.5, 0.5);
                    SelfTestBridge.focus = target[0];
                })
                .waitTicks(10)
                .run(stage -> {
                    wrenchAim(stage, light[0], target[0]);
                    stage.log("aim: wrenched to %s", stage.lightAt(light[0]).aim());
                })
                .waitTicks(30)
                .run(stage -> {
                    DirectionalLightBlockEntity searchlight = stage.lightAt(light[0]);
                    BeamPose beam = searchlight.tickBeam();
                    BeamTrace.Hit hit = BeamTrace.trace(stage.level, beam.origin(), beam.direction(), 48);
                    BlockPos air = BlockPos.containing(hit.end().subtract(beam.direction().scale(0.5)));
                    stage.log("aim: beam hits %s at %.2f blocks, %.2f from the aimed point; block light there %d",
                            hit.block(), hit.distance(), hit.end().distanceTo(target[0]),
                            stage.level.getBrightness(LightLayer.BLOCK, air));
                })
                .shot("aim-0-wall")
                .run(stage -> {
                    SelfTestBridge.focus = Vec3.atCenterOf(light[0]);
                    stage.stand(-1, 6);
                })
                .waitTicks(5)
                .shot("aim-1-head")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * A Floodlight on the side of a pillar, wrenched to light the ground in front of it: a wide lit patch.
     * (On top of the pillar it could not point down: aim stays in the half-space in front of the mount.)
     */
    static Script floodlight() {
        BlockPos[] light = new BlockPos[1];
        Vec3[] target = new Vec3[1];
        return new Script("floodlight")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, -4);
                    for (int y = 0; y <= 4; y++) {
                        stage.set(stage.at(-7, y, 6), Blocks.STONE.defaultBlockState());
                    }
                    light[0] = stage.at(-6, 4, 6);
                    stage.set(light[0], LightworksBlocks.FLOODLIGHT.getDefaultState()
                            .setValue(DirectionalLightBlock.FACING, Direction.EAST));
                    // Top of the ground 6 blocks east of the pillar
                    target[0] = Vec3.atLowerCornerOf(stage.at(0, 0, 6)).add(0.5, 0, 0.5);
                    SelfTestBridge.focus = target[0];
                })
                .waitTicks(10)
                .run(stage -> wrenchAim(stage, light[0], target[0]))
                .waitTicks(40)
                .run(stage -> {
                    StringBuilder levels = new StringBuilder();
                    for (int dx = -6; dx <= 6; dx += 2) {
                        levels.append(' ').append(stage.level.getBrightness(LightLayer.BLOCK, stage.at(dx, 0, 6)));
                    }
                    stage.log("floodlight: aim %s, block light along the ground from x-6 to x+6:%s",
                            stage.lightAt(light[0]).aim(), levels);
                })
                .shot("floodlight-0-ground")
                .run(stage -> {
                    SelfTestBridge.focus = Vec3.atCenterOf(light[0]);
                    stage.stand(-4, 2);
                })
                .waitTicks(5)
                .shot("floodlight-1-head")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * A Searchlight hanging under a Pan-Tilt Head on a Creative Motor at 16 RPM, tilted 60 degrees down by
     * a signal of 10: the spot should circle the ground below about 4.3 blocks from the axis, sweeping
     * 4.8 degrees a tick. When the motor stops, the light becomes a fixed light and lights its spot through
     * a server light block.
     */
    static Script panTilt() {
        int rpm = 16;
        BlockPos[] at = new BlockPos[3];
        return new Script("pantilt")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, -4);
                    BlockPos motor = stage.at(0, 9, 8);
                    at[0] = motor;
                    at[1] = motor.below();
                    at[2] = motor.below(2);
                    stage.set(at[1], LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                            .setValue(PanTiltHeadBlock.FACING, Direction.DOWN));
                    stage.set(at[2], searchlight(Direction.DOWN));
                    // A pressure plate holding signal 10 beside the head: tilt 60 degrees. It stands on glass,
                    // which does not carry its signal on to the light below the head
                    stage.set(at[1].east().below(), Blocks.GLASS.defaultBlockState());
                    stage.set(at[1].east(), Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState()
                            .setValue(WeightedPressurePlateBlock.POWER, 10));
                    stage.set(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                            .setValue(CreativeMotorBlock.FACING, Direction.DOWN));
                    setMotorSpeed(stage, motor, rpm);
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 0, 8)).add(0.5, 0, 0.5);
                })
                .waitTicks(40)
                .shot("pantilt-0-turning")
                .run(stage -> {
                    SelfTestBridge.probeGroundY = stage.origin.getY();
                    SelfTestBridge.probeAxis = Vec3.atCenterOf(at[2]);
                    SelfTestBridge.probe = "pantilt";
                    SelfTestBridge.recording = "pantilt-sweep";
                })
                .waitTicks(80)
                .run(stage -> {
                    SelfTestBridge.recording = null;
                    SelfTestBridge.probe = null;
                    SelfTestBridge.probeAxis = null;
                    setMotorSpeed(stage, at[0], 0);
                })
                .waitTicks(40)
                .run(stage -> {
                    DirectionalLightBlockEntity light = stage.lightAt(at[2]);
                    BeamPose beam = light.tickBeam();
                    BeamTrace.Hit hit = BeamTrace.trace(stage.level, beam.origin(), beam.direction(), 48);
                    BlockPos air = BlockPos.containing(hit.end().subtract(beam.direction().scale(0.5)));
                    stage.log("pantilt: stopped, moving %s, beam hits %s, block light at the spot %d (%s)",
                            beam.moving(), hit.block(), stage.level.getBrightness(LightLayer.BLOCK, air),
                            stage.level.getBlockState(air).getBlock().getDescriptionId());
                })
                .shot("pantilt-1-stopped")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * A Rotating Beacon on a Creative Motor at 8 RPM inside a ring of walls 6 blocks out: each beam's spot
     * should go round the walls at 2.4 degrees a tick, half a turn apart. Stopped, both beams light their
     * walls through server light blocks.
     */
    static Script beacon() {
        int rpm = 8;
        BlockPos[] at = new BlockPos[2];
        return new Script("beacon")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, 0);
                    at[0] = stage.at(0, 0, 10);
                    at[1] = at[0].above();
                    for (int dx = -6; dx <= 6; dx++) {
                        for (int dz = -6; dz <= 6; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) == 6 && !(dz == -6 && Math.abs(dx) <= 1)) {
                                for (int y = 0; y <= 3; y++) {
                                    stage.set(at[0].offset(dx, y, dz), Blocks.STONE.defaultBlockState());
                                }
                            }
                        }
                    }
                    stage.set(at[1], LightworksBlocks.ROTATING_BEACON.getDefaultState()
                            .setValue(RotatingBeaconBlock.FACING, Direction.UP));
                    stage.set(at[0], AllBlocks.CREATIVE_MOTOR.getDefaultState()
                            .setValue(CreativeMotorBlock.FACING, Direction.UP));
                    setMotorSpeed(stage, at[0], rpm);
                    SelfTestBridge.focus = Vec3.atCenterOf(at[1]);
                })
                .waitTicks(40)
                .shot("beacon-0-turning")
                .run(stage -> {
                    SelfTestBridge.probeGroundY = stage.origin.getY();
                    SelfTestBridge.probeAxis = Vec3.atCenterOf(at[1]);
                    SelfTestBridge.probe = "beacon";
                    SelfTestBridge.recording = "beacon-sweep";
                })
                .waitTicks(80)
                .run(stage -> {
                    SelfTestBridge.recording = null;
                    SelfTestBridge.probe = null;
                    SelfTestBridge.probeAxis = null;
                    setMotorSpeed(stage, at[0], 0);
                })
                .waitTicks(40)
                .run(stage -> {
                    int lit = 0;
                    for (BlockPos pos : BlockPos.betweenClosed(at[0].offset(-6, 0, -6), at[0].offset(6, 3, 6))) {
                        if (stage.level.getBlockState(pos).is(LightworksBlocks.BEAM_LIGHT.get())) {
                            lit++;
                        }
                    }
                    stage.log("beacon: stopped, %d server light blocks inside the ring", lit);
                })
                .shot("beacon-1-stopped")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.discardContraptions(at[0], 8);
                    stage.restoreBlocks();
                });
    }

    /**
     * Four Warning Lights in a row, one per pattern (steady, blink, pulse, flash), filmed for two seconds.
     * Logs the block light beside each (steady whatever the pattern) and each pattern's lens over 30 ticks.
     */
    static Script warning() {
        BlockPos[] lamps = new BlockPos[4];
        return new Script("warning")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, 0);
                    for (int i = 0; i < 4; i++) {
                        lamps[i] = stage.at(-3 + 2 * i, 0, 4);
                        stage.set(lamps[i], LightworksBlocks.WARNING_LIGHT.getDefaultState()
                                .setValue(SignalLampBlock.FACING, Direction.UP));
                        if (stage.level.getBlockEntity(lamps[i]) instanceof SmartBlockEntity lamp) {
                            ScrollValueBehaviour pattern = lamp.getBehaviour(ScrollValueBehaviour.TYPE);
                            if (pattern != null) {
                                pattern.setValue(i);
                            }
                        }
                    }
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 0, 4));
                })
                .waitTicks(30)
                .run(stage -> {
                    StringBuilder light = new StringBuilder();
                    for (BlockPos lamp : lamps) {
                        light.append(' ').append(stage.level.getBrightness(LightLayer.BLOCK, lamp.south()));
                    }
                    stage.log("warning: block light beside each lamp:%s", light);
                    long t = stage.level.getGameTime();
                    for (WarningPattern pattern : WarningPattern.values()) {
                        StringBuilder lens = new StringBuilder();
                        for (int i = 0; i < 30; i++) {
                            lens.append(String.format(" %.1f", pattern.at(t + i, 0)));
                        }
                        stage.log("warning: %s lens over 30 ticks:%s", pattern, lens);
                    }
                    SelfTestBridge.recording = "warning-patterns";
                })
                .waitTicks(40)
                .run(stage -> SelfTestBridge.recording = null)
                .shot("warning-0")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * Three Navigation Lights placed the way a player does, looking north: on the west face of a block
     * (port, red), the east face (starboard, green) and the south face (stern, white).
     */
    static Script navigation() {
        BlockPos[] post = new BlockPos[1];
        return new Script("navigation")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, 0);
                    post[0] = stage.at(0, 1, -5);
                    stage.set(post[0].below(), Blocks.STONE.defaultBlockState());
                    stage.set(post[0], Blocks.STONE.defaultBlockState());
                    stage.player.setYRot(180);
                    StringBuilder colours = new StringBuilder();
                    for (Direction face : new Direction[] {Direction.WEST, Direction.EAST, Direction.SOUTH}) {
                        BlockPos pos = post[0].relative(face);
                        BlockPlaceContext place = new BlockPlaceContext(stage.player, InteractionHand.MAIN_HAND,
                                new ItemStack(LightworksBlocks.NAVIGATION_LIGHT.get()),
                                new BlockHitResult(Vec3.atCenterOf(post[0]).relative(face, 0.5), face, post[0], false));
                        BlockState state = LightworksBlocks.NAVIGATION_LIGHT.get().getStateForPlacement(place);
                        stage.set(pos, state);
                        colours.append(' ').append(face.getSerializedName()).append('=')
                                .append(state.getValue(NavigationLightBlock.COLOUR).getSerializedName());
                    }
                    stage.log("navigation: placed looking north:%s", colours);
                    SelfTestBridge.focus = Vec3.atCenterOf(post[0]);
                })
                .waitTicks(30)
                .shot("navigation-0")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * Ponder: opens every block's scene in turn and photographs it three times as it plays (5, 12 and 19
     * seconds in). Ponder pauses the game, so the client does this alone while the server waits. Read the
     * shots for the beam, lens and text.
     */
    static Script ponder() {
        String plan = String.join(",", Stream.of("searchlight", "floodlight", "pan_tilt_head", "rotating_beacon",
                "warning_light", "headlight", "tail_light", "navigation_light")
                .map(block -> Lightworks.MOD_ID + ":" + block).toList());
        return new Script("ponder")
                .run(stage -> {
                    if (stage.level.getServer().isDedicatedServer()) {
                        stage.log("ponder: skipped on a dedicated server");
                        SelfTestBridge.ponderDone = true;
                        return;
                    }
                    SelfTestBridge.ponderDone = false;
                    SelfTestBridge.ponderPlan = plan;
                })
                .waitUntil(stage -> SelfTestBridge.ponderDone, 6000, "the Ponder walkthrough");
    }

    /**
     * Screenshot sheet: every block on one deck, in daylight (models and textures) and at night (lenses,
     * beams, light), seen from the front and the back; then, with Sable, the same deck assembled into a
     * vehicle, hovering and after moving 3 blocks east. Port is on the west end, starboard on the east, the
     * deck faces north (toward the camera).
     */
    static Script gallery() {
        BlockPos[] deck = new BlockPos[1];
        Script script = new Script("gallery")
                .run(stage -> {
                    stage.night();
                    stage.level.setDayTime(6000);
                    stage.hover(0, 10, -4);
                    deck[0] = stage.at(0, 5, 4);
                    BlockPos d = deck[0];
                    for (int x = -4; x <= 4; x++) {
                        for (int z = -1; z <= 1; z++) {
                            stage.set(d.offset(x, 0, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
                        }
                    }
                    stage.set(d.offset(-4, 1, 0), searchlight(Direction.UP));
                    stage.lightAt(d.offset(-4, 1, 0)).aimAlong(new Vec3(0.2, 0.25, -1));
                    stage.set(d.offset(-2, 1, 0), LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                            .setValue(DirectionalKineticBlock.FACING, Direction.UP));
                    stage.set(d.offset(-2, 2, 0), searchlight(Direction.UP));
                    stage.set(d.offset(0, 1, 0), LightworksBlocks.ROTATING_BEACON.getDefaultState()
                            .setValue(RotatingBeaconBlock.FACING, Direction.UP));
                    stage.set(d.offset(2, 1, 0), LightworksBlocks.WARNING_LIGHT.getDefaultState()
                            .setValue(SignalLampBlock.FACING, Direction.UP));
                    stage.set(d.offset(0, 0, -2), LightworksBlocks.FLOODLIGHT.getDefaultState()
                            .setValue(DirectionalLightBlock.FACING, Direction.NORTH));
                    stage.lightAt(d.offset(0, 0, -2)).aimAlong(new Vec3(0, -0.7, -1));
                    stage.set(d.offset(-3, 0, -2), headlight(Direction.NORTH));
                    stage.set(d.offset(3, 0, -2), headlight(Direction.NORTH));
                    stage.set(d.offset(-3, 0, 2), LightworksBlocks.TAIL_LIGHT.getDefaultState()
                            .setValue(SignalLampBlock.FACING, Direction.SOUTH));
                    stage.set(d.offset(3, 0, 2), LightworksBlocks.TAIL_LIGHT.getDefaultState()
                            .setValue(SignalLampBlock.FACING, Direction.SOUTH));
                    stage.set(d.offset(-5, 0, 0), navigationLight(Direction.WEST, "port"));
                    stage.set(d.offset(5, 0, 0), navigationLight(Direction.EAST, "starboard"));
                    SelfTestBridge.focus = Vec3.atCenterOf(d.above());
                })
                .waitTicks(40)
                .shot("gallery-0-day")
                .run(stage -> stage.level.setDayTime(18000))
                .waitTicks(40)
                .shot("gallery-1-night")
                .run(stage -> stage.hover(0, 10, 12))
                .waitTicks(20)
                .shot("gallery-2-night-rear")
                .run(stage -> stage.hover(0, 10, -4))
                .waitTicks(20);
        if (ModList.get().isLoaded("sable")) {
            script.run(stage -> {
                        BlockPos from = deck[0].offset(-5, 0, -2);
                        BlockPos to = deck[0].offset(5, 2, 2);
                        stage.run("sable paused true");
                        stage.run("sable assemble area " + from.getX() + " " + from.getY() + " " + from.getZ() + " "
                                + to.getX() + " " + to.getY() + " " + to.getZ());
                    })
                    .waitTicks(40)
                    .shot("gallery-3-vehicle")
                    .repeat(30, (stage, i) -> stage.run("sable physics translation @l add 0.1 0 0 global"))
                    .waitTicks(10)
                    .shot("gallery-4-vehicle-moved")
                    .run(stage -> {
                        stage.run("sable remove @l");
                        stage.run("sable paused false");
                    });
        } else {
            script.run(stage -> stage.log("gallery: vehicle shots skipped, Sable is not installed"));
        }
        return script.run(stage -> {
            SelfTestBridge.focus = null;
            stage.restoreBlocks();
        });
    }

    private static BlockState navigationLight(Direction facing, String colour) {
        BlockState state = LightworksBlocks.NAVIGATION_LIGHT.getDefaultState().setValue(SignalLampBlock.FACING, facing);
        for (var value : NavigationLightBlock.COLOUR.getPossibleValues()) {
            if (value.getSerializedName().equals(colour)) {
                state = state.setValue(NavigationLightBlock.COLOUR, value);
            }
        }
        return state;
    }

    /**
     * Engineer's Goggles: the player wears them and looks at a dyed, dimmed Searchlight lighting a wall,
     * then at a Warning Light, with the GUI shown: the overlay should list state, aim, beam and colour, and
     * the beam path should be drawn.
     */
    static Script goggles() {
        BlockPos[] at = new BlockPos[2];
        ItemStack[] helmet = new ItemStack[1];
        return new Script("goggles")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, 0);
                    at[0] = stage.at(2, 0, 3);
                    at[1] = stage.at(-2, 0, 3);
                    for (int y = 0; y <= 3; y++) {
                        for (int x = 0; x <= 4; x++) {
                            stage.set(stage.at(x, y, 10), Blocks.STONE.defaultBlockState());
                        }
                    }
                    stage.set(at[0], searchlight(Direction.UP));
                    stage.lightAt(at[0]).aimAlong(new Vec3(0, 0.15, 1));
                    stage.lightAt(at[0]).lightState().setColour(DyeColor.LIGHT_BLUE);
                    stage.lightAt(at[0]).notifyUpdate();
                    stage.set(at[0].east(), Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState()
                            .setValue(WeightedPressurePlateBlock.POWER, 3));
                    stage.set(at[0].east().below(), Blocks.GLASS.defaultBlockState());
                    stage.set(at[1], LightworksBlocks.WARNING_LIGHT.getDefaultState()
                            .setValue(SignalLampBlock.FACING, Direction.UP));
                    helmet[0] = stage.player.getItemBySlot(EquipmentSlot.HEAD);
                    stage.player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());
                    SelfTestBridge.showGui = true;
                    SelfTestBridge.focus = Vec3.atCenterOf(at[0]).add(0, -0.2, 0);
                })
                .waitTicks(30)
                .shot("goggles-0-searchlight")
                .run(stage -> SelfTestBridge.focus = Vec3.atCenterOf(at[1]).add(0, -0.2, 0))
                .waitTicks(10)
                .shot("goggles-1-warning")
                .run(stage -> {
                    SelfTestBridge.showGui = false;
                    SelfTestBridge.focus = null;
                    stage.player.setItemSlot(EquipmentSlot.HEAD, helmet[0]);
                    stage.restoreBlocks();
                });
    }

    /** Aims a light the way a player does: looking along {@code target - light centre} and clicking it with a Wrench. */
    private static void wrenchAim(Stage stage, BlockPos light, Vec3 target) {
        Vec3 d = target.subtract(Vec3.atCenterOf(light)).normalize();
        stage.player.setYRot((float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG));
        stage.player.setXRot((float) (-Math.asin(d.y) * Mth.RAD_TO_DEG));
        BlockState state = stage.level.getBlockState(light);
        if (state.getBlock() instanceof DirectionalLightBlock block) {
            block.onWrenched(state, new UseOnContext(stage.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(light), Direction.UP, light, false)));
        }
    }

    /**
     * S4 and S5, Sable vehicle: a deck with a Searchlight hanging under it, made into a vehicle with
     * Sable's own commands and flown east at {@code speed} blocks per tick. {@code film}: photograph and
     * film it from the side (only useful while it stays in view).
     */
    static Script vehicle(String name, double speed, int flightTicks, boolean film) {
        Script script = new Script(name);
        if (!ModList.get().isLoaded("sable")) {
            return script.run(stage -> stage.log("%s: skipped, Sable is not installed", name));
        }
        BlockPos[] deck = new BlockPos[1];
        script.run(stage -> {
                    stage.night();
                    stage.stand(5, -9);
                    deck[0] = stage.at(0, 8, 6);
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(5, 3, 6)).add(0.5, 0, 0.5);
                })
                .waitTicks(40);
        if (film) {
            script.shot(name + "-0-dark");
        }
        script.run(stage -> {
                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            stage.set(deck[0].offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
                        }
                    }
                    stage.set(deck[0].below(), searchlight(Direction.DOWN));
                    stage.set(deck[0].offset(2, 0, 0), headlight(Direction.EAST));
                    stage.set(deck[0].offset(-2, 0, 0), headlight(Direction.WEST));
                    BlockPos from = deck[0].offset(-2, -1, -1);
                    BlockPos to = deck[0].offset(2, 0, 1);
                    // Freeze physics so the vehicle hangs still and moves only when we move it
                    stage.run("sable paused true");
                    stage.run("sable assemble area " + from.getX() + " " + from.getY() + " " + from.getZ() + " "
                            + to.getX() + " " + to.getY() + " " + to.getZ());
                    stage.log("%s: assembled deck at %s, ground at y=%d", name, deck[0], stage.origin.getY() - 1);
                })
                .waitTicks(40);
        if (film) {
            script.shot(name + "-1-hovering");
        }
        script.run(stage -> {
                    SelfTestBridge.probeGroundY = stage.origin.getY();
                    SelfTestBridge.probe = name;
                    SelfTestBridge.recording = film ? name + "-flight" : null;
                })
                .repeat(flightTicks, (stage, i) -> stage.run("sable physics translation @l add " + speed + " 0 0 global"))
                .run(stage -> {
                    SelfTestBridge.recording = null;
                    SelfTestBridge.probe = null;
                })
                .waitTicks(20);
        if (film) {
            script.shot(name + "-2-landed-east");
        }
        return script.run(stage -> {
                    stage.run("sable remove @l");
                    stage.run("sable paused false");
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * S2, Create contraption: a Mechanical Bearing on a Creative Motor 10 blocks up, turning a 3-block arm
     * with a Searchlight hanging from its end at 32 RPM (about 10 blocks a second at the light), so the
     * lit spot circles on the ground.
     */
    static Script bearing() {
        int rpm = 32;
        int spinTicks = 100;
        BlockPos[] motor = new BlockPos[1];
        return new Script("bearing")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, -6);
                    motor[0] = stage.at(0, 9, 8);
                    BlockPos bearing = motor[0].above();
                    // Whatever the arm leaves behind when it stops at another angle is cleared on restore
                    stage.reserve(bearing.offset(-4, 0, -4), bearing.offset(4, 2, 4));
                    for (int x = 0; x <= 3; x++) {
                        stage.set(bearing.offset(x, 1, 0), Blocks.OAK_PLANKS.defaultBlockState());
                    }
                    stage.set(bearing.offset(3, 0, 0), searchlight(Direction.DOWN));
                    // Glue the arm together and the Searchlight to its end
                    stage.level.addFreshEntity(new SuperGlueEntity(stage.level,
                            AABB.encapsulatingFullBlocks(bearing.above(), bearing.offset(3, 1, 0))));
                    stage.level.addFreshEntity(new SuperGlueEntity(stage.level,
                            AABB.encapsulatingFullBlocks(bearing.offset(3, 0, 0), bearing.offset(3, 1, 0))));
                    stage.set(bearing, AllBlocks.MECHANICAL_BEARING.getDefaultState()
                            .setValue(DirectionalKineticBlock.FACING, Direction.UP));
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 5, 8)).add(0.5, 0, 0.5);
                })
                .waitTicks(40)
                .shot("bearing-0-still")
                .run(stage -> {
                    // A spinning motor makes the bearing assemble the arm into a contraption
                    stage.set(motor[0], AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
                    setMotorSpeed(stage, motor[0], rpm);
                })
                .waitTicks(20)
                .run(stage -> {
                    SelfTestBridge.probeGroundY = stage.origin.getY();
                    SelfTestBridge.probe = "bearing";
                    SelfTestBridge.recording = "bearing-spin";
                })
                .waitTicks(spinTicks)
                .run(stage -> {
                    SelfTestBridge.recording = null;
                    SelfTestBridge.probe = null;
                    setMotorSpeed(stage, motor[0], 0);
                })
                .waitTicks(40)
                .shot("bearing-1-stopped")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.discardContraptions(motor[0], 6);
                    stage.restoreBlocks();
                    stage.discardContraptions(motor[0], 6);
                });
    }

    /**
     * S3 stand-in for a train: a Mechanical Piston pushes a cart 6 blocks east and pulls it back, with a
     * Headlight at each end. The east one should light on the way out, the west one on the way back,
     * and the east one's spot lands on a wall ahead. Trains run the same contraption code.
     */
    static Script piston() {
        int rpm = 128;
        BlockPos[] at = new BlockPos[2];
        int[] sign = {1};
        return new Script("piston")
                .run(stage -> {
                    stage.night();
                    stage.stand(5, -6);
                    BlockPos piston = stage.at(0, 1, 6);
                    BlockPos cart = piston.east();
                    at[0] = piston;
                    at[1] = piston.above();
                    stage.reserve(piston.offset(-7, -1, -2), piston.offset(16, 3, 2));
                    for (int i = 1; i <= 6; i++) {
                        stage.set(piston.west(i), AllBlocks.PISTON_EXTENSION_POLE.getDefaultState()
                                .setValue(BlockStateProperties.FACING, Direction.EAST));
                    }
                    stage.set(cart, Blocks.OAK_PLANKS.defaultBlockState());
                    stage.set(cart.east(), headlight(Direction.EAST));
                    stage.set(cart.below(), headlight(Direction.WEST));
                    // Tail lights on top of the cart, one facing each way
                    stage.set(cart.above(), LightworksBlocks.TAIL_LIGHT.getDefaultState()
                            .setValue(SignalLampBlock.FACING, Direction.UP));
                    stage.level.addFreshEntity(new SuperGlueEntity(stage.level,
                            AABB.encapsulatingFullBlocks(cart.below(), cart.east().above())));
                    for (int y = 0; y <= 3; y++) {
                        for (int z = 5; z <= 7; z++) {
                            stage.set(stage.at(16, y, z), Blocks.STONE.defaultBlockState());
                        }
                    }
                    stage.set(piston, AllBlocks.MECHANICAL_PISTON.getDefaultState()
                            .setValue(BlockStateProperties.FACING, Direction.EAST)
                            .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
                    // Stay assembled at the end of travel and reverse in place, like a train
                    if (stage.level.getBlockEntity(piston) instanceof SmartBlockEntity actuator) {
                        ScrollValueBehaviour mode = actuator.getBehaviour(ScrollValueBehaviour.TYPE);
                        if (mode != null) {
                            mode.setValue(MovementMode.MOVE_NEVER_PLACE.ordinal());
                        }
                    }
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(7, 1, 6)).add(0.5, 0, 0.5);
                })
                .waitTicks(40)
                .shot("piston-0-still")
                .run(stage -> {
                    stage.set(at[1], AllBlocks.CREATIVE_MOTOR.getDefaultState()
                            .setValue(CreativeMotorBlock.FACING, Direction.DOWN));
                    setMotorSpeed(stage, at[1], rpm);
                    SelfTestBridge.probeGroundY = stage.origin.getY();
                    SelfTestBridge.probe = "piston";
                    SelfTestBridge.recording = "piston-run";
                })
                .waitTicks(10)
                .run(stage -> {
                    // Which way a speed sign pushes depends on the piston's axis: if it did not move, flip it
                    if (stage.level.getBlockState(at[0]).getValue(MechanicalPistonBlock.STATE) == PistonState.RETRACTED) {
                        sign[0] = -1;
                        setMotorSpeed(stage, at[1], -rpm);
                    }
                })
                .waitTicks(40)
                .run(stage -> setMotorSpeed(stage, at[1], -sign[0] * rpm))
                .waitTicks(45)
                .run(stage -> {
                    SelfTestBridge.recording = null;
                    SelfTestBridge.probe = null;
                    setMotorSpeed(stage, at[1], 0);
                })
                .waitTicks(20)
                .shot("piston-1-back")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.discardContraptions(at[0], 18);
                    stage.restoreBlocks();
                    stage.discardContraptions(at[0], 18);
                });
    }

    /**
     * S6 and S7, load: 50 Searchlights under a 10 by 5 deck, made into one Sable vehicle. Measured with
     * every beam switched off (the baseline: the same vehicle and blocks), then on while hovering, then on
     * while flying. Each phase logs our server and client time and the client's frame times.
     */
    static Script load() {
        Script script = new Script("load");
        if (!ModList.get().isLoaded("sable")) {
            return script.run(stage -> stage.log("load: skipped, Sable is not installed"));
        }
        int phaseTicks = 100;
        double speed = 0.1;
        BlockPos[] deck = new BlockPos[1];
        script.run(stage -> {
                    stage.night();
                    stage.stand(0, -10);
                    deck[0] = stage.at(0, 8, 6);
                    for (int x = -4; x <= 5; x++) {
                        for (int z = -2; z <= 2; z++) {
                            stage.set(deck[0].offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
                            stage.set(deck[0].offset(x, -1, z), searchlight(Direction.DOWN));
                        }
                    }
                    BlockPos from = deck[0].offset(-4, -1, -2);
                    BlockPos to = deck[0].offset(5, 0, 2);
                    stage.run("sable paused true");
                    stage.run("sable assemble area " + from.getX() + " " + from.getY() + " " + from.getZ() + " "
                            + to.getX() + " " + to.getY() + " " + to.getZ());
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 3, 6)).add(0.5, 0, 0.5);
                    SelfTestBridge.beamsOff = true;
                })
                .waitTicks(60);
        measure(script, "load-off-flying", phaseTicks,
                (stage, i) -> stage.run("sable physics translation @l add " + speed + " 0 0 global"));
        script.run(stage -> SelfTestBridge.beamsOff = false).waitTicks(40);
        measure(script, "load-on-hovering", phaseTicks, (stage, i) -> {
        });
        measure(script, "load-on-flying", phaseTicks,
                (stage, i) -> stage.run("sable physics translation @l add " + -speed + " 0 0 global"));
        return script.shot("load-on")
                .run(stage -> {
                    stage.run("sable remove @l");
                    stage.run("sable paused false");
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /** Runs {@code each} for {@code ticks} ticks and logs what our block entities cost the server meanwhile. */
    private static void measure(Script script, String phase, int ticks, BiConsumer<Stage, Integer> each) {
        long[] start = new long[2];
        script.run(stage -> {
                    start[0] = Timer.SERVER_BLOCK.nanos();
                    start[1] = Timer.SERVER_BLOCK.calls();
                    SelfTestBridge.frameStats = phase;
                })
                .repeat(ticks, each)
                .run(stage -> {
                    SelfTestBridge.frameStats = null;
                    long calls = Timer.SERVER_BLOCK.calls() - start[1];
                    stage.log("%s server: our block entities %.1f us per tick (%d light ticks), "
                                    + "server tick %.2f ms on average over the last 100 ticks",
                            phase, (Timer.SERVER_BLOCK.nanos() - start[0]) / 1000.0 / ticks, calls,
                            stage.level.getServer().getAverageTickTimeNanos() / 1e6);
                });
    }

    /**
     * One-off: removes Lightworks lights, contraptions and glue that earlier test runs left within 24
     * blocks of where the player joined (before contraptions were discarded on restore).
     */
    static Script cleanup() {
        return new Script("cleanup").run(stage -> {
            stage.discardContraptions(stage.origin, 24);
            int removed = 0;
            for (BlockPos pos : BlockPos.betweenClosed(stage.at(-24, -1, -24), stage.at(24, 16, 24))) {
                BlockState state = stage.level.getBlockState(pos);
                if (state.getBlock().builtInRegistryHolder().key().location().getNamespace().equals(Lightworks.MOD_ID)) {
                    stage.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    removed++;
                }
            }
            // The plank of the piston scenario's cart, left with its lights
            if (stage.level.getBlockState(stage.at(1, 1, 6)).is(Blocks.OAK_PLANKS)) {
                stage.level.setBlock(stage.at(1, 1, 6), Blocks.AIR.defaultBlockState(), 3);
                removed++;
            }
            stage.log("cleanup: removed %d leftover blocks", removed);
        });
    }

    /** Like turning the Creative Motor's value panel (its speed field is not public in Create 6.0.0). */
    private static void setMotorSpeed(Stage stage, BlockPos motor, int rpm) {
        if (stage.level.getBlockEntity(motor) instanceof CreativeMotorBlockEntity creative) {
            ScrollValueBehaviour speed = creative.getBehaviour(ScrollValueBehaviour.TYPE);
            if (speed != null) {
                speed.setValue(rpm);
            }
        }
    }
}
