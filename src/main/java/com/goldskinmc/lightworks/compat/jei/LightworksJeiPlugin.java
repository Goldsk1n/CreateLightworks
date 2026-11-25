package com.goldskinmc.lightworks.compat.jei;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.carried.Headlamps;
import com.goldskinmc.lightworks.registry.LightworksComponents;
import com.goldskinmc.lightworks.registry.LightworksItems;
import com.goldskinmc.lightworks.registry.LightworksTags;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * Shows JEI the special crafting recipes it cannot list by itself, as plain shapeless crafting: the Flashlight fitted to
 * each headgear that takes a headlamp, taken off again, and dyed (owner, Nov 25: the headlamp was found only through
 * the Flashlight's tooltip). JEI finds this class by its annotation and only when it is installed; nothing else refers
 * to it.
 */
@JeiPlugin
public final class LightworksJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = Lightworks.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<RecipeHolder<CraftingRecipe>> recipes = new ArrayList<>();
        ItemStack flashlight = LightworksItems.FLASHLIGHT.asStack();
        for (Holder<Item> mount : BuiltInRegistries.ITEM.getTagOrEmpty(LightworksTags.HEADLAMP_MOUNTS)) {
            ItemStack headgear = new ItemStack(mount);
            String name = BuiltInRegistries.ITEM.getKey(mount.value()).getPath();
            ItemStack fitted = Headlamps.fit(headgear.copy(), flashlight.copy());
            recipes.add(shapeless("headlamp_fitting/" + name, fitted, Ingredient.of(headgear), Ingredient.of(flashlight)));
            recipes.add(shapeless("headlamp_removing/" + name, flashlight, Ingredient.of(fitted)));
        }
        for (DyeColor colour : DyeColor.values()) {
            ItemStack dyed = flashlight.copy();
            dyed.set(LightworksComponents.LAMP_COLOUR.get(), colour);
            recipes.add(shapeless("flashlight_dyeing/" + colour.getName(), dyed, Ingredient.of(flashlight),
                    Ingredient.of(DyeItem.byColor(colour))));
        }
        registration.addRecipes(RecipeTypes.CRAFTING, recipes);
    }

    private static RecipeHolder<CraftingRecipe> shapeless(String path, ItemStack result, Ingredient... ingredients) {
        return new RecipeHolder<>(Lightworks.id("jei/" + path),
                new ShapelessRecipe("", CraftingBookCategory.MISC, result, NonNullList.of(Ingredient.EMPTY, ingredients)));
    }
}
