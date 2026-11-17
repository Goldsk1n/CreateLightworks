package com.goldskinmc.lightworks.content.carried;

import com.goldskinmc.lightworks.registry.LightworksComponents;
import com.goldskinmc.lightworks.registry.LightworksRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A Flashlight and one dye anywhere in a crafting grid give the Flashlight in that colour, keeping everything else on
 * it (name, on or off), as Create dyes its Toolbox. Washing takes the colour off: a fan through water or a cauldron.
 */
public class FlashlightDyeingRecipe extends CustomRecipe {
    public FlashlightDyeingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Found found = find(input);
        if (found == null) {
            return ItemStack.EMPTY;
        }
        ItemStack dyed = found.flashlight.copyWithCount(1);
        dyed.set(LightworksComponents.LAMP_COLOUR.get(), found.colour);
        return dyed;
    }

    /** One Flashlight and one dye of another colour than it has, nothing else; null otherwise. */
    private static @Nullable Found find(CraftingInput input) {
        ItemStack flashlight = ItemStack.EMPTY;
        DyeColor colour = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof FlashlightItem && flashlight.isEmpty()) {
                flashlight = stack;
            } else if (stack.getItem() instanceof DyeItem dye && colour == null) {
                colour = dye.getDyeColor();
            } else {
                return null;
            }
        }
        if (flashlight.isEmpty() || colour == null || colour == FlashlightItem.colour(flashlight)) {
            return null;
        }
        return new Found(flashlight, colour);
    }

    private record Found(ItemStack flashlight, DyeColor colour) {
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LightworksRecipeSerializers.FLASHLIGHT_DYEING.get();
    }
}
