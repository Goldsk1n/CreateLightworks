package com.goldskinmc.lightworks;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Lightworks.MOD_ID)
public final class Lightworks {
    public static final String MOD_ID = "lightworks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Lightworks(IEventBus modBus, ModContainer container) {
        LOGGER.info("Lightworks loading");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
