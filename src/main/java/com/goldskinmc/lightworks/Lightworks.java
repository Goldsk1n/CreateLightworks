package com.goldskinmc.lightworks;

import com.goldskinmc.lightworks.ponder.PonderSchematics;
import com.goldskinmc.lightworks.registry.LightworksBlockEntities;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.goldskinmc.lightworks.registry.LightworksCreativeTab;
import com.goldskinmc.lightworks.registry.LightworksLang;
import com.goldskinmc.lightworks.registry.LightworksRecipes;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.slf4j.Logger;

@Mod(Lightworks.MOD_ID)
public final class Lightworks {
    public static final String MOD_ID = "lightworks";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID)
            .setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                    .andThen(TooltipModifier.mapNull(KineticStats.create(item))));

    public Lightworks(IEventBus modBus, ModContainer container) {
        LOGGER.info("Lightworks loading");
        REGISTRATE.registerEventListeners(modBus);
        LightworksBlocks.register();
        LightworksBlockEntities.register();
        LightworksRecipes.register();
        LightworksCreativeTab.register(modBus);
        modBus.addListener(GatherDataEvent.class, PonderSchematics::gatherData);
        container.registerConfig(ModConfig.Type.SERVER, LightworksConfig.SPEC);
        LightworksLang.register();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
