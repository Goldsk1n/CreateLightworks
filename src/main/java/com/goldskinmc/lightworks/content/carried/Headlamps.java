package com.goldskinmc.lightworks.content.carried;

import com.goldskinmc.lightworks.registry.LightworksComponents;
import com.goldskinmc.lightworks.registry.LightworksItems;
import com.goldskinmc.lightworks.registry.LightworksTags;
import com.simibubi.create.AllItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A headlamp is a Flashlight fitted to headgear: Create's Engineer's Goggles or a diving helmet (item tag
 * {@code lightworks:headlamp_mounts}). It is data on that item, not an item of ours, so the headgear stays itself: Create
 * tells a diving helmet by its class for Backtank air and the Netherite helmet's lava protection, and goggles keep their
 * overlay. On, off and dye are the same components as on a Flashlight.
 */
public final class Headlamps {
    private Headlamps() {
    }

    /** Headgear a headlamp can be fitted to. */
    public static boolean canTake(ItemStack stack) {
        return !stack.isEmpty() && stack.is(LightworksTags.HEADLAMP_MOUNTS);
    }

    public static boolean isFitted(ItemStack stack) {
        return canTake(stack) && stack.getOrDefault(LightworksComponents.HEADLAMP.get(), false);
    }

    public static boolean isOn(ItemStack stack) {
        return isFitted(stack) && stack.getOrDefault(LightworksComponents.LAMP_ON.get(), false);
    }

    public static @Nullable DyeColor colour(ItemStack stack) {
        return stack.get(LightworksComponents.LAMP_COLOUR.get());
    }

    /** What the player wears on the head, if a headlamp is fitted to it; empty otherwise. */
    public static ItemStack worn(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        return isFitted(head) ? head : ItemStack.EMPTY;
    }

    /** True for Create's goggles, on which the lamp sits a little lower than on a helmet's brow. */
    public static boolean onGoggles(ItemStack stack) {
        return AllItems.GOGGLES.isIn(stack);
    }

    /** The headgear with the Flashlight fitted: everything else on it (enchantments, damage, name) stays. Off at first. */
    public static ItemStack fit(ItemStack headgear, ItemStack flashlight) {
        ItemStack fitted = headgear.copyWithCount(1);
        fitted.set(LightworksComponents.HEADLAMP.get(), true);
        fitted.set(LightworksComponents.LAMP_ON.get(), false);
        DyeColor colour = FlashlightItem.colour(flashlight);
        if (colour != null) {
            fitted.set(LightworksComponents.LAMP_COLOUR.get(), colour);
        } else {
            fitted.remove(LightworksComponents.LAMP_COLOUR.get());
        }
        return fitted;
    }

    /** The headgear with its headlamp taken off. */
    public static ItemStack strip(ItemStack headgear) {
        ItemStack bare = headgear.copyWithCount(1);
        bare.remove(LightworksComponents.HEADLAMP.get());
        bare.remove(LightworksComponents.LAMP_ON.get());
        bare.remove(LightworksComponents.LAMP_COLOUR.get());
        return bare;
    }

    /** The Flashlight a headlamp gives back when taken off, with its dye. */
    public static ItemStack flashlightFrom(ItemStack headgear) {
        ItemStack flashlight = new ItemStack(LightworksItems.FLASHLIGHT.get());
        DyeColor colour = colour(headgear);
        if (colour != null) {
            flashlight.set(LightworksComponents.LAMP_COLOUR.get(), colour);
        }
        return flashlight;
    }
}
