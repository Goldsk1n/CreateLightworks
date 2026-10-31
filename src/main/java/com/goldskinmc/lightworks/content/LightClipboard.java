package com.goldskinmc.lightworks.content;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What lights share on Create's Clipboard, under one key of their own: a paste applies what the target understands,
 * here the dye colour. Like Create's filters, a paste takes the item from the inventory, one dye, free in creative.
 * No colour is copied too, so a paste makes a dyed light undyed, for free.
 */
public final class LightClipboard {
    public static final String KEY = "LightworksLight";
    private static final String COLOUR = "Colour";

    private LightClipboard() {
    }

    public static void writeColour(CompoundTag tag, LightState state) {
        tag.putString(COLOUR, state.colour() == null ? "" : state.colour().getSerializedName());
    }

    /** False when there is nothing from a light on the Clipboard. */
    public static boolean hasColour(CompoundTag tag) {
        return tag.contains(COLOUR);
    }

    /** Gives the light the copied colour, using one dye; false, with a word to the player, without the dye. */
    public static boolean readColour(CompoundTag tag, LightState state, Player player) {
        DyeColor colour = DyeColor.byName(tag.getString(COLOUR), null);
        if (colour == state.colour()) {
            return true;
        }
        if (colour != null && !player.isCreative() && !useDye(player, colour)) {
            needsDye(player, colour);
            return false;
        }
        state.setColour(colour);
        return true;
    }

    private static boolean useDye(Player player, DyeColor colour) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof DyeItem dye && dye.getDyeColor() == colour) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Create says "pasted" when the click is done, so this waits its turn or nobody would read it. */
    private static void needsDye(Player player, DyeColor colour) {
        Component message = Component.translatable("lightworks.clipboard.needs_dye",
                new ItemStack(DyeItem.byColor(colour)).getHoverName()).withStyle(ChatFormatting.RED);
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.tell(new TickTask(server.getTickCount(), () -> player.displayClientMessage(message, true)));
        }
    }
}
