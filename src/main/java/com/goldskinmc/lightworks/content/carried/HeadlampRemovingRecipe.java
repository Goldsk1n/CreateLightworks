package com.goldskinmc.lightworks.content.carried;

import com.goldskinmc.lightworks.registry.LightworksRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Goggles or a diving helmet with a headlamp, alone in a crafting grid: the Flashlight comes out (with its dye), and the
 * headgear stays in the grid without its lamp, everything else on it kept.
 */
public class HeadlampRemovingRecipe extends CustomRecipe {
    public HeadlampRemovingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) >= 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int slot = find(input);
        return slot < 0 ? ItemStack.EMPTY : Headlamps.flashlightFrom(input.getItem(slot));
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(input);
        int slot = find(input);
        if (slot >= 0) {
            remaining.set(slot, Headlamps.strip(input.getItem(slot)));
        }
        return remaining;
    }

    /** The slot of the one fitted headgear when it is alone in the grid; -1 otherwise. */
    private static int find(CraftingInput input) {
        int found = -1;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (found >= 0 || !Headlamps.isFitted(stack)) {
                return -1;
            }
            found = i;
        }
        return found;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LightworksRecipeSerializers.HEADLAMP_REMOVING.get();
    }
}
