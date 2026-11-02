package com.goldskinmc.lightworks.ponder;

import com.goldskinmc.lightworks.Lightworks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** One Ponder scene per block, all under a Lightworks tag in the Ponder index. */
public class LightworksPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return Lightworks.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        LightworksScenes.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        LightworksScenes.registerTags(helper);
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        helper.registerSharedText("aim", "Hold right-click on Pan or Tilt to aim it");
        helper.registerSharedText("redstone", "Redstone dims it. A full signal turns it off");
        helper.registerSharedText("dye", "Right-click with a dye to change its colour");
    }
}
