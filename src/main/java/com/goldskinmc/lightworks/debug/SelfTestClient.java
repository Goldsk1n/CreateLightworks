package com.goldskinmc.lightworks.debug;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.compat.DynamicLights;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client half of {@link SelfTest}: logs the dynamic-light status once the test Searchlight has settled. */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class SelfTestClient {
    private static final int LOG_AT = 120;
    private static int ticksInWorld;

    private SelfTestClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!SelfTest.ENABLED || Minecraft.getInstance().level == null) {
            return;
        }
        if (++ticksInWorld == LOG_AT) {
            Lightworks.LOGGER.info("[selftest] client: tracked searchlights {}, {}",
                    TrackedLights.snapshot().size(), DynamicLights.status());
        }
    }
}
