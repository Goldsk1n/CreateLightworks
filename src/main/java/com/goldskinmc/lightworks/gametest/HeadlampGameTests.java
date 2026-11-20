package com.goldskinmc.lightworks.gametest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.carried.Headlamps;
import com.goldskinmc.lightworks.content.carried.ToggleHeadlampPayload;
import com.goldskinmc.lightworks.registry.LightworksComponents;
import com.goldskinmc.lightworks.registry.LightworksItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The headlamp: fitting a Flashlight to Create's goggles or a diving helmet in a crafting grid keeps the headgear's
 * enchantments, damage and name; the headgear alone gives the Flashlight back; the key's packet switches only the lamp
 * on the sender's head.
 */
@GameTestHolder(Lightworks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeadlampGameTests {
    private static final String EMPTY = "empty";

    private HeadlampGameTests() {
    }

    private static ItemStack create(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", id)));
    }

    private static ItemStack flashlight(DyeColor colour) {
        ItemStack stack = new ItemStack(LightworksItems.FLASHLIGHT.get());
        if (colour != null) {
            stack.set(LightworksComponents.LAMP_COLOUR.get(), colour);
        }
        return stack;
    }

    private static CraftingInput grid(ItemStack... items) {
        List<ItemStack> grid = new ArrayList<>(List.of(items));
        while (grid.size() < 9) {
            grid.add(grid.size() / 2, ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, grid);
    }

    private static Optional<RecipeHolder<CraftingRecipe>> recipe(Level level, CraftingInput input) {
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
    }

    private static ItemStack craft(Level level, ItemStack... items) {
        CraftingInput input = grid(items);
        return recipe(level, input).map(holder -> holder.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    @GameTest(template = EMPTY)
    public static void fittingKeepsTheHelmetsEnchantmentsDamageAndName(GameTestHelper helper) {
        Level level = helper.getLevel();
        ItemStack helmet = create("copper_diving_helmet");
        helper.assertTrue(Headlamps.canTake(helmet), "a Copper Diving Helmet cannot take a headlamp");
        helmet.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
        helmet.setDamageValue(17);
        helmet.set(DataComponents.CUSTOM_NAME, Component.literal("Diver"));
        ItemStack fitted = craft(level, helmet, flashlight(DyeColor.LIME));
        helper.assertTrue(fitted.is(helmet.getItem()), "fitting gave " + fitted + ", not the helmet");
        helper.assertTrue(Headlamps.isFitted(fitted), "no headlamp on the helmet");
        helper.assertTrue(!Headlamps.isOn(fitted), "the headlamp came on lit");
        helper.assertTrue(Headlamps.colour(fitted) == DyeColor.LIME, "the lamp's dye did not carry over");
        helper.assertTrue(fitted.getDamageValue() == 17, "damage " + fitted.getDamageValue() + ", not 17");
        helper.assertTrue("Diver".equals(fitted.getHoverName().getString()), "the helmet lost its name");
        helper.assertTrue(fitted.getEnchantments().size() == 1, "the helmet lost its enchantment");
        helper.assertTrue(craft(level, fitted, flashlight(null)).isEmpty(), "a second headlamp fitted");
        helper.assertTrue(craft(level, create("goggles"), flashlight(null), flashlight(null)).isEmpty(),
                "fitted with two Flashlights in the grid");
        helper.assertTrue(craft(level, new ItemStack(Items.IRON_HELMET), flashlight(null)).isEmpty(),
                "fitted to an iron helmet");
        helper.assertTrue(Headlamps.isFitted(craft(level, create("goggles"), flashlight(null))), "goggles did not take one");
        helper.assertTrue(Headlamps.isFitted(craft(level, create("netherite_diving_helmet"), flashlight(null))),
                "a Netherite Diving Helmet did not take one");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theHelmetAloneGivesTheFlashlightBack(GameTestHelper helper) {
        Level level = helper.getLevel();
        ItemStack goggles = create("goggles");
        goggles.set(DataComponents.CUSTOM_NAME, Component.literal("Specs"));
        ItemStack fitted = Headlamps.fit(goggles, flashlight(DyeColor.RED));
        fitted.set(LightworksComponents.LAMP_ON.get(), true);
        CraftingInput input = grid(fitted);
        RecipeHolder<CraftingRecipe> holder = recipe(level, input).orElse(null);
        helper.assertTrue(holder != null, "no recipe takes a headlamp off");
        ItemStack out = holder.value().assemble(input, level.registryAccess());
        helper.assertTrue(out.is(LightworksItems.FLASHLIGHT.get()), "taking it off gave " + out + ", not a Flashlight");
        helper.assertTrue(out.get(LightworksComponents.LAMP_COLOUR.get()) == DyeColor.RED, "the Flashlight lost its dye");
        NonNullList<ItemStack> left = holder.value().getRemainingItems(input);
        ItemStack bare = left.stream().filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(bare.is(goggles.getItem()), "the goggles did not stay in the grid");
        helper.assertTrue(!Headlamps.isFitted(bare) && bare.get(LightworksComponents.LAMP_ON.get()) == null
                && bare.get(LightworksComponents.LAMP_COLOUR.get()) == null, "the goggles kept the lamp's data");
        helper.assertTrue("Specs".equals(bare.getHoverName().getString()), "the goggles lost their name");
        helper.assertTrue(craft(level, goggles).isEmpty(), "bare goggles gave something");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theKeySwitchesOnlyTheWornLamp(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(!ToggleHeadlampPayload.toggle(player), "switched with nothing worn");
        ItemStack held = Headlamps.fit(create("goggles"), flashlight(null));
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        helper.assertTrue(!ToggleHeadlampPayload.toggle(player), "switched a headlamp only held in the hand");
        helper.assertTrue(!Headlamps.isOn(player.getMainHandItem()), "the held headlamp came on");
        player.setItemSlot(EquipmentSlot.HEAD, Headlamps.fit(create("copper_diving_helmet"), flashlight(null)));
        helper.assertTrue(ToggleHeadlampPayload.toggle(player), "the worn headlamp did not switch");
        helper.assertTrue(Headlamps.isOn(player.getItemBySlot(EquipmentSlot.HEAD)), "the worn headlamp is not on");
        helper.assertTrue(!Headlamps.isOn(player.getMainHandItem()), "switching reached the held one");
        ToggleHeadlampPayload.toggle(player);
        helper.assertTrue(!Headlamps.isOn(player.getItemBySlot(EquipmentSlot.HEAD)), "the worn headlamp did not switch off");
        player.setItemSlot(EquipmentSlot.HEAD, create("goggles"));
        helper.assertTrue(!ToggleHeadlampPayload.toggle(player), "switched bare goggles");
        helper.succeed();
    }
}
