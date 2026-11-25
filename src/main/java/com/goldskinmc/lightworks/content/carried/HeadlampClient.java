package com.goldskinmc.lightworks.content.carried;

import com.goldskinmc.lightworks.registry.LightworksItems;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The headlamp on the client: its key (H by default, in Controls under Create: Lightworks), which asks the server to
 * switch the worn lamp ({@link ToggleHeadlampPayload}), and the lines it adds to the headgear's tooltip.
 */
public final class HeadlampClient {
    public static final KeyMapping KEY = new KeyMapping("key.lightworks.headlamp", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.lightworks");

    private HeadlampClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(KEY));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onTick());
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, HeadlampClient::onTooltip);
    }

    private static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        while (KEY.consumeClick()) {
            if (mc.player != null && !Headlamps.worn(mc.player).isEmpty()) {
                PacketDistributor.sendToServer(new ToggleHeadlampPayload());
            }
        }
    }

    /** Under the headgear's name: that a headlamp is fitted and whether it is on, its dye, and the key. */
    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!Headlamps.isFitted(stack)) {
            // Headgear that can take one: the only other place a player learns of the headlamp is the Flashlight's own
            // tooltip (owner, Nov 25)
            if (Headlamps.canTake(stack)) {
                event.getToolTip().add(Math.min(1, event.getToolTip().size()),
                        Component.translatable("lightworks.headlamp.fit_hint",
                                LightworksItems.FLASHLIGHT.get().getDescription().copy().withStyle(ChatFormatting.GRAY))
                                .withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }
        List<Component> tooltip = event.getToolTip();
        int at = Math.min(1, tooltip.size());
        tooltip.add(at++, Component.translatable("lightworks.headlamp.fitted",
                Component.translatable(Headlamps.isOn(stack) ? "lightworks.headlamp.on" : "lightworks.headlamp.off"))
                .withStyle(ChatFormatting.GRAY));
        DyeColor colour = Headlamps.colour(stack);
        if (colour != null) {
            tooltip.add(at++, Component.translatable("item.lightworks.flashlight.colour",
                    Component.translatable("color.minecraft." + colour.getName())
                            .withStyle(style -> style.withColor(FlashlightItem.readable(colour.getTextColor()))))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(at, Component.translatable("lightworks.headlamp.key_hint",
                keyName().copy().withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * The headlamp key's name: a letter or digit by its Latin label, the one printed on every keyboard, else the game's
     * own name. The game names a key by what the keyboard layout types on it, once a session: with a Russian layout on at
     * that moment, H showed as its Cyrillic letter, while Ponder's W, named earlier in English, did not (owner, Nov 25).
     */
    private static Component keyName() {
        InputConstants.Key key = KEY.getKey();
        int code = key.getValue();
        if (key.getType() == InputConstants.Type.KEYSYM
                && (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z || code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9)) {
            return Component.literal(String.valueOf((char) code));
        }
        return KEY.getTranslatedKeyMessage();
    }
}
