package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.beam.PanTilt;
import com.goldskinmc.lightworks.content.beacon.RotatingBeaconBlock;
import com.goldskinmc.lightworks.content.beacon.RotatingBeaconBlockEntity;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlockEntity;
import com.goldskinmc.lightworks.content.signal.WarningPattern;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Create's Clipboard copies a light's settings: right-click copies and left-click pastes, through Create's own handler. */
@GameTestHolder(Lightworks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClipboardGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos FROM = new BlockPos(1, 1, 1);
    private static final BlockPos TO = new BlockPos(3, 1, 1);

    private ClipboardGameTests() {
    }

    private static Player withClipboard(GameTestHelper helper) {
        return withClipboard(helper, GameType.SURVIVAL);
    }

    private static Player withClipboard(GameTestHelper helper, GameType mode) {
        Player player = helper.makeMockPlayer(mode);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllBlocks.CLIPBOARD.asStack());
        return player;
    }

    private static void copy(GameTestHelper helper, Player player, BlockPos pos) {
        BlockPos world = helper.absolutePos(pos);
        CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, world,
                new BlockHitResult(Vec3.atCenterOf(world), Direction.NORTH, world, false));
    }

    private static void paste(GameTestHelper helper, Player player, BlockPos pos) {
        CommonHooks.onLeftClickBlock(player, helper.absolutePos(pos), Direction.NORTH,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
    }

    private static void searchlight(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
    }

    private static void warningLight(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, LightworksBlocks.WARNING_LIGHT.getDefaultState().setValue(SignalLampBlock.FACING, Direction.UP));
    }

    /** Pan and Tilt each keep their own value, and the colour comes along, from one kind of light to another. */
    @GameTest(template = EMPTY)
    public static void aimAndColourCopyBetweenLights(GameTestHelper helper) {
        searchlight(helper, FROM);
        helper.setBlock(TO, LightworksBlocks.FLOODLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
        DirectionalLightBlockEntity from = helper.getBlockEntity(FROM);
        DirectionalLightBlockEntity to = helper.getBlockEntity(TO);
        from.setPanTilt(new PanTilt(30, 20));
        from.lightState().setColour(DyeColor.RED);
        Player player = withClipboard(helper);
        ItemStack dye = new ItemStack(Items.RED_DYE, 2);
        player.getInventory().setItem(5, dye);

        copy(helper, player, FROM);
        paste(helper, player, TO);

        helper.assertTrue(to.panTilt().equals(new PanTilt(30, 20)), "pasted aim is " + to.panTilt());
        helper.assertTrue(to.lightState().colour() == DyeColor.RED, "pasted colour is " + to.lightState().colour());
        helper.assertTrue(dye.getCount() == 1, "the paste should use one dye, left " + dye.getCount());

        // Already that colour: nothing to dye
        paste(helper, player, TO);
        helper.assertTrue(dye.getCount() == 1, "a second paste should use no dye, left " + dye.getCount());
        helper.succeed();
    }

    /** Without the dye the colour stays as it was, and the aim still pastes; a dye of another colour does not count. */
    @GameTest(template = EMPTY)
    public static void withoutTheDyeOnlyTheColourIsLeftOut(GameTestHelper helper) {
        searchlight(helper, FROM);
        searchlight(helper, TO);
        DirectionalLightBlockEntity from = helper.getBlockEntity(FROM);
        DirectionalLightBlockEntity to = helper.getBlockEntity(TO);
        from.setPanTilt(new PanTilt(30, 20));
        from.lightState().setColour(DyeColor.RED);
        to.lightState().setColour(DyeColor.BLUE);
        Player player = withClipboard(helper);
        ItemStack other = new ItemStack(Items.GREEN_DYE, 2);
        player.getInventory().setItem(5, other);

        copy(helper, player, FROM);
        paste(helper, player, TO);

        helper.assertTrue(to.panTilt().equals(new PanTilt(30, 20)), "pasted aim is " + to.panTilt());
        helper.assertTrue(to.lightState().colour() == DyeColor.BLUE, "colour changed to " + to.lightState().colour());
        helper.assertTrue(other.getCount() == 2, "another colour's dye was used, left " + other.getCount());
        helper.succeed();
    }

    /** Creative uses no dye. */
    @GameTest(template = EMPTY)
    public static void creativePastesColourWithoutDye(GameTestHelper helper) {
        searchlight(helper, FROM);
        searchlight(helper, TO);
        DirectionalLightBlockEntity from = helper.getBlockEntity(FROM);
        DirectionalLightBlockEntity to = helper.getBlockEntity(TO);
        from.lightState().setColour(DyeColor.RED);
        Player player = withClipboard(helper, GameType.CREATIVE);

        copy(helper, player, FROM);
        paste(helper, player, TO);

        helper.assertTrue(to.lightState().colour() == DyeColor.RED, "pasted colour is " + to.lightState().colour());
        helper.succeed();
    }

    /** An undyed light's settings make a dyed one undyed: a paste gives the copied light's look. */
    @GameTest(template = EMPTY)
    public static void undyedCopyClearsTheColour(GameTestHelper helper) {
        searchlight(helper, FROM);
        searchlight(helper, TO);
        DirectionalLightBlockEntity to = helper.getBlockEntity(TO);
        to.lightState().setColour(DyeColor.BLUE);
        Player player = withClipboard(helper);

        copy(helper, player, FROM);
        paste(helper, player, TO);

        helper.assertTrue(to.lightState().colour() == null, "pasted colour is " + to.lightState().colour());
        helper.succeed();
    }

    /** A Warning Light gives its colour to a Searchlight, whose aim stays; its pattern goes to Warning Lights. */
    @GameTest(template = EMPTY)
    public static void warningLightCopiesWhatTheTargetUnderstands(GameTestHelper helper) {
        BlockPos other = new BlockPos(1, 1, 3);
        warningLight(helper, FROM);
        searchlight(helper, TO);
        warningLight(helper, other);
        SignalLampBlockEntity from = helper.getBlockEntity(FROM);
        DirectionalLightBlockEntity light = helper.getBlockEntity(TO);
        SignalLampBlockEntity lamp = helper.getBlockEntity(other);
        from.setPattern(WarningPattern.BLINK);
        from.lightState().setColour(DyeColor.GREEN);
        light.setPanTilt(new PanTilt(40, 10));
        Player player = withClipboard(helper);
        player.getInventory().setItem(5, new ItemStack(Items.GREEN_DYE, 2));

        copy(helper, player, FROM);
        paste(helper, player, TO);
        paste(helper, player, other);

        helper.assertTrue(light.lightState().colour() == DyeColor.GREEN, "light's colour is " + light.lightState().colour());
        helper.assertTrue(light.panTilt().equals(new PanTilt(40, 10)), "light's aim changed to " + light.panTilt());
        helper.assertTrue(lamp.lightState().colour() == DyeColor.GREEN, "lamp's colour is " + lamp.lightState().colour());
        helper.assertTrue(lamp.pattern() == WarningPattern.BLINK, "lamp's pattern is " + lamp.pattern());
        helper.succeed();
    }

    /** A Rotating Beacon has only its colour to give and take. */
    @GameTest(template = EMPTY)
    public static void beaconCopiesItsColour(GameTestHelper helper) {
        BlockPos other = new BlockPos(1, 1, 3);
        helper.setBlock(FROM, LightworksBlocks.ROTATING_BEACON.getDefaultState().setValue(RotatingBeaconBlock.FACING, Direction.UP));
        searchlight(helper, TO);
        helper.setBlock(other, LightworksBlocks.ROTATING_BEACON.getDefaultState().setValue(RotatingBeaconBlock.FACING, Direction.UP));
        RotatingBeaconBlockEntity from = helper.getBlockEntity(FROM);
        DirectionalLightBlockEntity light = helper.getBlockEntity(TO);
        RotatingBeaconBlockEntity beacon = helper.getBlockEntity(other);
        from.lightState().setColour(DyeColor.CYAN);
        Player player = withClipboard(helper);
        player.getInventory().setItem(5, new ItemStack(Items.CYAN_DYE, 2));

        copy(helper, player, FROM);
        paste(helper, player, TO);
        paste(helper, player, other);

        helper.assertTrue(light.lightState().colour() == DyeColor.CYAN, "light's colour is " + light.lightState().colour());
        helper.assertTrue(beacon.lightState().colour() == DyeColor.CYAN, "beacon's colour is " + beacon.lightState().colour());
        helper.succeed();
    }

    /** The speed of a Create block with a plain value box says nothing about a light's aim or a head's sweep. */
    @GameTest(template = EMPTY)
    public static void otherBlocksSettingsDoNotReachLights(GameTestHelper helper) {
        BlockPos headPos = new BlockPos(1, 1, 3);
        helper.setBlock(FROM, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        searchlight(helper, TO);
        helper.setBlock(headPos, LightworksBlocks.PAN_TILT_HEAD.getDefaultState().setValue(PanTiltHeadBlock.FACING, Direction.UP));
        DirectionalLightBlockEntity light = helper.getBlockEntity(TO);
        SmartBlockEntity head = helper.getBlockEntity(headPos);
        light.setPanTilt(new PanTilt(40, 10));
        ((SmartBlockEntity) helper.getBlockEntity(FROM)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(16);
        Player player = withClipboard(helper);

        copy(helper, player, FROM);
        paste(helper, player, TO);
        paste(helper, player, headPos);

        helper.assertTrue(light.panTilt().equals(new PanTilt(40, 10)), "light's aim changed to " + light.panTilt());
        int arc = head.getBehaviour(ScrollValueBehaviour.TYPE).getValue();
        helper.assertTrue(arc == 0, "head's sweep changed to " + arc);
        helper.succeed();
    }

    /** Create asks whether a paste would work every frame while a Clipboard points at a block: asking changes nothing. */
    @GameTest(template = EMPTY)
    public static void askingAboutAPasteChangesNothing(GameTestHelper helper) {
        searchlight(helper, FROM);
        searchlight(helper, TO);
        DirectionalLightBlockEntity from = helper.getBlockEntity(FROM);
        DirectionalLightBlockEntity to = helper.getBlockEntity(TO);
        from.setPanTilt(new PanTilt(30, 20));
        from.lightState().setColour(DyeColor.RED);
        PanTilt before = to.panTilt();
        CompoundTag tag = new CompoundTag();
        from.writeToClipboard(helper.getLevel().registryAccess(), tag, Direction.NORTH);

        boolean would = to.readFromClipboard(helper.getLevel().registryAccess(), tag, helper.makeMockPlayer(GameType.SURVIVAL),
                Direction.NORTH, true);

        helper.assertTrue(would, "a light should say it can take a light's settings");
        helper.assertTrue(to.panTilt().equals(before), "asking changed the aim to " + to.panTilt());
        helper.assertTrue(to.lightState().colour() == null, "asking changed the colour to " + to.lightState().colour());
        helper.succeed();
    }

    /** A head's sweep copies to another head. */
    @GameTest(template = EMPTY)
    public static void sweepCopiesBetweenHeads(GameTestHelper helper) {
        helper.setBlock(FROM, LightworksBlocks.PAN_TILT_HEAD.getDefaultState().setValue(PanTiltHeadBlock.FACING, Direction.UP));
        helper.setBlock(TO, LightworksBlocks.PAN_TILT_HEAD.getDefaultState().setValue(PanTiltHeadBlock.FACING, Direction.UP));
        SmartBlockEntity from = helper.getBlockEntity(FROM);
        SmartBlockEntity to = helper.getBlockEntity(TO);
        from.getBehaviour(ScrollValueBehaviour.TYPE).setValue(9);
        Player player = withClipboard(helper);

        copy(helper, player, FROM);
        paste(helper, player, TO);

        int arc = to.getBehaviour(ScrollValueBehaviour.TYPE).getValue();
        helper.assertTrue(arc == 9, "pasted sweep is " + arc);
        helper.succeed();
    }
}
