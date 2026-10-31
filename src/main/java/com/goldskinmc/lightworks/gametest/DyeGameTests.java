package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.directional.AimBoard;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.content.signal.SignalLampBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlockEntity;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dye colours: applied by right-clicking with a dye, kept through saving and sent to clients. */
@GameTestHolder(Lightworks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DyeGameTests {
    private DyeGameTests() {
    }

    /** Dye skips the Pan and Tilt boxes so the click reaches the light; other items still open the boards. */
    @GameTest(template = "empty")
    public static void dyeClicksPassTheAimBoards(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
        DirectionalLightBlockEntity light = helper.getBlockEntity(pos);
        for (AimBoard board : new AimBoard[] {light.getBehaviour(AimBoard.PAN), light.getBehaviour(AimBoard.TILT)}) {
            helper.assertTrue(board.bypassesInput(new ItemStack(Items.RED_DYE)), "dye stops at a box");
            helper.assertFalse(board.bypassesInput(new ItemStack(Items.STICK)), "other items skip the box");
            helper.assertFalse(board.bypassesInput(ItemStack.EMPTY), "an empty hand skips the box");
        }
        helper.succeed();
    }

    /** The same for the Warning Light's pattern box. */
    @GameTest(template = "empty")
    public static void dyeClicksPassThePatternBoard(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, LightworksBlocks.WARNING_LIGHT.getDefaultState().setValue(SignalLampBlock.FACING, Direction.UP));
        SignalLampBlockEntity lamp = helper.getBlockEntity(pos);
        ScrollValueBehaviour board = lamp.getBehaviour(ScrollValueBehaviour.TYPE);
        helper.assertTrue(board.bypassesInput(new ItemStack(Items.RED_DYE)), "dye stops at the box");
        helper.assertFalse(board.bypassesInput(new ItemStack(Items.STICK)), "other items skip the box");
        helper.assertFalse(board.bypassesInput(ItemStack.EMPTY), "an empty hand skips the box");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dyedColourPersists(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, LightworksBlocks.SEARCHLIGHT.getDefaultState().setValue(DirectionalLightBlock.FACING, Direction.UP));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE, 2));
        helper.useBlock(pos, player);

        DirectionalLightBlockEntity light = helper.getBlockEntity(pos);
        helper.assertTrue(light.lightState().colour() == DyeColor.RED, "dye did not colour the light: " + light.lightState().colour());
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "dyeing should use up one dye");

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BlockEntity loaded = BlockEntity.loadStatic(light.getBlockPos(), light.getBlockState(),
                light.saveWithFullMetadata(registries), registries);
        helper.assertTrue(loaded instanceof DirectionalLightBlockEntity copy && copy.lightState().colour() == DyeColor.RED,
                "colour lost in save and load");
        DirectionalLightBlockEntity client = new DirectionalLightBlockEntity(light.getType(), light.getBlockPos(),
                light.getBlockState());
        client.handleUpdateTag(light.getUpdateTag(registries), registries);
        helper.assertTrue(client.lightState().colour() == DyeColor.RED, "colour missing from the client update");
        helper.succeed();
    }
}
