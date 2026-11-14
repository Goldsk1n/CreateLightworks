package com.goldskinmc.lightworks.registry;

import static com.goldskinmc.lightworks.Lightworks.REGISTRATE;

import com.goldskinmc.lightworks.content.carried.FlashlightItem;
import com.tterrag.registrate.util.entry.ItemEntry;

/** Items that are not blocks: carried lamps. Their models come from resources, not Registrate. */
public final class LightworksItems {
    public static final ItemEntry<FlashlightItem> FLASHLIGHT = REGISTRATE.item("flashlight", FlashlightItem::new)
            .properties(p -> p.stacksTo(1))
            .lang("Flashlight")
            .model((c, p) -> {
            })
            .register();

    private LightworksItems() {
    }

    public static void register() {
    }
}
