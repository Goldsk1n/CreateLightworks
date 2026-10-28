package com.goldskinmc.lightworks.debug.selftest;

import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.content.searchlight.SearchlightBlock;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** The self-test scenarios, by name. Each builds relative to where the player joined, at night. */
final class Scenarios {
    static final Map<String, Supplier<Script>> ALL = new LinkedHashMap<>();
    /** What {@code -Pselftest=true} runs. */
    static final String DEFAULT = "fixed,vehicle,bearing";

    static {
        ALL.put("fixed", Scenarios::fixed);
        ALL.put("vehicle", Scenarios::vehicle);
        ALL.put("bearing", Scenarios::bearing);
    }

    private Scenarios() {
    }

    static BlockState searchlight(Direction facing) {
        return LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(SearchlightBlock.FACING, facing);
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
     * Sable's own commands and flown 10 blocks east at 2 blocks a second, filmed from the side.
     */
    static Script vehicle() {
        Script script = new Script("vehicle");
        if (!ModList.get().isLoaded("sable")) {
            return script.run(stage -> stage.log("vehicle: skipped, Sable is not installed"));
        }
        BlockPos[] deck = new BlockPos[1];
        double speed = 0.1;
        int flightTicks = 100;
        return script
                .run(stage -> {
                    stage.night();
                    stage.stand(5, -9);
                    deck[0] = stage.at(0, 8, 6);
                    SelfTestBridge.focus = Vec3.atLowerCornerOf(stage.at(5, 3, 6)).add(0.5, 0, 0.5);
                })
                .waitTicks(40)
                .shot("vehicle-0-dark")
                .run(stage -> {
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
                    stage.log("vehicle: assembled deck at %s, ground at y=%d", deck[0], stage.origin.getY() - 1);
                })
                .waitTicks(40)
                .shot("vehicle-1-hovering")
                .run(stage -> {
                    SelfTestBridge.probeGroundY = stage.origin.getY();
                    SelfTestBridge.probe = "vehicle";
                    SelfTestBridge.recording = "vehicle-flight";
                })
                .repeat(flightTicks, (stage, i) -> stage.run("sable physics translation @l add " + speed + " 0 0 global"))
                .run(stage -> {
                    SelfTestBridge.recording = null;
                    SelfTestBridge.probe = null;
                })
                .waitTicks(20)
                .shot("vehicle-2-landed-east")
                .run(stage -> {
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
                    stage.restoreBlocks();
                    stage.level.getEntitiesOfClass(SuperGlueEntity.class, new AABB(motor[0]).inflate(6))
                            .forEach(Entity::discard);
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
