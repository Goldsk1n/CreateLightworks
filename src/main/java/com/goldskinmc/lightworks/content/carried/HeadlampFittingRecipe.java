package com.goldskinmc.lightworks.content.carried;

import com.goldskinmc.lightworks.registry.LightworksRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Goggles or a diving helmet without a headlamp and a Flashlight, nothing else, anywhere in a crafting grid: the headgear
 * with the lamp fitted ({@link Headlamps#fit}), keeping its enchantments, damage and name. Special recipes do not show
 * in recipe viewers, so the Flashlight's tooltip says how.
 */
public class HeadlampFittingRecipe extends CustomRecipe {
    public HeadlampFittingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Found found = find(input);
        return found == null ? ItemStack.EMPTY : Headlamps.fit(found.headgear(), found.flashlight());
    }

    private record Found(ItemStack headgear, ItemStack flashlight) {
    }

    /** The headgear and the Flashlight, when they are all the grid holds; null otherwise. */
    private static @Nullable Found find(CraftingInput input) {
        ItemStack headgear = ItemStack.EMPTY;
        ItemStack flashlight = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (Headlamps.canTake(stack) && !Headlamps.isFitted(stack) && headgear.isEmpty()) {
                headgear = stack;
            } else if (stack.getItem() instanceof FlashlightItem && flashlight.isEmpty()) {
                flashlight = stack;
            } else {
                return null;
            }
        }
        return headgear.isEmpty() || flashlight.isEmpty() ? null : new Found(headgear, flashlight);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LightworksRecipeSerializers.HEADLAMP_FITTING.get();
    }
}
