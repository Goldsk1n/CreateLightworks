package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.carried.FlashlightItem;
import com.goldskinmc.lightworks.registry.LightworksComponents;
import com.goldskinmc.lightworks.registry.LightworksItems;
import com.simibubi.create.AllRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dyeing a Flashlight in a crafting grid, and washing the dye off with a fan through water or in a cauldron. */
@GameTestHolder(Lightworks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FlashlightGameTests {
    private static final String EMPTY = "empty";

    private FlashlightGameTests() {
    }

    /** A lit, renamed Flashlight, coloured if {@code colour} is not null. */
    private static ItemStack flashlight(DyeColor colour) {
        ItemStack stack = new ItemStack(LightworksItems.FLASHLIGHT.get());
        stack.set(LightworksComponents.LAMP_ON.get(), true);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Torchy"));
        if (colour != null) {
            stack.set(LightworksComponents.LAMP_COLOUR.get(), colour);
        }
        return stack;
    }

    private static ItemStack craft(Level level, ItemStack... items) {
        List<ItemStack> grid = new ArrayList<>(List.of(items));
        while (grid.size() < 9) {
            grid.add(grid.size() / 2, ItemStack.EMPTY);
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    @GameTest(template = EMPTY)
    public static void dyeInACraftingGridKeepsTheRest(GameTestHelper helper) {
        Level level = helper.getLevel();
        ItemStack red = craft(level, flashlight(null), new ItemStack(Items.RED_DYE));
        helper.assertTrue(red.is(LightworksItems.FLASHLIGHT.get()), "no Flashlight from a Flashlight and a red dye");
        helper.assertTrue(FlashlightItem.colour(red) == DyeColor.RED, "dyed " + FlashlightItem.colour(red) + ", not red");
        helper.assertTrue(FlashlightItem.isOn(red), "dyeing switched it off");
        helper.assertTrue("Torchy".equals(red.getHoverName().getString()), "dyeing lost its name");
        ItemStack blue = craft(level, red, new ItemStack(Items.BLUE_DYE));
        helper.assertTrue(FlashlightItem.colour(blue) == DyeColor.BLUE, "a red Flashlight did not dye blue");
        helper.assertTrue(craft(level, red, new ItemStack(Items.RED_DYE)).isEmpty(), "dyed red again");
        helper.assertTrue(craft(level, flashlight(null), new ItemStack(Items.RED_DYE), new ItemStack(Items.BLUE_DYE))
                .isEmpty(), "dyed with two dyes at once");
        helper.assertTrue(craft(level, flashlight(null), flashlight(null), new ItemStack(Items.RED_DYE)).isEmpty(),
                "dyed two Flashlights at once");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void fanWashingTakesOnlyTheDyeOff(GameTestHelper helper) {
        Level level = helper.getLevel();
        RecipeType<Recipe<SingleRecipeInput>> washing = AllRecipeTypes.SPLASHING.getType();
        Optional<RecipeHolder<Recipe<SingleRecipeInput>>> dyed =
                level.getRecipeManager().getRecipeFor(washing, new SingleRecipeInput(flashlight(DyeColor.LIME)), level);
        helper.assertTrue(dyed.isPresent(), "a dyed Flashlight has no washing recipe");
        ItemStack result = dyed.get().value().getResultItem(level.registryAccess());
        helper.assertTrue(result.is(LightworksItems.FLASHLIGHT.get()) && FlashlightItem.colour(result) == null,
                "washing gives " + result + ", not an undyed Flashlight");
        helper.assertTrue(level.getRecipeManager().getRecipeFor(washing, new SingleRecipeInput(flashlight(null)), level)
                .isEmpty(), "an undyed Flashlight would be washed too");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cauldronWashesTheDyeOff(GameTestHelper helper) {
        BlockPos cauldron = new BlockPos(2, 1, 2);
        helper.setBlock(cauldron, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CauldronInteraction washing = CauldronInteraction.WATER.map().get(LightworksItems.FLASHLIGHT.get());
        helper.assertTrue(washing != null, "no cauldron interaction for the Flashlight");

        ItemStack plain = flashlight(null);
        ItemInteractionResult none = washing.interact(helper.getBlockState(cauldron), helper.getLevel(),
                helper.absolutePos(cauldron), player, InteractionHand.MAIN_HAND, plain);
        helper.assertTrue(none == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                "an undyed Flashlight was washed");
        helper.assertBlockProperty(cauldron, LayeredCauldronBlock.LEVEL, 3);

        ItemStack red = flashlight(DyeColor.RED);
        BlockState full = helper.getBlockState(cauldron);
        washing.interact(full, helper.getLevel(), helper.absolutePos(cauldron), player, InteractionHand.MAIN_HAND, red);
        helper.assertTrue(FlashlightItem.colour(red) == null, "the cauldron left it " + FlashlightItem.colour(red));
        helper.assertTrue(FlashlightItem.isOn(red) && "Torchy".equals(red.getHoverName().getString()),
                "washing in a cauldron lost more than the dye");
        helper.assertBlockProperty(cauldron, LayeredCauldronBlock.LEVEL, 2);
        helper.succeed();
    }
}
