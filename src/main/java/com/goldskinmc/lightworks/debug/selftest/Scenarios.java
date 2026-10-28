package com.goldskinmc.lightworks.debug.selftest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.debug.Timer;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.simibubi.create.AllBlocks;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    static final String DEFAULT = "fixed,vehicle,bearing,piston,vehicle-fast";

    static {
        ALL.put("fixed", Scenarios::fixed);
        ALL.put("vehicle", () -> vehicle("vehicle", 0.1, 100, true));
        // Create's default train top speed, 28 blocks a second: how far the spot trails at train speeds
        ALL.put("vehicle-fast", () -> vehicle("vehicle-fast", 1.4, 40, false));
        ALL.put("bearing", Scenarios::bearing);
        ALL.put("piston", Scenarios::piston);
        // Not in the default set: heavier, or one-off
        ALL.put("load", Scenarios::load);
        ALL.put("cleanup", Scenarios::cleanup);
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
                    BlockPos from = deck[0].offset(-1, -1, -1);
                    BlockPos to = deck[0].offset(1, 0, 1);
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
                    stage.level.addFreshEntity(new SuperGlueEntity(stage.level,
                            AABB.encapsulatingFullBlocks(cart.below(), cart.east())));
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
