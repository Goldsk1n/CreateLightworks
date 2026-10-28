package com.goldskinmc.lightworks;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only setup. */
@Mod(value = Lightworks.MOD_ID, dist = Dist.CLIENT)
public final class LightworksClient {
    public LightworksClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, LightworksClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
