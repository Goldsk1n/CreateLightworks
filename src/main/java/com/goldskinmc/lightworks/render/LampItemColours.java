package com.goldskinmc.lightworks.render;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.content.carried.FlashlightItem;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.goldskinmc.lightworks.registry.LightworksItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.DyeColor;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * A lamp's item shows the colour it lights in: its lens texture is grey, tinted when drawn like a placed lamp's. A
 * Flashlight's lens takes its dye, warm white undyed, as its beam does.
 */
public final class LampItemColours {
    private LampItemColours() {
    }

    public static void register(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF000000 | LightType.WARNING_LIGHT.colour() : -1,
                LightworksBlocks.WARNING_LIGHT.get());
        event.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF000000 | LightType.TAIL_LIGHT.colour() : -1,
                LightworksBlocks.TAIL_LIGHT.get());
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) {
                return -1;
            }
            DyeColor colour = FlashlightItem.colour(stack);
            return 0xFF000000 | (colour == null ? LightType.FLASHLIGHT.colour() : colour.getTextureDiffuseColor());
        }, LightworksItems.FLASHLIGHT.get());
    }

    /** The Flashlight's model while it is on, its lens full-bright: item property {@code lightworks:on}. */
    public static void registerProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(LightworksItems.FLASHLIGHT.get(), Lightworks.id("on"),
                (stack, level, entity, seed) -> FlashlightItem.isOn(stack) ? 1 : 0));
    }
}
