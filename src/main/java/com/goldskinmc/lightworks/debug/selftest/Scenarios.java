package com.goldskinmc.lightworks.debug.selftest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.content.beacon.RotatingBeaconBlock;
import com.goldskinmc.lightworks.content.beacon.RotatingBeaconBlockEntity;
import com.goldskinmc.lightworks.beam.Aim;
import com.goldskinmc.lightworks.beam.PanTilt;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadBlock;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadBlockEntity;
import com.goldskinmc.lightworks.content.signal.EdgeLightBlock;
import com.goldskinmc.lightworks.debug.Timer;
import com.goldskinmc.lightworks.light.ProjectedTint;
import com.goldskinmc.lightworks.content.signal.SignalLampBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlockEntity;
import com.goldskinmc.lightworks.content.signal.WarningPattern;
import com.goldskinmc.lightworks.debug.Timer;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.light.BeamTrace;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.Create;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.IControlContraption.MovementMode;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.PistonState;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** The self-test scenarios, by name. Each builds relative to where the player joined, at night. */
final class Scenarios {
    static final Map<String, Supplier<Script>> ALL = new LinkedHashMap<>();
    /** What {@code -Pselftest=true} runs. */
    static final String DEFAULT =
            "fixed,aim,floodlight,pantilt,pantilt-level,beacon,warning,edge,sweep,goggles,colour,vehicle,bearing,piston,vehicle-fast";

    static {
        ALL.put("fixed", Scenarios::fixed);
        ALL.put("aim", Scenarios::aim);
        ALL.put("floodlight", Scenarios::floodlight);
        ALL.put("pantilt", Scenarios::panTilt);
        ALL.put("sweep", Scenarios::sweep);
        ALL.put("head-base", Scenarios::headBase);
        ALL.put("pantilt-level", Scenarios::panTiltLevel);
        ALL.put("moving-flood", Scenarios::movingFlood);
        ALL.put("flood-block", Scenarios::floodBlock);
        ALL.put("block-change", Scenarios::blockChange);
        ALL.put("day-colour", Scenarios::dayColour);
        ALL.put("glare", Scenarios::glare);
        ALL.put("held", Scenarios::held);
        ALL.put("shapes", Scenarios::shapes);
        ALL.put("wall-sides", Scenarios::wallSides);
        ALL.put("load-flood", Scenarios::loadFlood);
        ALL.put("level-dyed", Scenarios::levelDyed);
        ALL.put("load-level", Scenarios::loadLevel);
        ALL.put("beacon", Scenarios::beacon);
        ALL.put("warning", Scenarios::warning);
        ALL.put("goggles", Scenarios::goggles);
        ALL.put("colour", Scenarios::colour);
        ALL.put("colour-mix", Scenarios::colourMix);
        ALL.put("surfaces", Scenarios::surfaces);
        ALL.put("vehicle", () -> vehicle("vehicle", 0.1, 100, true));
        // Create's default train top speed, 28 blocks a second: how far the spot trails at train speeds
        ALL.put("vehicle-fast", () -> vehicle("vehicle-fast", 1.4, 40, false));
        ALL.put("bearing", Scenarios::bearing);
        ALL.put("piston", Scenarios::piston);
        // Not in the default set: heavier, or one-off
        ALL.put("load", () -> load("load", false));
        ALL.put("load-mix", () -> load("load-mix", true));
        ALL.put("edge", Scenarios::edge);
        ALL.put("load-edge", Scenarios::loadEdge);
        ALL.put("load-lamps-steady", () -> loadLamps("load-lamps-steady", WarningPattern.STEADY));
        ALL.put("load-lamps-blink", () -> loadLamps("load-lamps-blink", WarningPattern.BLINK));
        ALL.put("load-lamps-strobe", () -> loadLamps("load-lamps-strobe", WarningPattern.STROBE));
        ALL.put("load-lamps-steady2", () -> loadLamps("load-lamps-steady2", WarningPattern.STEADY));
        ALL.put("cleanup", Scenarios::cleanup);
        ALL.put("ponder", Scenarios::ponder);
        ALL.put("gallery", Scenarios::gallery);
        ALL.put("closeup", Scenarios::closeup);
        ALL.put("train", Scenarios::train);
        ALL.put("vehicle-beam", Scenarios::vehicleBeam);
        ALL.put("walk-past", Scenarios::walkPast);
        ALL.put("beam-cross", Scenarios::beamCross);
        ALL.put("inside-beam", Scenarios::insideBeam);
        ALL.put("replica", Scenarios::replica);
        ALL.put("showcase-aim", Scenarios::showcaseAim);
        ALL.put("showcase-signals", Scenarios::showcaseSignals);
        ALL.put("showcase-colour", Scenarios::showcaseColour);
        ALL.put("showcase-car", Scenarios::showcaseCar);
        ALL.put("showcase-airship", Scenarios::showcaseAirship);
        ALL.put("showcase-banner", Scenarios::showcaseBanner);
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
     * Board aim: a Searchlight on the floor aimed with its Pan and Tilt boards at a point high on a wall 12
     * blocks away. The beam should land within a block of that point and light it.
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
                    PanTilt set = boardAim(stage, light[0], target[0]);
                    stage.log("aim: boards set to %s, aim %s", set, stage.lightAt(light[0]).aim());
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
     * A Floodlight on the side of a pillar, tilted down on its board to light the ground in front of it: a
     * wide lit patch.
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
                .run(stage -> boardAim(stage, light[0], target[0]))
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
     * A Searchlight on a Pan-Tilt Head sweeping 45 degrees either way at 16 RPM (4.8 degrees a tick): the head's
     * pan should stay within 45 of where it started and reach both ends, with the spot following on the client
     * at every reversal. Then the box shown, and back to a full circle: the pan should go past 45.
     */
    static Script sweep() {
        BlockPos[] at = new BlockPos[3];
        float[] range = {0, 0};
        Script script = new Script("sweep")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, -4);
                    at[0] = stage.at(0, 9, 8);
                    at[1] = at[0].below();
                    at[2] = at[0].below(2);
                    stage.set(at[1], LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                            .setValue(PanTiltHeadBlock.FACING, Direction.DOWN));
                    stage.set(at[2], searchlight(Direction.DOWN));
                    stage.set(at[1].east().below(), Blocks.GLASS.defaultBlockState());
                    stage.set(at[1].east(), Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState()
                            .setValue(WeightedPressurePlateBlock.POWER, 10));
                    stage.set(at[0], AllBlocks.CREATIVE_MOTOR.getDefaultState()
                            .setValue(CreativeMotorBlock.FACING, Direction.DOWN));
                    ((PanTiltHeadBlockEntity) stage.level.getBlockEntity(at[1]))
                            .getBehaviour(ScrollValueBehaviour.TYPE).setValue(9);
                    setMotorSpeed(stage, at[0], 16);
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 0, 8)).add(0.5, 0, 0.5);
                })
                // Time for the tilt to reach the plate's signal
                .waitTicks(30)
                .run(stage -> {
                    SelfTestBridge.probeGroundY = stage.origin.getY();
                    SelfTestBridge.probeAxis = Vec3.atCenterOf(at[2]);
                    SelfTestBridge.probe = "sweep";
                    SelfTestBridge.recording = "sweep";
                });
        for (int i = 0; i < 120; i++) {
            script.waitTicks(1).run(stage -> {
                float yaw = Mth.wrapDegrees(((PanTiltHeadBlockEntity) stage.level.getBlockEntity(at[1])).yaw(0));
                range[0] = Math.min(range[0], yaw);
                range[1] = Math.max(range[1], yaw);
            });
        }
        return script.run(stage -> {
                    SelfTestBridge.recording = null;
                    SelfTestBridge.probe = null;
                    SelfTestBridge.probeAxis = null;
                    stage.log("sweep: pan went from %.1f to %.1f (expected about -45 to 45)", range[0], range[1]);
                    SelfTestBridge.showGui = true;
                    SelfTestBridge.valueBoard = at[1];
                    SelfTestBridge.focus = Vec3.atCenterOf(at[1]);
                    stage.hover(3, 8, 5);
                })
                .waitTicks(20)
                .shot("sweep-0-board")
                .run(stage -> {
                    SelfTestBridge.valueBoard = null;
                    SelfTestBridge.showGui = false;
                    SelfTestBridge.focus = null;
                    setMotorSpeed(stage, at[0], 0);
                    stage.restoreBlocks();
                });
    }

    /**
     * Dyes across surfaces: a row of Searchlights looking straight down per colour, undyed first, a strip of ground per
     * block, then low Floodlights on grass, undyed, red, green and blue. Each should look lit on every surface and never
     * darker than the ground beside it: a green light bright on grass, a red or blue one dim but clearly there.
     */
    static Script surfaces() {
        Block[] grounds = {Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.WHITE_WOOL, Blocks.DIRT, Blocks.SAND, Blocks.OAK_PLANKS,
                Blocks.DEEPSLATE};
        DyeColor[] colours = {null, DyeColor.RED, DyeColor.GREEN, DyeColor.BLUE, DyeColor.YELLOW, DyeColor.BROWN,
                DyeColor.GRAY, DyeColor.BLACK};
        int cell = 5;
        int across = grounds.length * cell;
        int along = colours.length * cell;
        Script script = new Script("surfaces")
                .run(stage -> {
                    stage.night();
                    for (int g = 0; g < grounds.length; g++) {
                        for (int x = 0; x < cell; x++) {
                            for (int z = 0; z < along; z++) {
                                stage.set(stage.at(g * cell + x - across / 2, -1, z + 2), grounds[g].defaultBlockState());
                            }
                        }
                        for (int c = 0; c < colours.length; c++) {
                            BlockPos light = stage.at(g * cell + cell / 2 - across / 2, 8, c * cell + cell / 2 + 2);
                            stage.set(light, searchlight(Direction.DOWN));
                            stage.lightAt(light).lightState().setColour(colours[c]);
                            stage.lightAt(light).notifyUpdate();
                        }
                    }
                    stage.hover(0, 30, -8);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 0, along / 2 + 2));
                })
                .waitTicks(60);
        script.shot("surfaces");
        script.run(stage -> {
            stage.restoreBlocks();
            // Far from where the rays land there is little block light: the lit ground should still not turn dark
            DyeColor[] lit = {null, DyeColor.RED, DyeColor.GREEN, DyeColor.BLUE};
            for (int i = 0; i < lit.length; i++) {
                BlockPos light = stage.at(i * 14 - 21, 1, 4);
                stage.set(light, LightworksBlocks.FLOODLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
                stage.lightAt(light).aimAlong(new Vec3(0, -1.5, 8));
                stage.lightAt(light).lightState().setColour(lit[i]);
                stage.lightAt(light).notifyUpdate();
            }
            stage.hover(0, 16, -6);
            SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 0, 14));
        }).waitTicks(60);
        script.shot("surfaces-field");
        return script.run(stage -> {
            SelfTestBridge.focus = null;
            stage.restoreBlocks();
        });
    }

    /**
     * A head with no signal turning a Searchlight level over flat ground, so the centre ray lands nowhere and the
     * light goes where the beam's lower rays land. The probe's bearing lag should stay near the tick's turn
     * (4.8 degrees at 16 RPM), not several ticks of it.
     */
    static Script panTiltLevel() {
        BlockPos[] at = new BlockPos[3];
        return new Script("pantilt-level")
                .run(stage -> {
                    stage.night();
                    at[0] = stage.at(0, 0, 8);
                    at[1] = at[0].above();
                    at[2] = at[1].above();
                    stage.set(at[0], AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
                    stage.set(at[1], LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                            .setValue(PanTiltHeadBlock.FACING, Direction.UP));
                    stage.set(at[2], searchlight(Direction.UP));
                    setMotorSpeed(stage, at[0], 16);
                    stage.hover(0, 12, 0);
                    SelfTestBridge.focus = Vec3.atCenterOf(at[2]);
                })
                .waitTicks(30)
                .run(stage -> {
                    SelfTestBridge.probeGroundY = stage.origin.getY();
                    SelfTestBridge.probeAxis = Vec3.atCenterOf(at[2]);
                    SelfTestBridge.probe = "pantilt-level";
                    SelfTestBridge.recording = "pantilt-level";
                })
                .waitTicks(120)
                .run(stage -> {
                    SelfTestBridge.recording = null;
                    SelfTestBridge.probe = null;
                    SelfTestBridge.probeAxis = null;
                })
                .shot("pantilt-level-0")
                .run(stage -> {
                    setMotorSpeed(stage, at[0], 0);
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * A level Floodlight, then a level Headlight, and a stone block moved across the beam: logs the block light on the
     * air in front of the block's lit face, per distance from the lamp and per blocks off its axis. Inside the beam a
     * block should have light wherever it stands, ray or no ray. The shot of stones in a beam shows the same by eye.
     */
    static Script floodBlock() {
        int[] distances = {8, 12, 16};
        int[] offsets = {-8, -6, -4, -2, 0, 2, 4, 6, 8};
        BlockPos[] lamp = new BlockPos[1];
        BlockPos[] placed = new BlockPos[2];
        Script script = new Script("flood-block").run(stage -> stage.night());
        for (int type = 0; type < 2; type++) {
            boolean flood = type == 0;
            script.run(stage -> {
                lamp[0] = stage.at(0, 0, 0);
                stage.set(lamp[0], (flood ? LightworksBlocks.FLOODLIGHT : LightworksBlocks.HEADLIGHT).getDefaultState()
                        .setValue(DirectionalLightBlock.FACING, Direction.UP));
                stage.lightAt(lamp[0]).setPanTilt(new PanTilt(0, 0));
                stage.lightAt(lamp[0]).aimAlong(new Vec3(0, 0, 1));
            }).waitTicks(20);
            String name = flood ? "Floodlight" : "Headlight";
            int high = 1;
            for (int distance : distances) {
                StringBuilder row = new StringBuilder();
                script.run(stage -> row.setLength(0));
                for (int offset : offsets) {
                    script.run(stage -> {
                        placed[0] = stage.at(offset, 0, distance);
                        placed[1] = placed[0].above();
                        stage.set(placed[0], Blocks.STONE.defaultBlockState());
                        if (high == 2) {
                            stage.set(placed[1], Blocks.STONE.defaultBlockState());
                        }
                    }).waitTicks(20).run(stage -> {
                        row.append(' ').append(stage.level.getBrightness(LightLayer.BLOCK, placed[0].north()));
                        stage.level.setBlock(placed[0], Blocks.AIR.defaultBlockState(), 3);
                        stage.level.setBlock(placed[1], Blocks.AIR.defaultBlockState(), 3);
                    }).waitTicks(10);
                }
                script.run(stage -> stage.log("flood-block: %s, %d out, light in front at -8 to 8 across:%s", name, distance, row));
            }
        }
        // The owner's scene: stones here and there in a level Floodlight's beam, none on a ray's line
        script.run(stage -> {
            lamp[0] = stage.at(0, 0, 0);
            stage.set(lamp[0], LightworksBlocks.FLOODLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
            stage.lightAt(lamp[0]).setPanTilt(new PanTilt(0, 0));
            stage.lightAt(lamp[0]).aimAlong(new Vec3(0, 0, 1));
            for (int[] at : new int[][] {{-6, 12}, {2, 12}, {-2, 16}, {4, 16}, {7, 14}, {-9, 17}}) {
                stage.set(stage.at(at[0], 0, at[1]), Blocks.STONE.defaultBlockState());
            }
            stage.hover(-3, 5, -3);
            SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 0, 13)).add(0.5, 0, 0.5);
        }).waitTicks(40).shot("flood-block");
        return script.run(stage -> {
            SelfTestBridge.focus = null;
            stage.restoreBlocks();
        });
    }

    /**
     * A stone placed in a level Floodlight's beam, away from its rays, then broken: the painted faces must follow at once
     * (more with the stone, back to the first count without it; 5 ticks is well under a refresh of anything else), and
     * the last shot must show no ghost of the block.
     */
    static Script blockChange() {
        return new Script("block-change").run(stage -> {
            stage.night();
            BlockPos lamp = stage.at(0, 0, 0);
            stage.set(lamp, LightworksBlocks.FLOODLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
            stage.lightAt(lamp).setPanTilt(new PanTilt(0, 0));
            stage.lightAt(lamp).aimAlong(new Vec3(0, 0, 1));
            stage.hover(-3, 5, -3);
            SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 0, 13)).add(0.5, 0, 0.5);
        }).waitTicks(40).run(stage -> stage.log("block-change: no stone, %d faces", ProjectedTint.activeFaces()))
                .run(stage -> stage.set(stage.at(5, 0, 13), Blocks.STONE.defaultBlockState()))
                .waitTicks(5).run(stage -> stage.log("block-change: stone placed, %d faces", ProjectedTint.activeFaces()))
                .shot("block-change-placed")
                .run(stage -> stage.level.setBlock(stage.at(5, 0, 13), Blocks.AIR.defaultBlockState(), 3))
                .waitTicks(5).run(stage -> stage.log("block-change: stone broken, %d faces", ProjectedTint.activeFaces()))
                .shot("block-change-broken")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * Slabs, stairs, carpet, snow, a path and farmland under a Floodlight looking straight down, undyed and then red:
     * the lit square must lie on their real shape, level with their tops, and not float at a full block's height. The
     * poppy keeps no paint, being drawn nothing like its shape.
     */
    static Script shapes() {
        BlockPos[] lamp = new BlockPos[1];
        return new Script("shapes").run(stage -> {
            stage.night();
            lamp[0] = stage.at(0, 4, 2);
            stage.set(lamp[0], LightworksBlocks.FLOODLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.DOWN));
            stage.set(stage.at(-3, 0, 1), Blocks.STONE_SLAB.defaultBlockState());
            stage.set(stage.at(-2, 0, 1), Blocks.STONE_SLAB.defaultBlockState());
            stage.set(stage.at(-1, 0, 1), Blocks.OAK_SLAB.defaultBlockState());
            Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            for (int i = 0; i < sides.length; i++) {
                stage.set(stage.at(1 + i, 0, 1), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, sides[i]));
            }
            stage.set(stage.at(-3, 0, 3), Blocks.WHITE_CARPET.defaultBlockState());
            stage.set(stage.at(-2, 0, 3), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3));
            stage.set(stage.at(-1, 0, 3), Blocks.DIRT_PATH.defaultBlockState());
            stage.set(stage.at(1, 0, 3), Blocks.FARMLAND.defaultBlockState());
            stage.set(stage.at(2, 0, 3), Blocks.POPPY.defaultBlockState());
            stage.set(stage.at(3, 0, 3), Blocks.STONE.defaultBlockState());
            stage.hover(-1, 4, -4);
            SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 0, 2)).add(0.5, 0, 0.5);
        }).waitTicks(40).run(stage -> stage.log("shapes: %d faces", ProjectedTint.activeFaces())).shot("shapes-white")
                .run(stage -> {
                    stage.lightAt(lamp[0]).lightState().setColour(DyeColor.RED);
                    stage.lightAt(lamp[0]).notifyUpdate();
                })
                .waitTicks(20).shot("shapes-red")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * The same Searchlight on white walls facing north and east, which Minecraft shades 0.8 and 0.6: the spot makes up
     * for that, so the two shots' centre brightness should nearly match, neither clipped to white. Then by day and dyed
     * red: both walls' colour must fade alike, whichever way they face.
     */
    static Script wallSides() {
        Script script = new Script("wall-sides").run(stage -> {
            stage.night();
            for (Vec3[] light : new Vec3[][] {{new Vec3(0, 1, 2), new Vec3(0, 0, 1)}, {new Vec3(22, 1, 0), new Vec3(-1, 0, 0)}}) {
                BlockPos lamp = stage.at((int) light[0].x, (int) light[0].y, (int) light[0].z);
                stage.set(lamp, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
                stage.lightAt(lamp).setPanTilt(new PanTilt(0, 0));
                stage.lightAt(lamp).aimAlong(light[1]);
            }
        });
        for (Block wall : List.of(Blocks.WHITE_CONCRETE, Blocks.DIORITE, Blocks.ANDESITE)) {
            String name = BuiltInRegistries.BLOCK.getKey(wall).getPath();
            script.run(stage -> {
                for (int y = 0; y < 7; y++) {
                    for (int i = -5; i <= 5; i++) {
                        stage.set(stage.at(i, y, 8), wall.defaultBlockState());
                        stage.set(stage.at(16, y, i), wall.defaultBlockState());
                    }
                }
                stage.hover(3, 3, 3);
                SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 1, 8)).add(0.5, 0.5, 0);
            }).waitTicks(40).shot("wall-sides-" + name + "-north").run(stage -> {
                stage.hover(20, 3, 3);
                SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(17, 1, 0)).add(0, 0.5, 0.5);
            }).waitTicks(10).shot("wall-sides-" + name + "-east");
        }
        script.run(stage -> {
            stage.level.setDayTime(6000);
            for (BlockPos lamp : List.of(stage.at(0, 1, 2), stage.at(22, 1, 0))) {
                stage.lightAt(lamp).lightState().setColour(DyeColor.RED);
                stage.lightAt(lamp).notifyUpdate();
            }
            stage.hover(3, 3, 3);
            SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 1, 8)).add(0.5, 0.5, 0);
        }).waitTicks(20).shot("wall-sides-day-north").run(stage -> {
            stage.hover(20, 3, 3);
            SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(17, 1, 0)).add(0, 0.5, 0.5);
        }).waitTicks(10).shot("wall-sides-day-east");
        return script.run(stage -> {
            SelfTestBridge.focus = null;
            stage.restoreBlocks();
        });
    }

    /**
     * Every block's item in the hand, by day, from the player's eyes (with the hotbar, for its icon) and from the front:
     * each must sit in the hand and not hang off it.
     */
    static Script held() {
        ItemStack[] saved = new ItemStack[1];
        Script script = new Script("held").run(stage -> {
            stage.level.setDayTime(6000);
            stage.stand(0, 0);
            saved[0] = stage.player.getMainHandItem().copy();
            SelfTestBridge.showGui = true;
        });
        for (ItemLike item : List.of(LightworksBlocks.SEARCHLIGHT, LightworksBlocks.FLOODLIGHT, LightworksBlocks.HEADLIGHT,
                LightworksBlocks.PAN_TILT_HEAD, LightworksBlocks.ROTATING_BEACON, LightworksBlocks.WARNING_LIGHT,
                LightworksBlocks.TAIL_LIGHT, LightworksBlocks.EDGE_LIGHT)) {
            String name = BuiltInRegistries.ITEM.getKey(item.asItem()).getPath();
            script.run(stage -> {
                stage.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                SelfTestBridge.thirdPerson = false;
            }).waitTicks(15).shot("held-" + name + "-eyes").run(stage -> SelfTestBridge.thirdPerson = true)
                    .waitTicks(10).shot("held-" + name + "-front");
        }
        return script.run(stage -> {
            SelfTestBridge.thirdPerson = false;
            SelfTestBridge.showGui = false;
            stage.player.setItemInHand(InteractionHand.MAIN_HAND, saved[0]);
        });
    }

    /**
     * Dyed Searchlights looking down by day, red and blue, on stone, white wool and grass, under open sky. The colour
     * should read without looking painted on.
     */
    static Script dayColour() {
        return new Script("day-colour").run(stage -> {
            stage.level.setDayTime(6000);
            Block[] floors = {Blocks.STONE, Blocks.WHITE_WOOL, null};
            DyeColor[] dyes = {DyeColor.RED, DyeColor.BLUE};
            for (int d = 0; d < dyes.length; d++) {
                for (int f = 0; f < floors.length; f++) {
                    int x = -7 + f * 7;
                    int z = 2 + d * 7;
                    if (floors[f] != null) {
                        for (int i = -3; i <= 3; i++) {
                            for (int j = -3; j <= 3; j++) {
                                stage.set(stage.at(x + i, 0, z + j), floors[f].defaultBlockState());
                            }
                        }
                    }
                    BlockPos lamp = stage.at(x, 5, z);
                    stage.set(lamp, searchlight(Direction.DOWN));
                    stage.lightAt(lamp).lightState().setColour(dyes[d]);
                    stage.lightAt(lamp).notifyUpdate();
                }
            }
            stage.hover(0, 13, -8);
            SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 0, 5)).add(0.5, 0, 0.5);
        }).waitTicks(40).shot("day-colour").run(stage -> {
            stage.hover(0, 2, -10);
            SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 2, 5)).add(0.5, 0, 0.5);
        }).waitTicks(20).shot("day-colour-side").run(stage -> {
            SelfTestBridge.focus = null;
            stage.restoreBlocks();
        });
    }

    /**
     * Looking into a Searchlight aimed at the eye: a soft glow round its lens, which must not hide what is behind it;
     * none from beside the beam, none with a wall between, a wider one from a Floodlight, and still one by day.
     */
    static Script glare() {
        BlockPos[] lamp = new BlockPos[1];
        return new Script("glare").run(stage -> {
            stage.night();
            lamp[0] = stage.at(0, 0, 0);
            stage.set(lamp[0], searchlight(Direction.UP));
            stage.lightAt(lamp[0]).aimAlong(new Vec3(0, 0.2, 1));
            stage.hover(0, 1, 10);
            SelfTestBridge.focus = Vec3.atCenterOf(lamp[0]);
        }).waitTicks(30).shot("glare-in-beam").run(stage -> stage.hover(6, 1, 10)).waitTicks(10).shot("glare-aside")
                .run(stage -> {
                    stage.hover(0, 1, 10);
                    for (int x = -1; x <= 1; x++) {
                        for (int y = 0; y <= 2; y++) {
                            stage.set(stage.at(x, y, 4), Blocks.GLASS.defaultBlockState());
                        }
                    }
                }).waitTicks(10).shot("glare-through-glass").run(stage -> {
                    for (int x = -1; x <= 1; x++) {
                        for (int y = 0; y <= 2; y++) {
                            stage.set(stage.at(x, y, 4), Blocks.STONE.defaultBlockState());
                        }
                    }
                }).waitTicks(10).shot("glare-wall").run(stage -> {
                    for (int x = -1; x <= 1; x++) {
                        for (int y = 0; y <= 2; y++) {
                            stage.set(stage.at(x, y, 4), Blocks.AIR.defaultBlockState());
                        }
                    }
                    stage.set(lamp[0], LightworksBlocks.FLOODLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
                    stage.lightAt(lamp[0]).aimAlong(new Vec3(0, 0.2, 1));
                }).waitTicks(20).shot("glare-floodlight").run(stage -> stage.level.setDayTime(6000)).waitTicks(10)
                .shot("glare-day").run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /** 50 fixed Floodlights aimed at the ground: what their rays and light blocks cost the server. */
    static Script loadFlood() {
        int ticks = 200;
        long[] start = new long[2];
        return new Script("load-flood")
                .run(stage -> {
                    stage.night();
                    for (int x = 0; x < 10; x++) {
                        for (int z = 0; z < 5; z++) {
                            BlockPos light = stage.at(x * 8 - 36, 6, z * 8 + 2);
                            stage.set(light, LightworksBlocks.FLOODLIGHT.getDefaultState()
                                    .setValue(DirectionalLightBlock.FACING, Direction.DOWN));
                        }
                    }
                    stage.hover(0, 30, -14);
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 0, 18)).add(0.5, 0, 0.5);
                })
                .waitTicks(100)
                .run(stage -> {
                    int lights = 0;
                    for (BlockPos pos : BlockPos.betweenClosed(stage.at(-44, -1, -6), stage.at(44, 8, 44))) {
                        if (stage.level.getBlockState(pos).is(LightworksBlocks.BEAM_LIGHT.get())) {
                            lights++;
                        }
                    }
                    stage.log("load-flood: %d light blocks", lights);
                    start[0] = Timer.SERVER_BLOCK.nanos();
                    start[1] = Timer.SERVER_BLOCK.calls();
                    SelfTestBridge.frameStats = "load-flood";
                })
                .waitTicks(ticks)
                .run(stage -> {
                    SelfTestBridge.frameStats = null;
                    stage.log("load-flood: our block entities %.1f us per tick (%d light ticks), server tick %.2f ms",
                            (Timer.SERVER_BLOCK.nanos() - start[0]) / 1000.0 / ticks, Timer.SERVER_BLOCK.calls() - start[1],
                            stage.level.getServer().getAverageTickTimeNanos() / 1e6);
                })
                .shot("load-flood-0")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * A green Searchlight on a head turning level over flat ground, as in the owner's screenshots: the colour should
     * follow the beam smoothly, not appear now and then. Film and look; the tint must stay on the beam's end.
     */
    static Script levelDyed() {
        BlockPos[] at = new BlockPos[3];
        return new Script("level-dyed")
                .run(stage -> {
                    stage.night();
                    at[0] = stage.at(0, 0, 8);
                    at[1] = at[0].above();
                    at[2] = at[1].above();
                    stage.set(at[0], AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
                    stage.set(at[1], LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                            .setValue(PanTiltHeadBlock.FACING, Direction.UP));
                    stage.set(at[2], searchlight(Direction.UP));
                    stage.lightAt(at[2]).lightState().setColour(DyeColor.GREEN);
                    stage.lightAt(at[2]).notifyUpdate();
                    setMotorSpeed(stage, at[0], 16);
                    stage.hover(0, 14, 0);
                    SelfTestBridge.focus = Vec3.atCenterOf(at[2]);
                })
                .waitTicks(30)
                .run(stage -> SelfTestBridge.recording = "level-dyed")
                .waitTicks(100)
                .run(stage -> SelfTestBridge.recording = null)
                .shot("level-dyed-0")
                .run(stage -> {
                    setMotorSpeed(stage, at[0], 0);
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * A Floodlight turning under a head, pointing almost straight down, undyed then red, from higher and higher up: a
     * moving beam's lit square grows with height. The square should stay on the ground as far as the beam lights it,
     * and fade rather than vanish at once if it grows past the limit. Logs the tinted faces at each height.
     */
    static Script movingFlood() {
        int[] heights = {8, 12, 15, 18, 22};
        DyeColor[] colours = {null, DyeColor.RED};
        BlockPos[] at = new BlockPos[3];
        Script script = new Script("moving-flood").run(stage -> stage.night());
        for (DyeColor colour : colours) {
            for (int height : heights) {
                String name = "moving-flood-" + (colour == null ? "white" : "red") + "-" + height;
                script.run(stage -> {
                    stage.restoreBlocks();
                    at[0] = stage.at(0, height + 2, 16);
                    at[1] = at[0].below();
                    at[2] = at[0].below(2);
                    stage.set(at[1], LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                            .setValue(PanTiltHeadBlock.FACING, Direction.DOWN));
                    stage.set(at[2], LightworksBlocks.FLOODLIGHT.getDefaultState()
                            .setValue(DirectionalLightBlock.FACING, Direction.DOWN));
                    stage.lightAt(at[2]).lightState().setColour(colour);
                    stage.lightAt(at[2]).notifyUpdate();
                    // Signal 14 from a plate on glass: tilt 84 degrees, so the spot circles and keeps moving
                    stage.set(at[1].east().below(), Blocks.GLASS.defaultBlockState());
                    stage.set(at[1].east(), Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState()
                            .setValue(WeightedPressurePlateBlock.POWER, 14));
                    stage.set(at[0], AllBlocks.CREATIVE_MOTOR.getDefaultState()
                            .setValue(CreativeMotorBlock.FACING, Direction.DOWN));
                    setMotorSpeed(stage, at[0], 16);
                    stage.hover(0, 34, -6);
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 0, 16)).add(0.5, 0, 0.5);
                }).waitTicks(45).run(stage -> stage.log("%s: %d tinted faces", name, ProjectedTint.activeFaces())).shot(name);
            }
        }
        return script.run(stage -> {
            SelfTestBridge.focus = null;
            stage.restoreBlocks();
        });
    }

    /** 50 of {@link #panTiltLevel}'s turning level lights in view: what tracing them costs, frame and client tick. */
    static Script loadLevel() {
        int ticks = 200;
        long[] start = new long[2];
        return new Script("load-level")
                .run(stage -> {
                    stage.night();
                    for (int x = -9; x <= 9; x += 2) {
                        for (int z = 0; z <= 8; z += 2) {
                            BlockPos motor = stage.at(x, 0, z);
                            stage.set(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                                    .setValue(CreativeMotorBlock.FACING, Direction.UP));
                            stage.set(motor.above(), LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                                    .setValue(PanTiltHeadBlock.FACING, Direction.UP));
                            stage.set(motor.above(2), searchlight(Direction.UP));
                            setMotorSpeed(stage, motor, 16);
                        }
                    }
                    stage.hover(0, 12, -8);
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 0, 4)).add(0.5, 0, 0.5);
                })
                .waitTicks(60)
                .run(stage -> {
                    start[0] = Timer.CLIENT_BLOCK.nanos();
                    start[1] = Timer.CLIENT_BLOCK.calls();
                    SelfTestBridge.frameStats = "load-level";
                })
                .waitTicks(ticks)
                .run(stage -> {
                    SelfTestBridge.frameStats = null;
                    stage.log("load-level: our client block entities %.1f us per tick (%d light ticks)",
                            (Timer.CLIENT_BLOCK.nanos() - start[0]) / 1000.0 / ticks, Timer.CLIENT_BLOCK.calls() - start[1]);
                })
                .shot("load-level-0")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * Close looks at a Searchlight's base on a Pan-Tilt Head. In {@code head-base-0-turned} the plate lines up with
     * the turntable, not the casing. In {@code head-base-1-no-head} the head is gone, and the plate is back in the
     * block model, square to the block; a Floodlight and a Headlight on heads of their own stay turned beside it.
     * In {@code head-base-2-on-bearing} a head and light at pan 30 hang from a
     * turning arm: plate and turntable still line up.
     */
    static Script headBase() {
        BlockPos[] at = new BlockPos[3];
        BlockPos[] arm = new BlockPos[1];
        BlockPos[] others = new BlockPos[2];
        return new Script("head-base")
                .run(stage -> {
                    stage.night();
                    at[0] = stage.at(0, 0, 4);
                    at[1] = at[0].above();
                    at[2] = at[1].above();
                    stage.set(at[0], AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
                    stage.set(at[1], LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                            .setValue(PanTiltHeadBlock.FACING, Direction.UP));
                    stage.set(at[2], searchlight(Direction.UP));
                    setMotorSpeed(stage, at[0], 16);
                    int x = -2;
                    for (BlockState light : new BlockState[] {LightworksBlocks.FLOODLIGHT.getDefaultState(),
                            LightworksBlocks.HEADLIGHT.getDefaultState()}) {
                        BlockPos motor = stage.at(x, 0, 4);
                        stage.set(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                                .setValue(CreativeMotorBlock.FACING, Direction.UP));
                        stage.set(motor.above(), LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                                .setValue(PanTiltHeadBlock.FACING, Direction.UP));
                        stage.set(motor.above(2), light.setValue(DirectionalLightBlock.FACING, Direction.UP));
                        setMotorSpeed(stage, motor, 16);
                        others[x < 0 ? 0 : 1] = motor;
                        x += 4;
                    }
                    stage.hover(1.5, 4, 2.5);
                    SelfTestBridge.focus = Vec3.atCenterOf(at[2]);
                })
                // About 30 degrees at 16 RPM
                .waitTicks(7)
                .run(stage -> {
                    setMotorSpeed(stage, at[0], 0);
                    setMotorSpeed(stage, others[0], 0);
                    setMotorSpeed(stage, others[1], 0);
                })
                .waitTicks(20)
                .run(stage -> stage.log("head-base: on a head, on_head %s",
                        stage.level.getBlockState(at[2]).getValue(DirectionalLightBlock.ON_HEAD)))
                .shot("head-base-0-turned")
                .run(stage -> stage.set(at[1], AllBlocks.BRASS_CASING.getDefaultState()))
                .waitTicks(10)
                .run(stage -> stage.log("head-base: head removed, on_head %s",
                        stage.level.getBlockState(at[2]).getValue(DirectionalLightBlock.ON_HEAD)))
                .shot("head-base-1-no-head")
                .run(stage -> {
                    BlockPos motor = stage.at(0, 9, 8);
                    BlockPos bearing = motor.above();
                    arm[0] = motor;
                    stage.reserve(bearing.offset(-4, -1, -4), bearing.offset(4, 2, 4));
                    for (int x = 0; x <= 3; x++) {
                        stage.set(bearing.offset(x, 1, 0), Blocks.OAK_PLANKS.defaultBlockState());
                    }
                    BlockPos head = bearing.offset(3, 0, 0);
                    stage.set(head, LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                            .setValue(PanTiltHeadBlock.FACING, Direction.DOWN));
                    stage.set(head.below(), searchlight(Direction.DOWN));
                    PanTiltHeadBlockEntity entity = (PanTiltHeadBlockEntity) stage.level.getBlockEntity(head);
                    CompoundTag tag = entity.saveWithFullMetadata(stage.level.registryAccess());
                    tag.putFloat("Pan", 30);
                    entity.loadWithComponents(tag, stage.level.registryAccess());
                    entity.sendData();
                    stage.level.addFreshEntity(new SuperGlueEntity(stage.level,
                            AABB.encapsulatingFullBlocks(bearing.above(), bearing.offset(3, 1, 0))));
                    stage.level.addFreshEntity(new SuperGlueEntity(stage.level,
                            AABB.encapsulatingFullBlocks(head.below(), bearing.offset(3, 1, 0))));
                    stage.set(bearing, AllBlocks.MECHANICAL_BEARING.getDefaultState()
                            .setValue(DirectionalKineticBlock.FACING, Direction.UP));
                    stage.hover(4.5, 6.5, 5.5);
                    SelfTestBridge.focus = Vec3.atCenterOf(bearing.below());
                })
                // Time for the light to read the head, so the contraption takes on_head and the angles along
                .waitTicks(10)
                .run(stage -> {
                    stage.set(arm[0], AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
                    setMotorSpeed(stage, arm[0], 4);
                })
                .waitTicks(40)
                .shot("head-base-2-on-bearing")
                .run(stage -> {
                    setMotorSpeed(stage, arm[0], 0);
                    SelfTestBridge.focus = null;
                    stage.discardContraptions(arm[0], 6);
                    stage.restoreBlocks();
                    stage.discardContraptions(arm[0], 6);
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
     * Logs the block light beside each at one moment (a blinking lamp's follows its pattern, so it can read
     * lower than the steady one's 11) and each pattern's lens over 30 ticks.
     */
    static Script warning() {
        BlockPos[] lamps = new BlockPos[6];
        return new Script("warning")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, 0);
                    for (int i = 0; i < lamps.length; i++) {
                        lamps[i] = stage.at(-5 + 2 * i, 0, 4);
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
                    SelfTestBridge.showGui = true;
                    SelfTestBridge.valueBoard = lamps[1];
                })
                .waitTicks(20)
                .shot("warning-1-board")
                .run(stage -> {
                    SelfTestBridge.valueBoard = null;
                    SelfTestBridge.showGui = false;
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
                "warning_light", "headlight", "tail_light", "edge_light")
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
                    lawn(stage);
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
                    stage.set(d.offset(4, 1, 0), LightworksBlocks.EDGE_LIGHT.getDefaultState());
                    stage.set(d.offset(0, 0, -2), LightworksBlocks.FLOODLIGHT.getDefaultState()
                            .setValue(DirectionalLightBlock.FACING, Direction.NORTH));
                    stage.lightAt(d.offset(0, 0, -2)).aimAlong(new Vec3(0, -0.7, -1));
                    stage.set(d.offset(-3, 0, -2), headlight(Direction.NORTH));
                    stage.set(d.offset(3, 0, -2), headlight(Direction.NORTH));
                    stage.set(d.offset(-3, 0, 2), LightworksBlocks.TAIL_LIGHT.getDefaultState()
                            .setValue(SignalLampBlock.FACING, Direction.SOUTH));
                    stage.set(d.offset(3, 0, 2), LightworksBlocks.TAIL_LIGHT.getDefaultState()
                            .setValue(SignalLampBlock.FACING, Direction.SOUTH));
                    SelfTestBridge.focus = Vec3.atCenterOf(d.above());
                })
                .waitTicks(40)
                .shot("gallery-0-day");
        closeUps(script, deck);
        script.run(stage -> {
                    stage.hover(0, 10, -4);
                    SelfTestBridge.focus = Vec3.atCenterOf(deck[0].above());
                    stage.level.setDayTime(18000);
                })
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

    /**
     * Daylight close-ups of every gallery block from about two blocks away (gallery-close-*), for judging
     * models and textures; one extra Rotating Beacon floats above the deck, seen from below, to show the
     * half shaft in its bottom.
     */
    private static void closeUps(Script script, BlockPos[] deck) {
        record View(String name, int dx, int dy, int dz, Vec3 towardCamera) {
        }
        Vec3 front = new Vec3(0.7, 0.8, -1.5);
        List<View> views = List.of(
                new View("searchlight", -4, 1, 0, front),
                new View("pan-tilt-head", -2, 1, 0, new Vec3(0.8, 0.9, -1.7)),
                new View("rotating-beacon", 0, 1, 0, front),
                new View("warning-light", 2, 1, 0, front),
                new View("floodlight", 0, 0, -2, new Vec3(0.9, 0.6, -1.3)),
                new View("headlight", 3, 0, -2, front),
                new View("tail-light", 3, 0, 2, new Vec3(0.7, 0.8, 1.5)),
                new View("beacon-underside", -1, 3, -3, new Vec3(0.9, -1.2, -1.2)));
        script.run(stage -> stage.set(deck[0].offset(-1, 3, -3), LightworksBlocks.ROTATING_BEACON.getDefaultState()
                .setValue(RotatingBeaconBlock.FACING, Direction.UP)));
        for (View view : views) {
            script.run(stage -> {
                        Vec3 block = Vec3.atCenterOf(deck[0].offset(view.dx(), view.dy(), view.dz()));
                        Vec3 eye = block.add(view.towardCamera().normalize().scale(2.2));
                        BlockPos origin = stage.at(0, 0, 0);
                        stage.hover(eye.x - origin.getX() - 0.5, eye.y - 1.62 - origin.getY(), eye.z - origin.getZ() - 0.5);
                        SelfTestBridge.focus = block;
                    })
                    .waitTicks(10)
                    .shot("gallery-close-" + view.name());
        }
    }

    /**
     * A real Create train: 49 blocks of track, a station, one bogey under three casings with Headlights at
     * both ends, a Tail and Brake Light and Train Controls each way, assembled at the station and driven
     * by setting its speed (as a driver would): east for 60 ticks, a stop, then west for 60 ticks and a
     * stop. The probe logs which Headlight is lit (they should swap when it reverses) and the brake light.
     * <p>
     * Not finished (Oct 29): track, station, assembly mode and bogey all work, but
     * {@code StationBlockEntity.assemble} returns without a train, an error or a warning. The real-train
     * check stays with the owner until this is solved. Not in the default set.
     */
    static Script train() {
        BlockPos[] at = new BlockPos[3];
        Train[] train = new Train[1];
        ItemStack[] hand = new ItemStack[1];
        Set<UUID> known = new HashSet<>();
        return new Script("train")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, -6);
                    at[0] = stage.at(0, 0, 4);
                    stage.reserve(stage.at(-30, 0, 2), stage.at(30, 4, 7));
                    for (int x = -24; x <= 24; x++) {
                        stage.set(stage.at(x, 0, 4), AllBlocks.TRACK.getDefaultState()
                                .setValue(TrackBlock.SHAPE, TrackShape.XO));
                    }
                    hand[0] = stage.player.getMainHandItem().copy();
                    SelfTestBridge.focus = Vec3.atCenterOf(at[0].above());
                })
                // The track joins Create's rail network a tick after it is placed
                .waitTicks(10)
                .run(stage -> {
                    // The station item, used as a player would: on the track, then where the station goes
                    stage.player.setYRot(-90);
                    stage.player.setXRot(30);
                    stage.player.setItemInHand(InteractionHand.MAIN_HAND, AllBlocks.TRACK_STATION.asStack());
                    use(stage, at[0], Direction.UP);
                    use(stage, stage.at(0, -1, 6), Direction.UP);
                    at[1] = stage.at(0, 0, 6);
                    stage.log("train: station %s", stage.level.getBlockEntity(at[1]) instanceof StationBlockEntity
                            ? "placed" : "missing (" + stage.level.getBlockState(at[1]) + ")");
                })
                .waitTicks(5)
                .run(stage -> {
                    if (!(stage.level.getBlockEntity(at[1]) instanceof StationBlockEntity station)) {
                        return;
                    }
                    boolean assembling = station.enterAssemblyMode(stage.player);
                    // The first bogey goes on the track one block past the station's, in the assembly direction
                    Direction way = station.getAssemblyDirection();
                    BlockPos clicked = way == null ? at[0] : at[0].relative(way);
                    stage.player.setItemInHand(InteractionHand.MAIN_HAND, AllBlocks.RAILWAY_CASING.asStack(4));
                    BlockState track = stage.level.getBlockState(clicked);
                    track.useItemOn(stage.player.getMainHandItem(), stage.level, stage.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false));
                    BlockPos bogey = stage.level.getBlockState(clicked.above()).getBlock() instanceof AbstractBogeyBlock<?>
                            ? clicked.above() : null;
                    stage.log("train: assembly mode %s, direction %s, bogey %s", assembling, way,
                            bogey == null ? "missing" : "at " + bogey);
                    if (bogey == null) {
                        return;
                    }
                    at[2] = bogey;
                    BlockPos body = bogey.above();
                    for (int x = -1; x <= 1; x++) {
                        stage.set(body.offset(x, 0, 0), AllBlocks.RAILWAY_CASING.getDefaultState());
                    }
                    stage.set(body.offset(2, 0, 0), headlight(Direction.EAST));
                    stage.set(body.offset(-2, 0, 0), headlight(Direction.WEST));
                    stage.set(body.offset(-1, 0, -1), LightworksBlocks.TAIL_LIGHT.getDefaultState()
                            .setValue(SignalLampBlock.FACING, Direction.NORTH));
                    stage.set(body.offset(1, 1, 0), AllBlocks.TRAIN_CONTROLS.getDefaultState()
                            .setValue(ControlsBlock.FACING, Direction.EAST));
                    stage.set(body.offset(-1, 1, 0), AllBlocks.TRAIN_CONTROLS.getDefaultState()
                            .setValue(ControlsBlock.FACING, Direction.WEST));
                    // A bogey sticks only to its sides along the track: the body on top is glued to it, the glue
                    // reaching down to take the bogey in
                    stage.level.addFreshEntity(new SuperGlueEntity(stage.level,
                            AABB.encapsulatingFullBlocks(body.offset(-2, -1, -1), body.offset(2, 1, 0))));
                })
                .waitTicks(5)
                .run(stage -> {
                    if (at[2] == null || !(stage.level.getBlockEntity(at[1]) instanceof StationBlockEntity station)) {
                        return;
                    }
                    known.addAll(Create.RAILWAYS.trains.keySet());
                    station.refreshAssemblyInfo();
                    stage.log("train: before assembly: network station %s, assembling %s, bogeys %s",
                            station.getStation() != null, station.isAssembling(), field(station, "bogeyCount"));
                    logAssemblyChecks(stage, station);
                    station.assemble(stage.player.getUUID());
                    stage.log("train: assembly error %s, trains now %d, assembling %s", assemblyError(station),
                            Create.RAILWAYS.trains.size(), station.isAssembling());
                })
                .waitTicks(5)
                .run(stage -> {
                    // The newly assembled train: the one Create did not know before
                    for (Train candidate : Create.RAILWAYS.trains.values()) {
                        if (!known.contains(candidate.id)) {
                            train[0] = candidate;
                        }
                    }
                    stage.log("train: %s; %d contraption entities near", train[0] != null
                                    ? "assembled, " + train[0].carriages.size() + " carriage"
                                    : "not assembled, " + Create.RAILWAYS.trains.size() + " trains known",
                            stage.level.getEntitiesOfClass(AbstractContraptionEntity.class,
                                    new AABB(at[0]).inflate(30)).size());
                })
                .waitTicks(15)
                .run(stage -> {
                    SelfTestBridge.probeGroundY = stage.origin.getY();
                    SelfTestBridge.probe = "train";
                    SelfTestBridge.recording = "train-run";
                })
                .repeat(60, (stage, i) -> drive(train[0], 0.3))
                .repeat(30, (stage, i) -> drive(train[0], 0))
                .repeat(60, (stage, i) -> drive(train[0], -0.3))
                .repeat(30, (stage, i) -> drive(train[0], 0))
                .run(stage -> {
                    SelfTestBridge.recording = null;
                    SelfTestBridge.probe = null;
                })
                .shot("train-0-stopped")
                .run(stage -> {
                    if (train[0] != null) {
                        Create.RAILWAYS.removeTrain(train[0].id);
                    }
                    stage.player.setItemInHand(InteractionHand.MAIN_HAND, hand[0]);
                    SelfTestBridge.focus = null;
                    stage.discardContraptions(at[0], 30);
                    stage.restoreBlocks();
                    stage.discardContraptions(at[0], 30);
                });
    }

    /** Sets a train's speed for this tick, as its controls do (blocks per tick; negative backwards). */
    private static void drive(Train train, double speed) {
        if (train != null) {
            train.manualTick = true;
            train.targetSpeed = speed;
            train.speed = speed;
        }
    }

    /** Uses the item in the player's main hand on a block face, as a right-click would. */
    private static void use(Stage stage, BlockPos pos, Direction face) {
        ItemStack stack = stage.player.getMainHandItem();
        stack.useOn(new UseOnContext(stage.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false)));
    }

    /** A private field of a Create class, for the log. */
    private static Object field(Object owner, String name) {
        try {
            java.lang.reflect.Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            return "?";
        }
    }

    /** Why a station did not assemble: its last error, read like the station screen does. */
    /**
     * Create's station assembly gives up silently in a few places: no bogeys found, the station's track point not valid,
     * no track end in the assembly direction, no rail graph there. Logs each, so a scene that makes no train says why.
     */
    private static void logAssemblyChecks(Stage stage, StationBlockEntity station) {
        int[] bogeys = (int[]) field(station, "bogeyLocations") instanceof int[] found ? found : null;
        var point = station.edgePoint;
        boolean valid = point != null && point.hasValidTrack();
        String ends = "?";
        String graph = "?";
        if (valid) {
            BlockPos track = point.getGlobalPosition();
            BlockState state = point.getTrackBlockState();
            var block = point.getTrack();
            Vec3 centre = Vec3.atBottomCenterOf(track).add(0, block.getElevationAtCenter(stage.level, track, state), 0);
            Direction way = station.getAssemblyDirection();
            StringBuilder found = new StringBuilder();
            com.simibubi.create.content.trains.graph.TrackNodeLocation match = null;
            for (var end : block.getConnected(stage.level, track, state, true, null)) {
                Vec3 to = end.getLocation().subtract(centre).normalize();
                found.append(String.format(" (%.2f %.2f %.2f)", to.x, to.y, to.z));
                if (way != null && Vec3.atLowerCornerOf(way.getNormal()).distanceToSqr(to) < 1e-6) {
                    match = end;
                }
            }
            ends = found + (match == null ? ", none towards " + way : ", one towards " + way);
            graph = match == null ? "-" : String.valueOf(Create.RAILWAYS.getGraph(stage.level, match) != null);
        }
        stage.log("train: checks: bogey offsets %s, track point valid %s, track ends%s, rail graph %s",
                bogeys == null ? "none" : java.util.Arrays.toString(bogeys), valid, ends, graph);
        if (valid && station.getAssemblyDirection() != null) {
            // What Create's carriage search finds from the first bogey, done here on a copy
            Direction way = station.getAssemblyDirection();
            BlockPos bogey = point.getGlobalPosition().above().relative(way, 1);
            var carriage = new com.simibubi.create.content.trains.entity.CarriageContraption(way);
            String result;
            try {
                result = "assembles " + carriage.assemble(stage.level, bogey);
            } catch (Exception e) {
                result = "throws " + e;
            }
            stage.log("train: carriage search from %s (%s, above it %s): %s, %s blocks, %s bogeys", bogey,
                    stage.level.getBlockState(bogey), stage.level.getBlockState(bogey.above()), result,
                    carriage.getBlocks().size(), field(carriage, "bogeys"));
        }
    }

    private static String assemblyError(StationBlockEntity station) {
        try {
            java.lang.reflect.Field field = StationBlockEntity.class.getDeclaredField("lastException");
            field.setAccessible(true);
            Object error = field.get(station);
            // Create keeps the reason in the exception's component, not its message
            return error instanceof com.simibubi.create.content.contraptions.AssemblyException assembly
                    ? assembly.component.getString() + " (carriage " + field(station, "failedCarriageIndex") + ")"
                    : String.valueOf(error);
        } catch (ReflectiveOperationException e) {
            return "unknown (" + e + ")";
        }
    }

    /**
     * Close-up at night from the side: a Searchlight, a Floodlight and a stopped Rotating Beacon, all
     * shining east; the beam should start flush on each lens and be the lens's size.
     */
    static Script closeup() {
        return new Script("closeup")
                .run(stage -> {
                    stage.night();
                    BlockPos searchlight = stage.at(0, 0, 4);
                    stage.set(searchlight, searchlight(Direction.UP));
                    stage.lightAt(searchlight).aimAlong(new Vec3(1, 0, 0));
                    BlockPos floodlight = stage.at(0, 0, 6);
                    stage.set(floodlight.west(), Blocks.STONE.defaultBlockState());
                    stage.set(floodlight, LightworksBlocks.FLOODLIGHT.getDefaultState()
                            .setValue(DirectionalLightBlock.FACING, Direction.EAST));
                    stage.set(stage.at(0, 0, 8), LightworksBlocks.ROTATING_BEACON.getDefaultState()
                            .setValue(RotatingBeaconBlock.FACING, Direction.UP));
                    stage.hover(-1.5, 1.2, 1.5);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(1, 0, 5));
                })
                .waitTicks(40)
                .run(stage -> {
                    // Every light the beams placed: level-aimed beams light where their lower edge lands
                    StringBuilder lights = new StringBuilder();
                    for (BlockPos pos : BlockPos.betweenClosed(stage.at(-2, -1, 0), stage.at(30, 4, 12))) {
                        BlockState state = stage.level.getBlockState(pos);
                        if (state.is(LightworksBlocks.BEAM_LIGHT.get())) {
                            lights.append(String.format(" (%d %d %d)=%d", pos.getX() - stage.origin.getX(),
                                    pos.getY() - stage.origin.getY(), pos.getZ() - stage.origin.getZ(),
                                    state.getValue(BlockStateProperties.LEVEL)));
                        }
                    }
                    stage.log("closeup: light blocks relative to the site:%s", lights.length() == 0 ? " none" : lights);
                })
                .shot("closeup-0")
                // Right beside the beacon at lens height, as a player standing next to it sees it
                .run(stage -> {
                    stage.hover(-1.3, 0.2, 7.5);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 0, 8)).add(0, 0.6, 0);
                })
                .waitTicks(10)
                .shot("closeup-1-beside-beacon")
                // Standing in the Searchlight's beam, looking along it
                .run(stage -> {
                    stage.hover(2.0, 0.0, 3.5);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(12, 0, 4));
                })
                .waitTicks(10)
                .shot("closeup-2-in-beam")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * Overlapping coloured light, at night on white wool: red, green and blue Floodlights side by side, lighting
     * one wall. Where two overlap it should read yellow, cyan or magenta and where all three do near white,
     * never darker than the wool. Then 24 Floodlights in those colours on the same wall, for the cost: the log
     * gives the milliseconds a frame spent mixing and drawing the tint.
     */
    static Script colourMix() {
        long[] timed = new long[2];
        return new Script("colour-mix")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, -3);
                    for (int x = -9; x <= 9; x++) {
                        for (int z = 0; z <= 11; z++) {
                            stage.set(stage.at(x, -1, z), Blocks.WHITE_WOOL.defaultBlockState());
                        }
                        for (int y = 0; y <= 8; y++) {
                            stage.set(stage.at(x, y, 11), Blocks.WHITE_WOOL.defaultBlockState());
                        }
                    }
                    DyeColor[] colours = {DyeColor.RED, DyeColor.LIME, DyeColor.BLUE};
                    for (int i = 0; i < 3; i++) {
                        mixLight(stage, stage.at(-3 + i * 3, 0, 3), colours[i]);
                    }
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 3, 10));
                })
                .waitTicks(40)
                .shot("colour-mix-0")
                .run(stage -> {
                    DyeColor[] colours = {DyeColor.RED, DyeColor.LIME, DyeColor.BLUE};
                    for (int i = 0; i < 24; i++) {
                        mixLight(stage, stage.at(-6 + i % 6 * 2 + (i / 6 % 2), 0, 1 + i / 6), colours[i % 3]);
                    }
                })
                .waitTicks(40)
                .run(stage -> {
                    timed[0] = Timer.TINT.nanos();
                    timed[1] = Timer.TINT.calls();
                })
                .waitTicks(100)
                .run(stage -> {
                    long frames = Math.max(1, Timer.TINT.calls() - timed[1]);
                    stage.log("colour-mix: %.3f ms a frame for the tint over %d frames, %d tinted faces from all lights",
                            (Timer.TINT.nanos() - timed[0]) / 1e6 / frames, frames, ProjectedTint.activeFaces());
                })
                .shot("colour-mix-1-many")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                    stage.stand(0, 0);
                });
    }

    private static void mixLight(Stage stage, BlockPos pos, DyeColor colour) {
        stage.set(pos, LightworksBlocks.FLOODLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
        DirectionalLightBlockEntity light = stage.lightAt(pos);
        light.aimAlong(new Vec3(0, 0.3, 1));
        light.lightState().setColour(colour);
        light.notifyUpdate();
    }

    /**
     * Coloured light, at night on white wool: a red-dyed Searchlight on a wool wall, a blue-dyed Floodlight
     * on the wool floor, and a Tail Light (red) and a Warning Light (amber) each beside the wall. The wool takes their colours ({@code ProjectedTint}); the shot's log
     * line gives the number of tinted faces.
     */
    static Script colour() {
        BlockPos[] beaconBox = new BlockPos[1];
        return new Script("colour")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, -2);
                    for (int x = -6; x <= 6; x++) {
                        for (int z = 2; z <= 10; z++) {
                            stage.set(stage.at(x, -1, z), Blocks.WHITE_WOOL.defaultBlockState());
                        }
                        for (int y = 0; y <= 4; y++) {
                            stage.set(stage.at(x, y, 10), Blocks.WHITE_WOOL.defaultBlockState());
                        }
                    }
                    BlockPos red = stage.at(-3, 0, 3);
                    stage.set(red, searchlight(Direction.UP));
                    stage.lightAt(red).aimAlong(Vec3.atCenterOf(stage.at(-3, 2, 10)).subtract(Vec3.atCenterOf(red)));
                    stage.lightAt(red).lightState().setColour(DyeColor.RED);
                    stage.lightAt(red).notifyUpdate();
                    BlockPos blue = stage.at(3, 4, 4);
                    stage.set(blue.above(), Blocks.WHITE_WOOL.defaultBlockState());
                    stage.set(blue, LightworksBlocks.FLOODLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.DOWN));
                    stage.lightAt(blue).lightState().setColour(DyeColor.BLUE);
                    stage.lightAt(blue).notifyUpdate();
                    stage.set(stage.at(-5, 0, 9), LightworksBlocks.TAIL_LIGHT.getDefaultState()
                            .setValue(SignalLampBlock.FACING, Direction.UP));
                    stage.set(stage.at(5, 0, 9), LightworksBlocks.WARNING_LIGHT.getDefaultState()
                            .setValue(SignalLampBlock.FACING, Direction.UP));
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 1, 8));
                })
                .waitTicks(40)
                .shot("colour-0")
                .run(stage -> {
                    stage.restoreBlocks();
                    // The owner's scene: a light-blue beacon in a box of wool walls with open corners
                    beaconBox[0] = stage.at(0, 0, 8);
                    BlockPos motor = beaconBox[0];
                    for (int d = -2; d <= 2; d++) {
                        for (int y = 0; y <= 2; y++) {
                            stage.set(motor.offset(d, y, -4), Blocks.WHITE_WOOL.defaultBlockState());
                            stage.set(motor.offset(d, y, 4), Blocks.WHITE_WOOL.defaultBlockState());
                            stage.set(motor.offset(-4, y, d), Blocks.WHITE_WOOL.defaultBlockState());
                            stage.set(motor.offset(4, y, d), Blocks.WHITE_WOOL.defaultBlockState());
                        }
                    }
                    stage.set(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
                    setMotorSpeed(stage, motor, 0);
                    stage.set(motor.above(), LightworksBlocks.ROTATING_BEACON.getDefaultState()
                            .setValue(RotatingBeaconBlock.FACING, Direction.UP));
                    if (stage.level.getBlockEntity(motor.above()) instanceof RotatingBeaconBlockEntity beacon) {
                        beacon.lightState().setColour(DyeColor.LIGHT_BLUE);
                        beacon.notifyUpdate();
                    }
                    stage.hover(0, 7, 3);
                    SelfTestBridge.focus = Vec3.atCenterOf(motor.above());
                })
                .waitTicks(40)
                .shot("colour-1-beacon-stopped")
                .run(stage -> setMotorSpeed(stage, beaconBox[0], 8))
                .waitTicks(37)
                .shot("colour-2-beacon-turning")
                .run(stage -> {
                    setMotorSpeed(stage, beaconBox[0], 0);
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                    stage.stand(0, 0);
                });
    }

    /**
     * Engineer's Goggles: the player wears them and looks at a dyed, dimmed Searchlight lighting a wall,
     * then at a Warning Light, with the GUI shown: the overlay should list state, aim and colour, and no beam
     * details or debug lines should show.
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
                .run(stage -> {
                    // A notch where the centre ray lands: the Beam line should still name the stone round it
                    stage.set(stage.at(2, 1, 10), Blocks.AIR.defaultBlockState());
                    List<Component> lines = new ArrayList<>();
                    stage.lightAt(at[0]).addToGoggleTooltip(lines, false);
                    stage.log("goggles: with a notch at the centre ray: %s", lines.get(lines.size() - 2).getString().trim());
                    SelfTestBridge.focus = Vec3.atCenterOf(at[1]).add(0, -0.2, 0);
                })
                .waitTicks(10)
                .shot("goggles-1-warning")
                .run(stage -> {
                    SelfTestBridge.showGui = false;
                    SelfTestBridge.focus = null;
                    stage.player.setItemSlot(EquipmentSlot.HEAD, helmet[0]);
                    stage.restoreBlocks();
                });
    }

    /**
     * Aims a light at {@code target} the way a player does: by setting the whole-degree pan and tilt
     * nearest that direction on its boards. Returns what was set.
     */
    private static PanTilt boardAim(Stage stage, BlockPos light, Vec3 target) {
        Vec3 d = target.subtract(Vec3.atCenterOf(light));
        DirectionalLightBlockEntity be = stage.lightAt(light);
        Direction f = be.mountFacing();
        PanTilt set = PanTilt.of(f.getStepX(), f.getStepY(), f.getStepZ(),
                Aim.fromDirection(f.getStepX(), f.getStepY(), f.getStepZ(), d.x, d.y, d.z));
        be.setPanTilt(set);
        return set;
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
     * A Floodlight and a Searchlight at the foot of a stepped grassy slope, both lighting it, filmed while the camera walks
     * past along the bottom, as a player would. The lit squares on the slope must stay in every frame of the clip: they
     * once went out frame by frame while the camera moved, losing the depth test to the slope they lie on. Each tick also
     * logs how many times the lights' renderers ran per frame (2 when nothing skips them; Entity Culling, with
     * {@code -Pdev_conflicts=entityculling}, skips block entities it thinks are hidden, and the visible beam goes with
     * them) and how many faces are painted (steady for fixed lights). Then the camera flies past fast and close, a block a
     * tick three blocks from the slope (filmed: the squares must stay there too), and looks down on the lit steps from
     * above, where some of their sides face away, and at a lit step's edge from close by and a little above: no bright
     * lines may run along the steps' edges.
     */
    static Script walkPast() {
        long[] last = new long[2];
        return new Script("walk-past")
                .run(stage -> {
                    stage.night();
                    for (int x = -14; x <= 14; x++) {
                        for (int z = 3; z <= 22; z++) {
                            // Every third column a step further on, so the steps have sides as natural ground does
                            int top = Math.min(14, z - 2 - (Math.floorMod(x, 3) == 0 ? 1 : 0));
                            for (int y = 0; y < top; y++) {
                                stage.set(stage.at(x, y, z), y == top - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
                                        : Blocks.DIRT.defaultBlockState());
                            }
                        }
                    }
                    BlockPos flood = stage.at(-3, 0, 1);
                    stage.set(flood, LightworksBlocks.FLOODLIGHT.getDefaultState()
                            .setValue(DirectionalLightBlock.FACING, Direction.UP));
                    stage.lightAt(flood).aimAlong(new Vec3(0, 0.5, 1));
                    BlockPos search = stage.at(3, 0, 1);
                    stage.set(search, searchlight(Direction.UP));
                    stage.lightAt(search).aimAlong(new Vec3(0.4, 0.8, 1));
                    stage.hover(-12, 1, -5);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 4, 8));
                })
                .waitTicks(60)
                .shot("walk-past-0-start")
                .run(stage -> {
                    last[0] = Timer.LIGHT_RENDER.calls();
                    last[1] = Timer.TINT.calls();
                    SelfTestBridge.recording = "walk-past";
                })
                .repeat(160, (stage, i) -> {
                    stage.hover(-12 + i * 0.15, 1, -5 + i * 0.02);
                    long renders = Timer.LIGHT_RENDER.calls() - last[0];
                    long frames = Timer.TINT.calls() - last[1];
                    last[0] = Timer.LIGHT_RENDER.calls();
                    last[1] = Timer.TINT.calls();
                    stage.log("walk-past: tick %d camera x %.2f: %d light renders in %d frames, %d painted faces", i,
                            -12 + i * 0.15, renders, frames, ProjectedTint.activeFaces());
                })
                .run(stage -> SelfTestBridge.recording = null)
                .shot("walk-past-1-end")
                .run(stage -> {
                    stage.hover(-14, 2, 0);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 3, 6));
                })
                .waitTicks(20)
                .run(stage -> SelfTestBridge.recording = "walk-past-fast")
                .repeat(28, (stage, i) -> stage.hover(-14 + i, 2, 0))
                // The same flown smoothly, as a player flies: moved between ticks, not teleported once a tick
                .run(stage -> {
                    SelfTestBridge.recording = null;
                    stage.hover(-14, 2, -1);
                })
                .waitTicks(20)
                .run(stage -> {
                    SelfTestBridge.recording = "walk-past-smooth";
                    SelfTestBridge.velocity = new Vec3(0.6, 0, 0.02);
                })
                .waitTicks(45)
                .run(stage -> SelfTestBridge.velocity = new Vec3(-0.6, 0.05, 0))
                .waitTicks(45)
                .run(stage -> {
                    SelfTestBridge.velocity = null;
                    SelfTestBridge.recording = null;
                    stage.hover(-5, 16, -4);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(-1, 4, 8));
                })
                .waitTicks(40)
                .shot("walk-past-2-above")
                .run(stage -> {
                    stage.hover(-7, 10, 2);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(-2, 4, 7));
                })
                .waitTicks(40)
                .shot("walk-past-3-above-close")
                .run(stage -> {
                    stage.hover(-3.5, 4.2, 3.6);
                    SelfTestBridge.focus = new Vec3(stage.origin.getX() - 3, stage.origin.getY() + 4, stage.origin.getZ() + 6);
                })
                .waitTicks(40)
                .shot("walk-past-4-edge-close")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * Someone else's ground round a light, copied block by block from a world: a JSON list of {@code [dx, dy, dz,
     * "block[state]"]} relative to the light, the file named by the system property {@code lightworks.replica}. A
     * Searchlight shining level and east is placed where the light was, 14 above the ground here so the copy's lowest
     * blocks stand on it, and the camera flies smoothly along the beam and back over the lit ground, filmed, then looks
     * down on it from close by. For reproducing what a player saw in their own world.
     */
    static Script replica() {
        String file = System.getProperty("lightworks.replica");
        if (file == null) {
            return new Script("replica").run(stage -> stage.log("replica: skipped, -Dlightworks.replica is not set"));
        }
        BlockPos[] light = new BlockPos[1];
        return new Script("replica")
                .run(stage -> {
                    stage.night();
                    light[0] = stage.at(0, 14, 0);
                    try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(file))) {
                        com.google.gson.JsonArray blocks = com.google.gson.JsonParser.parseReader(reader).getAsJsonArray();
                        int placed = 0;
                        for (com.google.gson.JsonElement element : blocks) {
                            com.google.gson.JsonArray block = element.getAsJsonArray();
                            BlockState state = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
                                    BuiltInRegistries.BLOCK.asLookup(), block.get(3).getAsString(), false).blockState();
                            stage.set(light[0].offset(block.get(0).getAsInt(), block.get(1).getAsInt(), block.get(2).getAsInt()),
                                    state);
                            placed++;
                        }
                        stage.log("replica: placed %d blocks", placed);
                    } catch (Exception e) {
                        stage.log("replica: could not read %s: %s", file, e);
                    }
                    stage.set(light[0], searchlight(Direction.UP));
                    stage.lightAt(light[0]).aimAlong(new Vec3(1, 0, 0));
                    stage.hover(2, 15.4, -3);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(12, 14, 1));
                })
                .waitTicks(100)
                .shot("replica-0-start")
                .run(stage -> {
                    SelfTestBridge.recording = "replica-fly";
                    SelfTestBridge.velocity = new Vec3(0.35, 0, 0.03);
                })
                .waitTicks(60)
                .run(stage -> SelfTestBridge.velocity = new Vec3(-0.35, 0.02, 0.05))
                .waitTicks(60)
                .run(stage -> {
                    SelfTestBridge.velocity = null;
                    SelfTestBridge.recording = null;
                    stage.hover(11, 18, 1);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(14, 13, 2));
                })
                .waitTicks(40)
                .run(stage -> SelfTestBridge.recording = "replica-hover")
                .waitTicks(80)
                .run(stage -> {
                    SelfTestBridge.recording = null;
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * How a beam looks from inside it: a level Searchlight, then a Floodlight, shining east at a white wall 20 blocks
     * off, with the camera in the beam halfway along, looking along it, back at the lamp, to the side and down.
     */
    static Script insideBeam() {
        Script script = new Script("inside-beam").run(stage -> {
            stage.night();
            for (int z = -6; z <= 6; z++) {
                for (int y = 0; y <= 8; y++) {
                    stage.set(stage.at(20, y, z), Blocks.WHITE_CONCRETE.defaultBlockState());
                }
            }
        });
        String[] names = {"searchlight", "floodlight"};
        for (String name : names) {
            BlockPos lamp = BlockPos.ZERO.offset(0, 2, 0);
            script.run(stage -> {
                stage.set(stage.at(0, 2, 0), "searchlight".equals(name) ? searchlight(Direction.UP)
                        : LightworksBlocks.FLOODLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
                stage.lightAt(stage.at(0, 2, 0)).aimAlong(new Vec3(1, 0, 0));
                // The eye on the beam's axis, ten blocks out
                stage.hover(10, 2.5 - 1.62 + 0.5, 0);
            });
            Vec3[] looks = {new Vec3(20, 2.5, 0.5), new Vec3(0, 2.5, 0.5), new Vec3(10.5, 2.5, 10), new Vec3(11, -1, 0.6)};
            String[] views = {"along", "at-lamp", "side", "down"};
            for (int i = 0; i < looks.length; i++) {
                Vec3 look = looks[i];
                script.run(stage -> SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.origin).add(look))
                        .waitTicks(i == 0 ? 60 : 20)
                        .shot("inside-" + name + "-" + views[i]);
            }
            script.run(stage -> stage.set(stage.at(lamp.getX(), lamp.getY(), lamp.getZ()), Blocks.AIR.defaultBlockState()));
        }
        return script.run(stage -> {
            SelfTestBridge.focus = null;
            stage.restoreBlocks();
        });
    }

    /**
     * A level Searchlight shining east along a stone wall, with the camera crossing its beam halfway along, slowly from
     * south of it to north, looking at the wall. Filmed: the haze of the beam's sides must fade in and out as the camera
     * nears them, with no frame-to-frame jump in brightness where the camera passes through a side (logged). Then a shot
     * along the beam with the lamp out of sight behind the camera: the beam must still show (Sodium draws a block entity
     * only while its section is on screen, unless its renderer asks to be drawn off-screen). Last a stone in the beam,
     * seen from the side: the beam must end on its face, with no more than a sliver of haze behind it.
     */
    static Script beamCross() {
        return new Script("beam-cross")
                .run(stage -> {
                    stage.night();
                    for (int x = -2; x <= 24; x++) {
                        for (int y = 0; y <= 7; y++) {
                            stage.set(stage.at(x, y, 6), Blocks.STONE.defaultBlockState());
                        }
                    }
                    BlockPos light = stage.at(0, 2, 0);
                    stage.set(light, searchlight(Direction.UP));
                    stage.lightAt(light).aimAlong(new Vec3(1, 0, 0));
                    stage.hover(10, 1.4, -4);
                    SelfTestBridge.focus = new Vec3(stage.origin.getX() + 10.5, stage.origin.getY() + 2.5,
                            stage.origin.getZ() + 6);
                })
                .waitTicks(60)
                .run(stage -> SelfTestBridge.recording = "beam-cross")
                // From four blocks south of the beam to four north, a tenth of a block a tick
                .repeat(80, (stage, i) -> stage.hover(10, 1.4, -4 + i * 0.1))
                .run(stage -> {
                    SelfTestBridge.recording = null;
                    stage.hover(26, 1.4, -3);
                    SelfTestBridge.focus = new Vec3(stage.origin.getX() + 44, stage.origin.getY() + 2.5, stage.origin.getZ() + 3);
                })
                .waitTicks(40)
                .shot("beam-cross-lamp-behind")
                .run(stage -> {
                    stage.set(stage.at(8, 2, 0), Blocks.STONE.defaultBlockState());
                    stage.hover(8, 1.4, -6);
                    SelfTestBridge.focus = new Vec3(stage.origin.getX() + 8.5, stage.origin.getY() + 2.5, stage.origin.getZ() + 0.5);
                })
                .waitTicks(40)
                .shot("beam-cross-blocked")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * A Searchlight on the ground shining up at a plank platform 8 blocks above, before and after the platform becomes a
     * Sable vehicle. Both times the beam should stop on it, about 7.5 blocks out, and the shots should show the visible
     * beam ending there. The log also gives where Sable's own ray-cast says it hit: on the vehicle that is far off, in
     * the vehicle's plot coordinates, which the beam must not take as its length. Needs Sable.
     */
    static Script vehicleBeam() {
        if (!ModList.get().isLoaded("sable")) {
            return new Script("vehicle-beam").run(stage -> stage.log("vehicle-beam: skipped, Sable is not installed"));
        }
        BlockPos[] light = new BlockPos[1];
        return new Script("vehicle-beam")
                .run(stage -> {
                    stage.night();
                    light[0] = stage.at(0, 0, 4);
                    stage.set(light[0], searchlight(Direction.UP));
                    stage.lightAt(light[0]).aimAlong(new Vec3(0, 1, 0.2));
                    for (int x = -2; x <= 2; x++) {
                        for (int z = 3; z <= 8; z++) {
                            stage.set(stage.at(x, 8, z), Blocks.OAK_PLANKS.defaultBlockState());
                        }
                    }
                    stage.hover(7, 2, -3);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 5, 5));
                })
                .waitTicks(40)
                .run(stage -> logVehicleBeam(stage, light[0], "placed blocks"))
                .shot("vehicle-beam-0-placed")
                .run(stage -> {
                    BlockPos from = stage.at(-2, 8, 3);
                    BlockPos to = stage.at(2, 8, 8);
                    stage.run("sable paused true");
                    stage.run("sable assemble area " + from.getX() + " " + from.getY() + " " + from.getZ() + " "
                            + to.getX() + " " + to.getY() + " " + to.getZ());
                })
                .waitTicks(40)
                .run(stage -> logVehicleBeam(stage, light[0], "Sable vehicle"))
                .shot("vehicle-beam-1-vehicle")
                .run(stage -> {
                    stage.run("sable remove @l");
                    stage.run("sable paused false");
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    private static void logVehicleBeam(Stage stage, BlockPos pos, String phase) {
        DirectionalLightBlockEntity light = stage.lightAt(pos);
        Vec3 from = light.beamOrigin();
        Vec3 dir = light.beamDirection();
        BlockHitResult raw = stage.level.clip(new ClipContext(from, from.add(dir.scale(48)), ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        BeamTrace.Hit hit = BeamTrace.trace(stage.level, from, dir, 48);
        stage.log("vehicle-beam: %s: Sable's ray-cast hit %s %.1f blocks away; the beam stops %.1f blocks out on %s "
                + "(expected about 7.5)", phase, raw.getType(), raw.getLocation().distanceTo(from), hit.distance(), hit.block());
    }

    /**
     * Mod-page still: a Searchlight on the ground lighting a square on a stone wall ten blocks away, and a Floodlight on
     * a pillar beside it lighting the grass in front, at night, from a few blocks to one side.
     */
    static Script showcaseAim() {
        return new Script("showcase-aim")
                .run(stage -> {
                    stage.night();
                    lawn(stage);
                    SelfTestBridge.bright = true;
                    for (int x = -6; x <= 4; x++) {
                        for (int y = 0; y <= 6; y++) {
                            stage.set(stage.at(x, y, 10), Blocks.STONE_BRICKS.defaultBlockState());
                        }
                    }
                    BlockPos search = stage.at(-2, 0, 0);
                    stage.set(search, searchlight(Direction.UP));
                    Vec3 lens = Vec3.atCenterOf(search);
                    stage.lightAt(search).aimAlong(new Vec3(-1, 3, 10).add(Vec3.atLowerCornerOf(stage.origin)).subtract(lens));
                    for (int y = 0; y <= 2; y++) {
                        stage.set(stage.at(3, y, 0), Blocks.STONE_BRICKS.defaultBlockState());
                    }
                    stage.set(stage.at(3, 2, 1), LightworksBlocks.FLOODLIGHT.getDefaultState()
                            .setValue(DirectionalLightBlock.FACING, Direction.SOUTH));
                    stage.lightAt(stage.at(3, 2, 1)).aimAlong(new Vec3(-0.2, -0.6, 1));
                    stage.hover(8, 3, -4);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 1, 5));
                })
                .waitTicks(60)
                .shot("showcase-aim")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    SelfTestBridge.bright = false;
                    stage.restoreBlocks();
                });
    }

    /**
     * Mod-page still: a Rotating Beacon on a block, standing still with its two beams out along north and south, two lit
     * Warning Lights beyond it and a row of Edge Lights in white, blue, green and red in front, at night, seen from the
     * west so the beams cross the picture.
     */
    static Script showcaseSignals() {
        DyeColor[] colours = {DyeColor.WHITE, DyeColor.BLUE, DyeColor.GREEN, DyeColor.RED, DyeColor.GREEN, DyeColor.BLUE,
                DyeColor.WHITE};
        return new Script("showcase-signals")
                .run(stage -> {
                    stage.night();
                    lawn(stage);
                    SelfTestBridge.bright = true;
                    stage.set(stage.at(0, 0, 6), Blocks.STONE_BRICKS.defaultBlockState());
                    stage.set(stage.at(0, 1, 6), LightworksBlocks.ROTATING_BEACON.getDefaultState()
                            .setValue(RotatingBeaconBlock.FACING, Direction.UP));
                    for (int z : new int[] {3, 9}) {
                        stage.set(stage.at(2, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
                        stage.set(stage.at(2, 1, z), LightworksBlocks.WARNING_LIGHT.getDefaultState()
                                .setValue(SignalLampBlock.FACING, Direction.UP));
                    }
                    for (int i = 0; i < colours.length; i++) {
                        stage.set(stage.at(-3, 0, 3 + i), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                                .setValue(EdgeLightBlock.COLOUR, colours[i]));
                    }
                    stage.hover(-9, 3, 6);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 1, 6));
                })
                .waitTicks(60)
                .shot("showcase-signals")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    SelfTestBridge.bright = false;
                    stage.restoreBlocks();
                });
    }

    /**
     * Mod-page still: red, green and blue Searchlights on the grass shining on a white concrete wall, their squares
     * overlapping: red and green make yellow, all three white. Seen from behind the lights.
     */
    static Script showcaseColour() {
        DyeColor[] colours = {DyeColor.RED, DyeColor.LIME, DyeColor.BLUE};
        // Where each beam lands on the wall, relative to the stage
        Vec3[] targets = {new Vec3(-0.6, 3, 10), new Vec3(1.6, 3, 10), new Vec3(0.5, 4.8, 10)};
        return new Script("showcase-colour")
                .run(stage -> {
                    stage.night();
                    lawn(stage);
                    SelfTestBridge.bright = true;
                    for (int x = -6; x <= 6; x++) {
                        for (int y = 0; y <= 7; y++) {
                            stage.set(stage.at(x, y, 10), Blocks.WHITE_CONCRETE.defaultBlockState());
                        }
                    }
                    for (int i = 0; i < colours.length; i++) {
                        BlockPos pos = stage.at(-3 + 3 * i, 0, 0);
                        stage.set(pos, searchlight(Direction.UP));
                        DirectionalLightBlockEntity light = stage.lightAt(pos);
                        light.aimAlong(targets[i].add(Vec3.atLowerCornerOf(stage.origin)).subtract(Vec3.atCenterOf(pos)));
                        light.lightState().setColour(colours[i]);
                        light.notifyUpdate();
                    }
                    stage.hover(0, 2, -6);
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 3, 10)).add(0.5, 0, 0);
                })
                .waitTicks(60)
                .shot("showcase-colour")
                .run(stage -> {
                    SelfTestBridge.focus = null;
                    SelfTestBridge.bright = false;
                    stage.restoreBlocks();
                });
    }

    /**
     * Mod-page still: a small red car of concrete and glass on four black wheels, made a Sable vehicle and driving slowly
     * east on the grass: Headlights lighting the way ahead, Tail Lights behind. Seen from ahead and to one side. Needs
     * Sable.
     */
    static Script showcaseCar() {
        return vehicleShot("showcase-car", stage -> {
            BlockPos c = stage.at(0, 0, 6);
            for (int x : new int[] {-2, 2}) {
                for (int z : new int[] {-1, 1}) {
                    stage.set(c.offset(x, 0, z), Blocks.BLACK_CONCRETE.defaultBlockState());
                }
            }
            for (int x = -3; x <= 3; x++) {
                for (int z = -1; z <= 1; z++) {
                    stage.set(c.offset(x, 1, z), Blocks.RED_CONCRETE.defaultBlockState());
                    if (x >= -2 && x <= 1) {
                        stage.set(c.offset(x, 2, z), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                        stage.set(c.offset(x, 3, z), Blocks.RED_CONCRETE.defaultBlockState());
                    }
                }
            }
            for (int z : new int[] {-1, 1}) {
                stage.set(c.offset(4, 1, z), headlight(Direction.EAST));
                stage.set(c.offset(-4, 1, z), LightworksBlocks.TAIL_LIGHT.getDefaultState()
                        .setValue(SignalLampBlock.FACING, Direction.WEST));
            }
            stage.hover(10, 2.5, -2);
            SelfTestBridge.focus = Vec3.atCenterOf(stage.at(1, 1, 6));
            return new BlockPos[] {c.offset(-4, 0, -1), c.offset(4, 3, 1)};
        });
    }

    /**
     * Mod-page still: a small airship, a white wool envelope over a plank gondola hung on fences, made a Sable vehicle
     * and flying slowly east: Headlights in front, Tail Lights behind, a Searchlight under the gondola lighting the
     * grass ahead, red and green Edge Lights on its sides and a Warning Light on top. Seen from the side. Needs Sable.
     */
    static Script showcaseAirship() {
        return vehicleShot("showcase-airship", stage -> {
            BlockPos[] box = airship(stage);
            stage.hover(-3, 3, -12);
            SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 7, 6));
            return box;
        });
    }

    /**
     * Mod-page banner: a hot-air balloon coming down over a paved runway at night, a real one: Create Aeronautics'
     * envelope round a powered Hot Air Burner, made a Sable vehicle (Aeronautics blocks are looked up by name, so nothing
     * here refers to its code; without it or Sable the scene is skipped). The shot is taken while it is still placed
     * blocks, since a beam stops on a Sable vehicle but paints no colour on it; then it is made a
     * vehicle with physics running, and a second shot shows it rising on its own. Red, green and blue Searchlights beside the runway mix
     * on the balloon's side; the balloon's Searchlight lights the runway ahead and its Headlights look down it; white Edge Lights line the runway, green across the near end and red across the far end;
     * a lighthouse on a headland in the lake to the east turns a Rotating Beacon. Hills and trees rise behind the runway on
     * the left and sand lines the shore. Seen from the south-west, the airship right of the middle, the lighthouse near the
     * right edge and the runway running off into the dark on the left, under the title.
     */
    static Script showcaseBanner() {
        BlockPos[] motor = new BlockPos[1];
        BlockPos[][] balloon = new BlockPos[1][];
        if (!ModList.get().isLoaded("sable") || !ModList.get().isLoaded("aeronautics")) {
            return new Script("showcase-banner")
                    .run(stage -> stage.log("showcase-banner: skipped, it needs Sable and Create Aeronautics"));
        }
        return new Script("showcase-banner")
                .run(stage -> {
                    stage.night();
                    lawn(stage);
                    SelfTestBridge.bright = true;
                    // The lake east of the runway, two blocks deep, reaching past the horizon so its edges never show, and
                    // a sandy shore along it
                    for (BlockPos pos : BlockPos.betweenClosed(stage.at(14, -2, -110), stage.at(110, -1, 40))) {
                        stage.set(pos, Blocks.WATER.defaultBlockState());
                    }
                    for (BlockPos pos : BlockPos.betweenClosed(stage.at(11, -1, -110), stage.at(13, -1, 40))) {
                        stage.set(pos, Blocks.SAND.defaultBlockState());
                    }
                    hill(stage, stage.at(-25, 0, -45), 22, 7);
                    hill(stage, stage.at(-6, 0, -78), 18, 5);
                    for (int[] at : new int[][] {{-8, -10}, {-15, -22}, {-3, -28}, {-20, -6}, {-11, -40}}) {
                        tree(stage, stage.at(at[0], 0, at[1]));
                    }
                    // Runway: paving with dashes down the middle, Edge Lights along it
                    for (int x = 3; x <= 9; x++) {
                        for (int z = -34; z <= 22; z++) {
                            boolean dash = x == 6 && Math.floorMod(z, 6) < 3;
                            stage.set(stage.at(x, -1, z), (dash ? Blocks.WHITE_CONCRETE : Blocks.GRAY_CONCRETE).defaultBlockState());
                        }
                    }
                    for (int z = -32; z <= 20; z += 4) {
                        for (int x : new int[] {2, 10}) {
                            stage.set(stage.at(x, 0, z), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                                    .setValue(EdgeLightBlock.COLOUR, DyeColor.WHITE));
                        }
                    }
                    for (int x = 3; x <= 9; x += 2) {
                        stage.set(stage.at(x, 0, 22), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                                .setValue(EdgeLightBlock.COLOUR, DyeColor.GREEN));
                        stage.set(stage.at(x, 0, -34), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                                .setValue(EdgeLightBlock.COLOUR, DyeColor.RED));
                    }
                    BlockPos[] box = hotAirBalloon(stage, stage.at(6, 4, 6));
                    // Red, green and blue Searchlights west of the runway, their squares overlapping on the balloon's side
                    DyeColor[] colours = {DyeColor.RED, DyeColor.LIME, DyeColor.BLUE};
                    Vec3[] targets = {new Vec3(1.2, 11.0, 5.0), new Vec3(1.2, 11.0, 7.2), new Vec3(1.4, 12.6, 6.1)};
                    for (int i = 0; i < colours.length; i++) {
                        BlockPos pos = stage.at(-7, 0, 3 + 3 * i);
                        stage.set(pos, searchlight(Direction.UP));
                        DirectionalLightBlockEntity light = stage.lightAt(pos);
                        light.aimAlong(targets[i].add(Vec3.atLowerCornerOf(stage.origin)).subtract(Vec3.atCenterOf(pos)));
                        light.lightState().setColour(colours[i]);
                        light.notifyUpdate();
                    }
                    motor[0] = lighthouse(stage, stage.at(36, 0, 6));
                    setMotorSpeed(stage, motor[0], 4);
                    balloon[0] = box;
                    stage.hover(-15, 7, 24);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(-5, 8, 2));
                })
                .waitTicks(100)
                .shot("showcase-banner")
                .run(stage -> {
                    BlockPos[] box = balloon[0];
                    stage.run("sable assemble area " + box[0].getX() + " " + box[0].getY() + " " + box[0].getZ() + " "
                            + box[1].getX() + " " + box[1].getY() + " " + box[1].getZ());
                })
                .waitTicks(40)
                .shot("showcase-banner-rising")
                .run(stage -> {
                    stage.run("sable remove @l");
                    SelfTestBridge.focus = null;
                    SelfTestBridge.bright = false;
                    setMotorSpeed(stage, motor[0], 0);
                    stage.discardContraptions(motor[0], 8);
                    stage.restoreBlocks();
                });
    }

    /**
     * A simple hot-air balloon that really flies, its basket floor centred on {@code g}: a round shell of Create
     * Aeronautics' white envelope, open at the bottom over a Hot Air Burner in a plank basket, the burner powered by a
     * redstone block beside it and turned up to full; fences from the basket's corners to the envelope. Headlights at the
     * front of the basket, Tail Lights at the back, red and green Edge Lights on its left and right rim, a Searchlight
     * under it lighting the ground ahead and a Warning Light on top. Returns the corners of what to assemble.
     */
    private static BlockPos[] hotAirBalloon(Stage stage, BlockPos g) {
        BlockState envelope = aeronautics("white_envelope");
        BlockPos centre = g.above(8);
        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    boolean opening = y < -3 && Math.abs(x) <= 1 && Math.abs(z) <= 1;
                    if (d <= 5.6 && d > 4.5 && !opening) {
                        stage.set(centre.offset(x, y, z), envelope);
                    }
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                stage.set(g.offset(x, 0, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
                boolean rim = x != 0 || z != 0;
                if (rim && !(x == 0 && z == 1)) {
                    stage.set(g.offset(x, 1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
                }
            }
        }
        for (int x : new int[] {-1, 1}) {
            for (int z : new int[] {-1, 1}) {
                for (int y = 2; y <= 4; y++) {
                    stage.set(g.offset(x, y, z), Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
        }
        BlockPos burner = g.above();
        stage.set(burner, aeronautics("adjustable_burner"));
        stage.set(g.offset(0, 1, 1), Blocks.REDSTONE_BLOCK.defaultBlockState());
        if (stage.level.getBlockEntity(burner) instanceof SmartBlockEntity be) {
            ScrollValueBehaviour output = be.getBehaviour(ScrollValueBehaviour.TYPE);
            if (output != null) {
                output.setValue(Integer.MAX_VALUE);
            }
        }
        for (int x : new int[] {-1, 1}) {
            stage.set(g.offset(x, 1, -2), headlight(Direction.NORTH));
            stage.lightAt(g.offset(x, 1, -2)).aimAlong(new Vec3(0, -0.3, -1));
            stage.set(g.offset(x, 1, 2), LightworksBlocks.TAIL_LIGHT.getDefaultState()
                    .setValue(SignalLampBlock.FACING, Direction.SOUTH));
        }
        for (int z : new int[] {-1, 0}) {
            stage.set(g.offset(-2, 1, z), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                    .setValue(EdgeLightBlock.FACING, Direction.WEST).setValue(EdgeLightBlock.COLOUR, DyeColor.RED));
            stage.set(g.offset(2, 1, z), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                    .setValue(EdgeLightBlock.FACING, Direction.EAST).setValue(EdgeLightBlock.COLOUR, DyeColor.GREEN));
        }
        stage.set(g.offset(0, -1, -1), searchlight(Direction.DOWN));
        stage.lightAt(g.offset(0, -1, -1)).aimAlong(new Vec3(0, -1, -0.7));
        stage.set(centre.above(6), LightworksBlocks.WARNING_LIGHT.getDefaultState()
                .setValue(SignalLampBlock.FACING, Direction.UP));
        return new BlockPos[] {g.offset(-6, -1, -6), centre.offset(6, 6, 6)};
    }

    /** A Create Aeronautics block by name, so no code here refers to it; only used when it is installed. */
    private static BlockState aeronautics(String name) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("aeronautics", name)).defaultBlockState();
    }

    /**
     * A lighthouse on a little headland in the lake, its foot at {@code base}: a round tower tapering upwards in red and
     * white bands, a gallery with a rail, and an open lantern under a red roof where a Creative Motor turns a Rotating
     * Beacon. Returns the motor.
     */
    private static BlockPos lighthouse(Stage stage, BlockPos base) {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 7) {
                    continue;
                }
                int top = d < 5 ? (d < 3.5 ? 1 : 0) : -1;
                for (int y = -2; y <= top; y++) {
                    BlockState state = y < top ? Blocks.STONE.defaultBlockState()
                            : d < 3.5 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.SAND.defaultBlockState();
                    stage.set(base.offset(dx, y, dz), state);
                }
            }
        }
        BlockPos foot = base.above(2);
        int height = 13;
        for (int y = 0; y < height; y++) {
            double radius = 2.6 - y * 0.08;
            BlockState band = (y / 3) % 2 == 0 ? Blocks.WHITE_CONCRETE.defaultBlockState()
                    : Blocks.RED_CONCRETE.defaultBlockState();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        stage.set(foot.offset(dx, y, dz), band);
                    }
                }
            }
        }
        BlockPos gallery = foot.above(height);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 <= 10) {
                    stage.set(gallery.offset(dx, 0, dz), Blocks.SMOOTH_STONE.defaultBlockState());
                }
                if (d2 > 5 && d2 <= 10) {
                    stage.set(gallery.offset(dx, 1, dz), Blocks.IRON_BARS.defaultBlockState());
                }
            }
        }
        for (int dx : new int[] {-1, 1}) {
            for (int dz : new int[] {-1, 1}) {
                for (int y = 1; y <= 2; y++) {
                    stage.set(gallery.offset(dx, y, dz), Blocks.IRON_BARS.defaultBlockState());
                }
            }
        }
        BlockPos motor = gallery.above();
        stage.set(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        stage.set(motor.above(), LightworksBlocks.ROTATING_BEACON.getDefaultState()
                .setValue(RotatingBeaconBlock.FACING, Direction.UP));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                stage.set(gallery.offset(dx, 3, dz), Blocks.RED_CONCRETE.defaultBlockState());
            }
        }
        stage.set(gallery.above(4), Blocks.LIGHTNING_ROD.defaultBlockState());
        return motor;
    }

    /** A round grassy hill on the flat ground, {@code height} blocks high in the middle and {@code radius} across. */
    private static void hill(Stage stage, BlockPos centre, int radius, int height) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double f = 1 - (dx * dx + dz * dz) / (double) (radius * radius);
                int top = (int) Math.round(height * Math.max(f, 0));
                // Grass all through, so every step shows a grassy edge rather than bare dirt
                for (int y = 0; y < top; y++) {
                    stage.set(centre.offset(dx, y, dz), Blocks.GRASS_BLOCK.defaultBlockState());
                }
            }
        }
    }

    /** An oak, five logs and a round crown of leaves, standing on {@code base}. */
    private static void tree(Stage stage, BlockPos base) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
        for (int y = 3; y <= 6; y++) {
            int r = y >= 5 ? 1 : 2;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.abs(x) + Math.abs(z) < 2 * r || r == 1 && y == 5) {
                        stage.set(base.offset(x, y, z), leaves);
                    }
                }
            }
        }
        for (int y = 0; y < 5; y++) {
            stage.set(base.above(y), Blocks.OAK_LOG.defaultBlockState());
        }
    }

    /** Builds the showcase airship round origin + (0, 5, 6) and returns the corners of what to assemble. */
    private static BlockPos[] airship(Stage stage) {
        BlockPos c = stage.at(0, 5, 6);
        for (int x = -2; x <= 2; x++) {
            for (int z = -1; z <= 1; z++) {
                stage.set(c.offset(x, 0, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        for (int x : new int[] {-2, 2}) {
            for (int y = 1; y <= 2; y++) {
                stage.set(c.offset(x, y, 0), Blocks.SPRUCE_FENCE.defaultBlockState());
            }
        }
        for (int x = -7; x <= 7; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    double e = (x / 6.5) * (x / 6.5) + (y / 2.7) * (y / 2.7) + (z / 2.7) * (z / 2.7);
                    if (e <= 1) {
                        stage.set(c.offset(x, 5 + y, z), Blocks.WHITE_WOOL.defaultBlockState());
                    }
                }
            }
        }
        for (int z : new int[] {-1, 1}) {
            stage.set(c.offset(3, 0, z), headlight(Direction.EAST));
            stage.set(c.offset(-3, 0, z), LightworksBlocks.TAIL_LIGHT.getDefaultState()
                    .setValue(SignalLampBlock.FACING, Direction.WEST));
            for (int x : new int[] {-1, 1}) {
                stage.set(c.offset(x, 1, z), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                        .setValue(EdgeLightBlock.COLOUR, z < 0 ? DyeColor.RED : DyeColor.GREEN));
            }
        }
        stage.set(c.offset(1, -1, 0), searchlight(Direction.DOWN));
        stage.lightAt(c.offset(1, -1, 0)).aimAlong(new Vec3(0.6, -1, 0));
        stage.set(c.offset(0, 8, 0), LightworksBlocks.WARNING_LIGHT.getDefaultState()
                .setValue(SignalLampBlock.FACING, Direction.UP));
        return new BlockPos[] {c.offset(-7, -1, -3), c.offset(7, 8, 3)};
    }

    /**
     * Builds with {@code build} (which returns the corners of what to assemble), makes it a Sable vehicle with physics
     * paused, moves it slowly east and takes shot {@code name} on the way, since Headlights light only on a vehicle going
     * their way.
     */
    private static Script vehicleShot(String name, Function<Stage, BlockPos[]> build) {
        Script script = new Script(name);
        if (!ModList.get().isLoaded("sable")) {
            return script.run(stage -> stage.log("%s: skipped, Sable is not installed", name));
        }
        return script.run(stage -> {
                    stage.night();
                    lawn(stage);
                    SelfTestBridge.bright = true;
                    BlockPos[] box = build.apply(stage);
                    stage.run("sable paused true");
                    stage.run("sable assemble area " + box[0].getX() + " " + box[0].getY() + " " + box[0].getZ() + " "
                            + box[1].getX() + " " + box[1].getY() + " " + box[1].getZ());
                })
                .waitTicks(40)
                .repeat(80, (stage, i) -> {
                    stage.run("sable physics translation @l add 0.05 0 0 global");
                    if (i == 40) {
                        SelfTestBridge.requestShot(name);
                    }
                })
                .run(stage -> {
                    stage.run("sable remove @l");
                    stage.run("sable paused false");
                    SelfTestBridge.focus = null;
                    SelfTestBridge.bright = false;
                    stage.restoreBlocks();
                });
    }

    /**
     * Grass over the ground round the test site, hiding the dirt older runs left where their blocks stood, and no items
     * lying on it.
     */
    private static void lawn(Stage stage) {
        stage.level.getEntitiesOfClass(ItemEntity.class, new AABB(stage.origin).inflate(24)).forEach(Entity::discard);
        for (BlockPos pos : BlockPos.betweenClosed(stage.at(-16, -1, -12), stage.at(16, -1, 16))) {
            if (stage.level.getBlockState(pos).is(Blocks.DIRT)) {
                stage.set(pos, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
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
     * <p>
     * With {@code mix}, the 50 lights are every kind instead: under the deck 20 Searchlights and 10
     * Floodlights pointing down; on top 5 Rotating Beacons (two beams each), and Warning Lights and Tail and Brake
     * Lights by turns.
     */
    static Script load(String name, boolean mix) {
        Script script = new Script(name);
        if (!ModList.get().isLoaded("sable")) {
            return script.run(stage -> stage.log("%s: skipped, Sable is not installed", name));
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
                            if (!mix) {
                                stage.set(deck[0].offset(x, -1, z), searchlight(Direction.DOWN));
                            }
                        }
                        if (mix) {
                            stage.set(deck[0].offset(x, -1, -2), searchlight(Direction.DOWN));
                            stage.set(deck[0].offset(x, -1, 0), searchlight(Direction.DOWN));
                            stage.set(deck[0].offset(x, -1, 2), LightworksBlocks.FLOODLIGHT.getDefaultState()
                                    .setValue(DirectionalLightBlock.FACING, Direction.DOWN));
                            BlockState top = (x + 4) % 2 == 0 ? LightworksBlocks.WARNING_LIGHT.getDefaultState()
                                    : LightworksBlocks.TAIL_LIGHT.getDefaultState();
                            stage.set(deck[0].offset(x, 1, -1), top.setValue(SignalLampBlock.FACING, Direction.UP));
                            stage.set(deck[0].offset(x, 1, 1), x % 2 == 0
                                    ? LightworksBlocks.ROTATING_BEACON.getDefaultState()
                                            .setValue(RotatingBeaconBlock.FACING, Direction.UP)
                                    : (x % 4 == 1 ? LightworksBlocks.WARNING_LIGHT : LightworksBlocks.TAIL_LIGHT)
                                            .getDefaultState().setValue(SignalLampBlock.FACING, Direction.UP));
                        }
                    }
                    BlockPos from = deck[0].offset(-4, -1, -2);
                    BlockPos to = deck[0].offset(5, mix ? 1 : 0, 2);
                    stage.run("sable paused true");
                    stage.run("sable assemble area " + from.getX() + " " + from.getY() + " " + from.getZ() + " "
                            + to.getX() + " " + to.getY() + " " + to.getZ());
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 3, 6)).add(0.5, 0, 0.5);
                    SelfTestBridge.beamsOff = true;
                })
                .waitTicks(60);
        measure(script, name + "-off-flying", phaseTicks,
                (stage, i) -> stage.run("sable physics translation @l add " + speed + " 0 0 global"));
        script.run(stage -> SelfTestBridge.beamsOff = false).waitTicks(40);
        measure(script, name + "-on-hovering", phaseTicks, (stage, i) -> {
        });
        measure(script, name + "-on-flying", phaseTicks,
                (stage, i) -> stage.run("sable physics translation @l add " + -speed + " 0 0 global"));
        return script.shot(name + "-on")
                .run(stage -> {
                    stage.run("sable remove @l");
                    stage.run("sable paused false");
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * Edge Lights: a dyed row at night, then the lens's full-bright glow on the moves, since it comes from the
     * baked model: on a bearing's arm (Create contraption) and on a Sable vehicle if installed. The lenses should
     * glow in every shot and the moving ones keep their colours.
     */
    static Script edge() {
        DyeColor[] colours = {DyeColor.WHITE, DyeColor.BLUE, DyeColor.GREEN, DyeColor.RED, DyeColor.YELLOW};
        BlockPos[] motor = new BlockPos[1];
        Script script = new Script("edge")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, -4);
                    for (int x = -6; x <= 6; x++) {
                        stage.set(stage.at(x, 0, 4), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                                .setValue(EdgeLightBlock.COLOUR, colours[Math.floorMod(x, colours.length)]));
                    }
                    // Mounted on every side of one block: each lens should point straight out of its face
                    BlockPos mount = stage.at(0, 2, 6);
                    stage.set(mount, Blocks.STONE.defaultBlockState());
                    for (Direction side : Direction.values()) {
                        if (side != Direction.SOUTH) {
                            stage.set(mount.relative(side), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                                    .setValue(EdgeLightBlock.FACING, side));
                        }
                    }
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 1, 4));
                })
                .waitTicks(40)
                .shot("edge-0-row")
                .run(stage -> {
                    stage.restoreBlocks();
                    stage.hover(-1, 9, 4);
                    motor[0] = stage.at(0, 5, 8);
                    BlockPos bearing = motor[0].above();
                    stage.reserve(bearing.offset(-4, 0, -4), bearing.offset(4, 3, 4));
                    for (int x = 0; x <= 3; x++) {
                        stage.set(bearing.offset(x, 1, 0), Blocks.OAK_PLANKS.defaultBlockState());
                        stage.set(bearing.offset(x, 2, 0), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                                .setValue(EdgeLightBlock.COLOUR, colours[x]));
                    }
                    stage.level.addFreshEntity(new SuperGlueEntity(stage.level,
                            AABB.encapsulatingFullBlocks(bearing.above(), bearing.offset(3, 2, 0))));
                    stage.set(bearing, AllBlocks.MECHANICAL_BEARING.getDefaultState()
                            .setValue(DirectionalKineticBlock.FACING, Direction.UP));
                    SelfTestBridge.focus = Vec3.atCenterOf(bearing.above());
                })
                .waitTicks(20)
                .run(stage -> {
                    stage.set(motor[0], AllBlocks.CREATIVE_MOTOR.getDefaultState()
                            .setValue(CreativeMotorBlock.FACING, Direction.UP));
                    setMotorSpeed(stage, motor[0], 8);
                })
                .waitTicks(40)
                .shot("edge-1-bearing")
                .run(stage -> {
                    setMotorSpeed(stage, motor[0], 0);
                    stage.discardContraptions(motor[0], 6);
                    stage.restoreBlocks();
                    stage.discardContraptions(motor[0], 6);
                });
        if (!ModList.get().isLoaded("sable")) {
            return script.run(stage -> {
                SelfTestBridge.focus = null;
                stage.log("edge: vehicle part skipped, Sable is not installed");
            });
        }
        return script.run(stage -> {
                    BlockPos deck = stage.at(0, 6, 6);
                    stage.hover(0, 10, 1);
                    for (int x = -4; x <= 4; x++) {
                        for (int z = -1; z <= 1; z++) {
                            stage.set(deck.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
                        }
                        stage.set(deck.offset(x, 1, -1), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                                .setValue(EdgeLightBlock.COLOUR, colours[Math.floorMod(x, colours.length)]));
                        stage.set(deck.offset(x, 1, 1), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                                .setValue(EdgeLightBlock.COLOUR, colours[Math.floorMod(x, colours.length)]));
                    }
                    BlockPos from = deck.offset(-4, 0, -1);
                    BlockPos to = deck.offset(4, 1, 1);
                    stage.run("sable paused true");
                    stage.run("sable assemble area " + from.getX() + " " + from.getY() + " " + from.getZ() + " "
                            + to.getX() + " " + to.getY() + " " + to.getZ());
                    SelfTestBridge.focus = Vec3.atCenterOf(deck);
                })
                .waitTicks(40)
                .shot("edge-2-vehicle")
                .run(stage -> {
                    stage.run("sable remove @l");
                    stage.run("sable paused false");
                    SelfTestBridge.focus = null;
                    stage.restoreBlocks();
                });
    }

    /**
     * 200 Edge Lights in two long rows on the ground, against the same ground empty: their frame and tick
     * cost, which should be small enough not to matter.
     */
    static Script loadEdge() {
        Script script = new Script("load-edge")
                .run(stage -> {
                    stage.night();
                    stage.stand(0, -8);
                    SelfTestBridge.focus = Vec3.atCenterOf(stage.at(0, 0, 8));
                })
                .waitTicks(60);
        measure(script, "load-edge-without", 200, (stage, i) -> {
        });
        script.run(stage -> {
            for (int i = 0; i < 100; i++) {
                stage.set(stage.at(-25 + i / 2, 0, 4 + (i % 2) * 8), LightworksBlocks.EDGE_LIGHT.getDefaultState());
                stage.set(stage.at(-25 + i / 2, 0, 6 + (i % 2) * 8), LightworksBlocks.EDGE_LIGHT.getDefaultState()
                        .setValue(EdgeLightBlock.COLOUR, DyeColor.BLUE));
            }
        }).waitTicks(60);
        measure(script, "load-edge-with-200", 200, (stage, i) -> {
        });
        return script.shot("load-edge-200").run(stage -> {
            SelfTestBridge.focus = null;
            stage.restoreBlocks();
        });
    }

    /**
     * 50 Warning Lights in one pattern on a Sable vehicle, hovering and flying. A blinking lamp changes its block
     * state for its block light, which relights and rebuilds its section: compare the patterns' server ticks and
     * frame times against steady, run first and last because timings drift on one machine.
     */
    static Script loadLamps(String name, WarningPattern pattern) {
        Script script = new Script(name);
        if (!ModList.get().isLoaded("sable")) {
            return script.run(stage -> stage.log("%s: skipped, Sable is not installed", name));
        }
        BlockPos[] deck = new BlockPos[1];
        script.run(stage -> {
                    stage.night();
                    stage.stand(0, -10);
                    deck[0] = stage.at(0, 8, 6);
                    for (int x = -4; x <= 5; x++) {
                        for (int z = -2; z <= 2; z++) {
                            stage.set(deck[0].offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
                            BlockPos lamp = deck[0].offset(x, 1, z);
                            stage.set(lamp, LightworksBlocks.WARNING_LIGHT.getDefaultState()
                                    .setValue(SignalLampBlock.FACING, Direction.UP));
                            if (stage.level.getBlockEntity(lamp) instanceof SignalLampBlockEntity entity) {
                                entity.setPattern(pattern);
                            }
                        }
                    }
                    BlockPos from = deck[0].offset(-4, 0, -2);
                    BlockPos to = deck[0].offset(5, 1, 2);
                    stage.run("sable paused true");
                    stage.run("sable assemble area " + from.getX() + " " + from.getY() + " " + from.getZ() + " "
                            + to.getX() + " " + to.getY() + " " + to.getZ());
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(0, 3, 6)).add(0.5, 0, 0.5);
                })
                .waitTicks(60);
        measure(script, name + "-hovering", 200, (stage, i) -> {
        });
        measure(script, name + "-flying", 200, (stage, i) -> stage.run("sable physics translation @l add 0.1 0 0 global"));
        return script.run(stage -> {
            stage.run("sable remove @l");
            stage.run("sable paused false");
            SelfTestBridge.focus = null;
            stage.restoreBlocks();
        }).waitTicks(40);
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
