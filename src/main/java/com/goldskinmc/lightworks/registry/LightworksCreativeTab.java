package com.goldskinmc.lightworks.registry;

import com.goldskinmc.lightworks.Lightworks;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Our own creative tab, placed after Create's. */
public final class LightworksCreativeTab {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Lightworks.MOD_ID);
    private static final ResourceKey<CreativeModeTab> CREATE_PALETTES =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath("create", "palettes"));

    static {
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.lightworks"))
                .icon(LightworksBlocks.SEARCHLIGHT::asStack)
                .withTabsBefore(CREATE_PALETTES)
                .displayItems((parameters, output) -> contents().forEach(output::accept))
                .build());
    }

    private LightworksCreativeTab() {
    }

    /** Beam lights, their mount, then the vehicle lamps. */
    public static List<ItemLike> contents() {
        return List.of(LightworksBlocks.SEARCHLIGHT, LightworksBlocks.FLOODLIGHT, LightworksBlocks.PAN_TILT_HEAD,
                LightworksBlocks.ROTATING_BEACON, LightworksBlocks.WARNING_LIGHT, LightworksBlocks.HEADLIGHT,
                LightworksBlocks.TAIL_LIGHT, LightworksBlocks.NAVIGATION_LIGHT);
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
