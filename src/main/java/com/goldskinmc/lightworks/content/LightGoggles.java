package com.goldskinmc.lightworks.content;

import com.goldskinmc.lightworks.LightworksConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

/**
 * Lines of the Engineer's Goggles overlay for our lights, in Create's layout: a title, then indented
 * label and value pairs. Text comes from {@code lightworks.goggles.*} lang keys.
 */
public final class LightGoggles {
    private static final String INDENT = "    ";

    private LightGoggles() {
    }

    public static void title(List<Component> tooltip, Component name) {
        tooltip.add(Component.literal(INDENT).append(name.copy().withStyle(ChatFormatting.WHITE)));
    }

    /** "label: value" with the label grey and the value in {@code colour}. */
    public static void line(List<Component> tooltip, String key, ChatFormatting colour, Object... args) {
        tooltip.add(Component.literal(INDENT + " ").append(Component.translatable("lightworks.goggles." + key + ".label")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" "))
                .append(Component.translatable("lightworks.goggles." + key, args).withStyle(colour)));
    }

    /** A hint shown while sneaking, dark grey like Create's. */
    public static void hint(List<Component> tooltip, String key) {
        tooltip.add(Component.literal(INDENT + " ").append(Component.translatable("lightworks.goggles.hint." + key)
                .withStyle(ChatFormatting.DARK_GRAY)));
    }

    /** On or off, with the brightness or the reason it is dark. */
    public static void brightness(List<Component> tooltip, LightState state) {
        if (state.on()) {
            line(tooltip, "brightness", ChatFormatting.GOLD, state.brightness());
        } else if (LightworksConfig.invertRedstone()) {
            line(tooltip, "off_unpowered", ChatFormatting.DARK_RED);
        } else {
            line(tooltip, "off_powered", ChatFormatting.DARK_RED);
        }
        if (state.signal() > 0) {
            line(tooltip, "signal", ChatFormatting.RED, state.signal());
        }
    }

    public static void colour(List<Component> tooltip, @Nullable DyeColor colour) {
        MutableComponent name = colour == null ? Component.translatable("lightworks.goggles.colour.default")
                : Component.translatable("color.minecraft." + colour.getSerializedName());
        line(tooltip, "colour", ChatFormatting.AQUA, name);
    }
}
