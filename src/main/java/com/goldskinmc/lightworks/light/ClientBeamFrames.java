package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * The once-a-frame work for lights that follow their beam each frame ({@link ClientBeam#followSpots}): client only, and
 * nothing outside the client refers to it, so the client's world can be fetched here and handed on.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class ClientBeamFrames {
    private ClientBeamFrames() {
    }

    /** Before the level is drawn, so a moved light block is in this frame's light update. */
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            ClientBeam.followSpots(level, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }
}
