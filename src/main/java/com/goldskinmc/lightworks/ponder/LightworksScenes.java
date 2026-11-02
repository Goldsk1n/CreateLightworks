package com.goldskinmc.lightworks.ponder;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.beam.Aim;
import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.content.beacon.RotatingBeaconBlockEntity;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadBlockEntity;
import com.goldskinmc.lightworks.content.signal.EdgeLightBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlockEntity;
import com.goldskinmc.lightworks.content.signal.WarningPattern;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import java.util.function.Consumer;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Ponder scenes, one per block, built on the schematics from {@link PonderSchematics}. Ponder worlds
 * have no light and no redstone updates, so scenes set what a light would read (signal, brightness,
 * colour, aim) on its block entity directly; the beams are real, traced inside the scene.
 */
final class LightworksScenes {
    static final ResourceLocation LIGHTS = Lightworks.id("lights");

    private LightworksScenes() {
    }

    static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> scenes = helper.withKeyFunction(RegistryEntry::getId);
        scenes.forComponents(LightworksBlocks.SEARCHLIGHT).addStoryBoard("searchlight", LightworksScenes::searchlight, LIGHTS);
        scenes.forComponents(LightworksBlocks.FLOODLIGHT).addStoryBoard("floodlight", LightworksScenes::floodlight, LIGHTS);
        scenes.forComponents(LightworksBlocks.PAN_TILT_HEAD)
                .addStoryBoard("pan_tilt_head", LightworksScenes::panTiltHead, LIGHTS);
        scenes.forComponents(LightworksBlocks.ROTATING_BEACON)
                .addStoryBoard("rotating_beacon", LightworksScenes::rotatingBeacon, LIGHTS);
        scenes.forComponents(LightworksBlocks.WARNING_LIGHT)
                .addStoryBoard("warning_light", LightworksScenes::warningLight, LIGHTS);
        scenes.forComponents(LightworksBlocks.HEADLIGHT).addStoryBoard("headlight", LightworksScenes::headlight, LIGHTS);
        scenes.forComponents(LightworksBlocks.TAIL_LIGHT).addStoryBoard("tail_light", LightworksScenes::tailLight, LIGHTS);
        scenes.forComponents(LightworksBlocks.EDGE_LIGHT)
                .addStoryBoard("edge_light", LightworksScenes::edgeLight, LIGHTS);
    }

    static void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(LIGHTS)
                .addToIndex()
                .item(LightworksBlocks.SEARCHLIGHT.get(), true, false)
                .title("Lightworks")
                .description("Lights that keep working on anything Create builds")
                .register();
        PonderTagRegistrationHelper<ItemProviderEntry<?, ?>> tags = helper.withKeyFunction(RegistryEntry::getId);
        tags.addToTag(LIGHTS)
                .add(LightworksBlocks.SEARCHLIGHT)
                .add(LightworksBlocks.FLOODLIGHT)
                .add(LightworksBlocks.PAN_TILT_HEAD)
                .add(LightworksBlocks.ROTATING_BEACON)
                .add(LightworksBlocks.WARNING_LIGHT)
                .add(LightworksBlocks.HEADLIGHT)
                .add(LightworksBlocks.TAIL_LIGHT)
                .add(LightworksBlocks.EDGE_LIGHT);
        tags.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES)
                .add(LightworksBlocks.PAN_TILT_HEAD)
                .add(LightworksBlocks.ROTATING_BEACON);
    }

    static void searchlight(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("searchlight", "Lighting a spot with a Searchlight");
        scene.configureBasePlate(0, 0, Layouts.Searchlight.SIZE);
        scene.showBasePlate();
        scene.idle(10);
        BlockPos light = Layouts.Searchlight.LIGHT;
        BlockPos target = Layouts.Searchlight.TARGET;
        scene.world().showSection(util.select().fromTo(Layouts.Searchlight.WALL_FROM, Layouts.Searchlight.WALL_TO),
                Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(light), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showOutline(PonderPalette.WHITE, "target", util.select().position(target), 80);
        scene.overlay().showText(60)
                .attachKeyFrame()
                .text("Searchlights throw a long, narrow beam")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(target, Direction.WEST));
        scene.idle(70);
        Vec3 top = util.vector().topOf(light);
        scene.overlay().showText(60)
                .text("It lights the block it lands on")
                .placeNearTarget()
                .pointAt(top);
        scene.idle(70);

        Vec3 boards = util.vector().blockSurface(light, Direction.WEST).add(0, -0.25, 0);
        scene.overlay().showControls(boards, Pointing.LEFT, 40).rightClick();
        scene.idle(7);
        aim(scene, util, light, Direction.UP,
                PonderSchematics.face(Layouts.Searchlight.TARGET_AFTER_AIM, Direction.WEST));
        scene.overlay().showText(70).attachKeyFrame().sharedText("aim").placeNearTarget().pointAt(boards);
        scene.idle(80);

        BlockPos lever = Layouts.Searchlight.LEVER;
        scene.world().showSection(util.select().position(lever), Direction.DOWN);
        scene.idle(15);
        scene.world().toggleRedstonePower(util.select().position(lever));
        scene.effects().indicateRedstone(lever);
        power(scene, util, light, 15);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .sharedText("redstone")
                .placeNearTarget()
                .pointAt(util.vector().topOf(lever));
        scene.idle(80);
        scene.world().toggleRedstonePower(util.select().position(lever));
        power(scene, util, light, 0);
        scene.idle(20);

        scene.overlay().showControls(top, Pointing.DOWN, 40).rightClick().withItem(new ItemStack(Items.LIGHT_BLUE_DYE));
        scene.idle(7);
        dye(scene, util, light, DyeColor.LIGHT_BLUE);
        scene.overlay().showText(60).attachKeyFrame().sharedText("dye").placeNearTarget().pointAt(top);
        scene.idle(70);
        scene.overlay().showText(80)
                .text("On moving vehicles, the beam still lands where it points")
                .placeNearTarget()
                .pointAt(top);
        scene.idle(90);
        scene.markAsFinished();
    }

    static void floodlight(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("floodlight", "Lighting an area with a Floodlight");
        scene.configureBasePlate(0, 0, Layouts.Floodlight.SIZE);
        // Taller than the other scenes: the lamp hangs high enough for its whole beam to land on the plate
        scene.scaleSceneView(0.8f);
        scene.setSceneOffsetY(-1.5f);
        scene.showBasePlate();
        scene.idle(10);
        BlockPos light = Layouts.Floodlight.LIGHT;
        BlockPos target = Layouts.Floodlight.TARGET;
        scene.world().showSection(util.select().fromTo(Layouts.Floodlight.PILLAR_FROM, Layouts.Floodlight.PILLAR_TO)
                .add(util.select().fromTo(Layouts.Floodlight.ARM_FROM, Layouts.Floodlight.ARM_TO)), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(light), Direction.WEST);
        scene.idle(20);

        scene.overlay().showOutline(PonderPalette.WHITE, "area", util.select().fromTo(target.offset(-2, 0, -2),
                target.offset(2, 0, 2)), 60);
        scene.overlay().showText(60)
                .attachKeyFrame()
                .text("Floodlights spread a wide beam")
                .placeNearTarget()
                .pointAt(util.vector().topOf(target));
        scene.idle(70);
        Vec3 side = util.vector().blockSurface(light, Direction.WEST);
        scene.overlay().showText(80)
                .text("Mount one high and aim it down to light an area")
                .placeNearTarget()
                .pointAt(side);
        scene.idle(90);

        Vec3 front = side;
        Vec3 boards = side.add(0, -0.25, 0);
        scene.overlay().showControls(boards, Pointing.LEFT, 40).rightClick();
        scene.idle(7);
        aim(scene, util, light, Direction.DOWN,
                PonderSchematics.face(Layouts.Floodlight.TARGET_AFTER_AIM, Direction.UP));
        scene.overlay().showText(70).attachKeyFrame().sharedText("aim").placeNearTarget().pointAt(boards);
        scene.idle(80);
        scene.overlay().showText(60)
                .text("It mounts on floors, walls and ceilings")
                .placeNearTarget()
                .pointAt(side);
        scene.idle(70);

        scene.overlay().showControls(front, Pointing.LEFT, 40).rightClick().withItem(new ItemStack(Items.RED_DYE));
        scene.idle(7);
        dye(scene, util, light, DyeColor.RED);
        scene.overlay().showText(60).attachKeyFrame().sharedText("dye").placeNearTarget().pointAt(front);
        scene.idle(70);
        scene.markAsFinished();
    }

    static void headlight(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("headlight", "Headlights on vehicles");
        scene.configureBasePlate(0, 0, Layouts.Cart.SIZE);
        scene.showBasePlate();
        scene.idle(10);
        BlockPos front = Layouts.Cart.FRONT;
        BlockPos rear = Layouts.Cart.REAR;
        ElementLink<WorldSectionElement> cart = scene.world().showIndependentSection(
                util.select().fromTo(rear, front), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(70)
                .text("Headlights light the way ahead of a vehicle")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(front, Direction.EAST));
        scene.idle(80);

        brightness(scene, util, rear, 0);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("On the move, only those facing forward light up")
                .placeNearTarget()
                .pointAt(util.vector().topOf(front));
        scene.world().moveSection(cart, new Vec3(1, 0, 0), 40);
        scene.idle(100);

        brightness(scene, util, front, 0);
        brightness(scene, util, rear, 15);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("When it reverses, they swap over")
                .placeNearTarget()
                .pointAt(util.vector().topOf(rear.offset(1, 0, 0)));
        scene.world().moveSection(cart, new Vec3(-2, 0, 0), 60);
        scene.idle(80);

        power(scene, util, front, 4);
        power(scene, util, rear, 4);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Power one with redstone before assembly to keep it lit both ways")
                .placeNearTarget()
                .pointAt(util.vector().topOf(rear.offset(-1, 0, 0)));
        scene.world().moveSection(cart, new Vec3(1, 0, 0), 40);
        scene.idle(100);
        scene.markAsFinished();
    }

    static void tailLight(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("tail_light", "Tail and Brake Lights");
        scene.configureBasePlate(0, 0, Layouts.Cart.SIZE);
        scene.showBasePlate();
        scene.idle(10);
        BlockPos front = Layouts.Cart.FRONT;
        BlockPos tail = Layouts.Cart.REAR;
        ElementLink<WorldSectionElement> cart = scene.world().showIndependentSection(
                util.select().fromTo(tail, front), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("Tail and Brake Lights glow dim red at the back")
                .placeNearTarget()
                .pointAt(onLamp(tail, 13, 8, 8));
        scene.idle(90);

        scene.world().moveSection(cart, new Vec3(1, 0, 0), 40);
        scene.idle(40);
        brake(scene, tail);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("They brighten for a second when the vehicle slows or stops")
                .placeNearTarget()
                .pointAt(onLamp(tail.offset(1, 0, 0), 13, 8, 8));
        scene.idle(100);

        brightness(scene, util, front, 0);
        scene.world().moveSection(cart, new Vec3(-2, 0, 0), 50);
        scene.idle(50);
        brake(scene, tail);
        scene.overlay().showText(70)
                .text("Placed in the world, they just glow")
                .placeNearTarget()
                .pointAt(onLamp(tail.offset(-1, 0, 0), 13, 8, 8));
        scene.idle(80);
        scene.markAsFinished();
    }

    static void panTiltHead(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("pan_tilt_head", "Sweeping a light with the Pan-Tilt Head");
        scene.configureBasePlate(0, 0, Layouts.PanTilt.SIZE);
        scene.showBasePlate();
        scene.idle(10);
        BlockPos motor = Layouts.PanTilt.MOTOR;
        BlockPos head = Layouts.PanTilt.HEAD;
        BlockPos light = Layouts.PanTilt.LIGHT;
        scene.world().showSection(util.select().position(motor), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(head), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(light), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("A Pan-Tilt Head turns the light on top of it")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(head, Direction.WEST));
        scene.idle(90);

        Selection kinetics = util.select().fromTo(motor, head);
        scene.world().setKineticSpeed(kinetics, 16);
        scene.effects().rotationSpeedIndicator(motor);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("The shaft pans it, as fast as the shaft turns")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(motor, Direction.WEST));
        scene.idle(100);

        BlockPos lever = Layouts.PanTilt.LEVER;
        scene.world().showSection(util.select().fromTo(Layouts.PanTilt.LEVER_BASE, lever), Direction.DOWN);
        scene.idle(15);
        tilt(scene, util, head, lever, 5);
        scene.overlay().showText(130)
                .attachKeyFrame()
                .text("Redstone tilts it up: 6° per level, straight up at 15")
                .placeNearTarget()
                .pointAt(util.vector().topOf(lever));
        scene.idle(70);
        tilt(scene, util, head, lever, 10);
        scene.idle(70);

        scene.world().modifyBlockEntityNBT(util.select().position(head), PanTiltHeadBlockEntity.class,
                nbt -> nbt.putInt("ScrollValue", 9));
        scene.overlay().showControls(util.vector().blockSurface(head, Direction.NORTH), Pointing.RIGHT, 40).rightClick();
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Hold right-click on its box to sweep back and forth")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(head, Direction.NORTH));
        scene.idle(120);

        scene.world().setKineticSpeed(kinetics, 0);
        scene.overlay().showText(60)
                .attachKeyFrame()
                .text("Stopped, the light holds its aim")
                .placeNearTarget()
                .pointAt(util.vector().topOf(light));
        scene.idle(70);
        scene.markAsFinished();
    }

    static void rotatingBeacon(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("rotating_beacon", "Rotating Beacons");
        scene.configureBasePlate(0, 0, Layouts.Beacon.SIZE);
        scene.showBasePlate();
        scene.idle(10);
        BlockPos motor = Layouts.Beacon.MOTOR;
        BlockPos beacon = Layouts.Beacon.BEACON;
        scene.world().showSection(util.select().position(motor), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(beacon), Direction.DOWN);
        scene.idle(20);

        Selection kinetics = util.select().fromTo(motor, beacon);
        scene.world().setKineticSpeed(kinetics, 8);
        scene.overlay().showText(70)
                .text("Rotating Beacons sweep two opposite beams round")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(beacon, Direction.WEST));
        scene.idle(80);

        scene.world().setKineticSpeed(kinetics, 32);
        scene.effects().rotationSpeedIndicator(motor);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("The faster the shaft, the faster the sweep")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(motor, Direction.WEST));
        scene.idle(80);

        scene.world().setKineticSpeed(kinetics, 0);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Stopped, the beams hold still and light where they land")
                .placeNearTarget()
                .pointAt(util.vector().topOf(beacon));
        scene.idle(90);

        Vec3 top = util.vector().topOf(beacon);
        scene.overlay().showControls(top, Pointing.DOWN, 40).rightClick().withItem(new ItemStack(Items.RED_DYE));
        scene.idle(7);
        dye(scene, util, beacon, RotatingBeaconBlockEntity.class, DyeColor.RED);
        scene.world().setKineticSpeed(kinetics, 16);
        scene.overlay().showText(60).attachKeyFrame().sharedText("dye").placeNearTarget().pointAt(top);
        scene.idle(70);
        scene.markAsFinished();
    }

    static void warningLight(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("warning_light", "Warning Lights");
        scene.configureBasePlate(0, 0, Layouts.Warning.SIZE);
        scene.showBasePlate();
        scene.idle(10);
        BlockPos lamp = Layouts.Warning.LAMP;
        scene.world().showSection(util.select().position(lamp), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(70)
                .text("Warning Lights glow amber and give block light")
                .placeNearTarget()
                .pointAt(onLamp(lamp, 8, 10, 8));
        scene.idle(80);

        scene.overlay().showCenteredScrollInput(lamp, Direction.WEST, 60);
        scene.idle(10);
        pattern(scene, lamp, WarningPattern.BLINK);
        scene.overlay().showText(190)
                .attachKeyFrame()
                .text("Hold right-click on its box to pick a pattern")
                .placeNearTarget()
                .pointAt(onLamp(lamp, 4, 5, 8));
        scene.idle(70);
        pattern(scene, lamp, WarningPattern.PULSE);
        scene.idle(60);
        pattern(scene, lamp, WarningPattern.DOUBLE_FLASH);
        scene.idle(60);
        pattern(scene, lamp, WarningPattern.STROBE);
        scene.overlay().showText(80)
                .text("The lower row runs the same patterns only at night")
                .placeNearTarget()
                .pointAt(onLamp(lamp, 4, 5, 8));
        scene.idle(90);

        scene.overlay().showText(60)
                .text("Its light follows the pattern too")
                .placeNearTarget()
                .pointAt(onLamp(lamp, 8, 10, 8));
        scene.idle(70);

        Vec3 top = onLamp(lamp, 8, 10, 8);
        scene.overlay().showControls(top, Pointing.DOWN, 40).rightClick().withItem(new ItemStack(Items.RED_DYE));
        scene.idle(7);
        dye(scene, util, lamp, SignalLampBlockEntity.class, DyeColor.RED);
        scene.overlay().showText(60)
                .attachKeyFrame()
                .sharedText("dye")
                .placeNearTarget()
                .pointAt(top);
        scene.idle(70);
        scene.markAsFinished();
    }

    static void edgeLight(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("edge_light", "Edge Lights");
        scene.configureBasePlate(0, 0, Layouts.Edge.SIZE);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(Layouts.Edge.STRIP_FROM, Layouts.Edge.STRIP_TO), Direction.DOWN);
        scene.idle(10);
        for (int i = 0; i < Layouts.Edge.LEFT.length; i++) {
            scene.world().showSection(util.select().position(Layouts.Edge.LEFT[i]), Direction.DOWN);
            scene.world().showSection(util.select().position(Layouts.Edge.RIGHT[i]), Direction.DOWN);
            scene.idle(5);
        }
        scene.idle(10);

        BlockPos left = Layouts.Edge.LEFT[1];
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Edge Lights mark runways, taxiways and platforms")
                .placeNearTarget()
                .pointAt(onLamp(left, 8, 3, 8));
        scene.idle(80);
        scene.overlay().showText(70)
                .text("Vehicles and players pass over them")
                .placeNearTarget()
                .pointAt(onLamp(Layouts.Edge.RIGHT[1], 8, 3, 8));
        scene.idle(80);

        scene.overlay().showControls(onLamp(left, 8, 3, 8), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(Items.BLUE_DYE));
        scene.idle(7);
        for (BlockPos pos : Layouts.Edge.LEFT) {
            scene.world().modifyBlock(pos, state -> state.setValue(EdgeLightBlock.COLOUR, DyeColor.BLUE), false);
        }
        for (BlockPos pos : Layouts.Edge.RIGHT) {
            scene.world().modifyBlock(pos, state -> state.setValue(EdgeLightBlock.COLOUR, DyeColor.BLUE), false);
        }
        scene.overlay().showText(60)
                .attachKeyFrame()
                .sharedText("dye")
                .placeNearTarget()
                .pointAt(onLamp(left, 8, 3, 8));
        scene.idle(70);

        for (BlockPos pos : Layouts.Edge.LEFT) {
            scene.world().modifyBlock(pos, state -> state.setValue(EdgeLightBlock.BRIGHTNESS, 0), false);
        }
        for (BlockPos pos : Layouts.Edge.RIGHT) {
            scene.world().modifyBlock(pos, state -> state.setValue(EdgeLightBlock.BRIGHTNESS, 0), false);
        }
        scene.overlay().showText(70)
                .attachKeyFrame()
                .sharedText("redstone")
                .placeNearTarget()
                .pointAt(onLamp(left, 8, 3, 8));
        scene.idle(80);
        scene.markAsFinished();
    }

    /**
     * A point on a lamp smaller than its block, in sixteenths: {@code topOf} and {@code blockSurface} mean the whole
     * block's space, so a line aimed with them ends in the air beside the lamp.
     */
    private static Vec3 onLamp(BlockPos pos, double x, double y, double z) {
        return Vec3.atLowerCornerOf(pos).add(x / 16, y / 16, z / 16);
    }

    /** Changes a light's saved state the way a block entity would load it. */
    private static void edit(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos,
            Class<? extends BlockEntity> type, Consumer<LightState> change) {
        scene.world().modifyBlockEntityNBT(util.select().position(pos), type, (CompoundTag nbt) -> {
            LightState state = LightState.of(nbt);
            change.accept(state);
            state.write(nbt);
        });
    }

    private static void aim(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos light, Direction facing,
            Vec3 target) {
        Aim aim = PonderSchematics.aimAt(light, facing, target);
        edit(scene, util, light, DirectionalLightBlockEntity.class, state -> state.setAim(aim));
    }

    /** A redstone signal as the light would read it, with the default (not inverted) dimming. */
    private static void power(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos light, int signal) {
        edit(scene, util, light, DirectionalLightBlockEntity.class, state -> {
            state.setSignal(signal);
            state.setBrightness(15 - signal);
        });
    }

    /** Lit or dark without a signal: how a Headlight looks leading or trailing on a moving vehicle. */
    private static void brightness(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos light, int brightness) {
        edit(scene, util, light, DirectionalLightBlockEntity.class, state -> state.setBrightness(brightness));
    }

    private static void dye(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos light, DyeColor colour) {
        dye(scene, util, light, DirectionalLightBlockEntity.class, colour);
    }

    private static void dye(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos light,
            Class<? extends BlockEntity> type, DyeColor colour) {
        edit(scene, util, light, type, state -> state.setColour(colour));
    }

    private static void tilt(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos head, BlockPos lever, int signal) {
        scene.world().modifyBlockEntityNBT(util.select().position(lever), AnalogLeverBlockEntity.class,
                nbt -> nbt.putInt("State", signal));
        scene.effects().indicateRedstone(lever);
        scene.world().modifyBlockEntityNBT(util.select().position(head), PanTiltHeadBlockEntity.class,
                nbt -> nbt.putInt("Signal", signal));
    }

    private static void pattern(CreateSceneBuilder scene, BlockPos lamp, WarningPattern pattern) {
        scene.world().modifyBlockEntity(lamp, SignalLampBlockEntity.class, be -> be.setPattern(pattern));
    }

    private static void brake(CreateSceneBuilder scene, BlockPos tail) {
        scene.world().modifyBlockEntity(tail, SignalLampBlockEntity.class, SignalLampBlockEntity::showBrake);
    }
}
