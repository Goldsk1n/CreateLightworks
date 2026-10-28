package com.goldskinmc.lightworks;

import com.goldskinmc.lightworks.registry.LightworksBlockEntities;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(Lightworks.MOD_ID)
public final class Lightworks {
    public static final String MOD_ID = "lightworks";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID);

    public Lightworks(IEventBus modBus, ModContainer container) {
        LOGGER.info("Lightworks loading");
        REGISTRATE.registerEventListeners(modBus);
        LightworksBlocks.register();
        LightworksBlockEntities.register();
        modBus.addListener(Lightworks::addToCreativeTabs);
        container.registerConfig(ModConfig.Type.SERVER, LightworksConfig.SPEC);
        REGISTRATE.addRawLang("lightworks.configuration.serverLightBlocks", "Server light blocks");
        REGISTRATE.addRawLang("lightworks.configuration.invertRedstone", "Invert redstone");
        REGISTRATE.addRawLang("lightworks.configuration.maxBeamRange", "Longest beam");
        REGISTRATE.addRawLang("lightworks.configuration.beamVisual", "Visible beam");
        REGISTRATE.addRawLang("lightworks.configuration.beamVisualWithShaders", "Visible beam with shaders");
        REGISTRATE.addRawLang("lightworks.configuration.debugOverlay", "Debug overlay (F3)");
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(LightworksBlocks.SEARCHLIGHT);
            event.accept(LightworksBlocks.HEADLIGHT);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
