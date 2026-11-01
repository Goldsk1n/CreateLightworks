package com.goldskinmc.lightworks.render;

import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** A lamp's item shows the colour it lights in: its lens texture is grey, tinted when drawn like a placed lamp's. */
public final class LampItemColours {
    private LampItemColours() {
    }

    public static void register(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF000000 | LightType.WARNING_LIGHT.colour() : -1,
                LightworksBlocks.WARNING_LIGHT.get());
        event.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF000000 | LightType.TAIL_LIGHT.colour() : -1,
                LightworksBlocks.TAIL_LIGHT.get());
    }
}
