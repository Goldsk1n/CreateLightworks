package com.goldskinmc.lightworks.render;

import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.content.carried.Headlamps;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/**
 * A small lens near the top-left corner of headgear with a headlamp fitted, in inventory slots, the hotbar and JEI:
 * fitted, Create's goggles and diving helmets looked exactly like plain ones, and JEI's fitting recipe read as goggles
 * and a Flashlight making goggles (owner, Nov 25). It is the corner of the Flashlight's own lens (its glint, streak and
 * body, three pixels square) in the lamp's dye, always as lit: chosen by the owner over an abstract lamp swatch, a
 * half-size Flashlight icon (too murky to see) and outlined versions. A slot decoration, as the durability bar is, so
 * Create's own item models stay untouched; dropped items in the world do not show it.
 */
public final class HeadlampBadge implements IItemDecorator {
    /** The Flashlight lens texture's top-left three by three, grey, tinted by the dye as the lens is. */
    private static final int[][] LENS = {{255, 245, 166}, {245, 189, 142}, {166, 142, 142}};
    /** One pixel in from the slot's corner. */
    private static final int INSET = 1;
    /** A dye darker than this (its brightest channel) is lifted to it, hue kept, so a black lamp still shows. */
    private static final int MIN_BRIGHTNESS = 110;

    private HeadlampBadge() {
    }

    /**
     * For every item: headgear that takes a headlamp is named by an item tag that packs may extend, which is not loaded
     * yet when decorations are registered, so the badge itself checks the stack it is drawn on.
     */
    public static void register(RegisterItemDecorationsEvent event) {
        HeadlampBadge badge = new HeadlampBadge();
        for (Item item : BuiltInRegistries.ITEM) {
            event.register(item, badge);
        }
    }

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!Headlamps.isFitted(stack)) {
            return false;
        }
        DyeColor colour = Headlamps.colour(stack);
        int rgb = lifted(colour == null ? LightType.FLASHLIGHT.colour() : colour.getTextureDiffuseColor() & 0xFFFFFF);
        graphics.pose().pushPose();
        // Above the item, as the stack count and durability bar are
        graphics.pose().translate(0, 0, 200);
        for (int row = 0; row < LENS.length; row++) {
            for (int col = 0; col < LENS[row].length; col++) {
                int px = x + INSET + col;
                int py = y + INSET + row;
                graphics.fill(px, py, px + 1, py + 1, 0xFF000000 | tinted(rgb, LENS[row][col]));
            }
        }
        graphics.pose().popPose();
        return false;
    }

    /** A dark colour raised so its brightest channel reaches {@link #MIN_BRIGHTNESS}, by the same amount on each. */
    static int lifted(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int lift = Math.max(0, MIN_BRIGHTNESS - Math.max(r, Math.max(g, b)));
        return Math.min(255, r + lift) << 16 | Math.min(255, g + lift) << 8 | Math.min(255, b + lift);
    }

    /** A grey texture value tinted by a colour, as the game tints a lens. */
    private static int tinted(int rgb, int grey) {
        int r = (rgb >> 16 & 0xFF) * grey / 255;
        int g = (rgb >> 8 & 0xFF) * grey / 255;
        int b = (rgb & 0xFF) * grey / 255;
        return r << 16 | g << 8 | b;
    }
}
