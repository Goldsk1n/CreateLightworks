package com.goldskinmc.lightworks;

import com.goldskinmc.lightworks.ponder.LightworksPonder;
import com.goldskinmc.lightworks.render.Partials;
import net.neoforged.api.distmarker.Dist;
import com.goldskinmc.lightworks.render.EdgeLightColours;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only setup. */
@Mod(value = Lightworks.MOD_ID, dist = Dist.CLIENT)
public final class LightworksClient {
    public LightworksClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(Partials::register);
        modBus.addListener(EdgeLightColours::register);
        modBus.addListener(FMLClientSetupEvent.class, event -> LightworksPonder.registerPlugin());
        container.registerConfig(ModConfig.Type.CLIENT, LightworksClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
