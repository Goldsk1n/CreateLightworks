package com.goldskinmc.lightworks.debug;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.compat.DynamicLights;
import com.goldskinmc.lightworks.content.searchlight.SearchlightBlockEntity;
import com.goldskinmc.lightworks.host.BeamPose;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client half of {@link SelfTest}. Logs the dynamic-light status for the fixed light, then, for a
 * Searchlight on a vehicle, checks each tick that the lit spot is on the ground directly below the light
 * in world coordinates, and logs a summary once the vehicle is gone (scenario S4).
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class SelfTestClient {
    private static final int FIXED_LOG_AT = 120;
    private static int ticksInWorld;

    private static int vehicleSamples;
    private static int vehicleNoSpot;
    private static double maxHorizontalGap;
    private static double maxGroundError;
    private static double firstSpotX = Double.NaN;
    private static double lastSpotX = Double.NaN;
    private static boolean vehicleReported;

    private SelfTestClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!SelfTest.ENABLED || mc.level == null || mc.player == null) {
            return;
        }
        if (++ticksInWorld == FIXED_LOG_AT) {
            Lightworks.LOGGER.info("[selftest] fixed: client tracked searchlights {}, {}",
                    TrackedLights.snapshot().size(), DynamicLights.status());
        }
        boolean sawVehicleLight = false;
        for (SearchlightBlockEntity light : TrackedLights.snapshot()) {
            if (light.getLevel() != mc.level || light.isRemoved()) {
                continue;
            }
            BeamPose beam = light.worldBeam();
            if (!beam.onVehicle()) {
                continue;
            }
            sawVehicleLight = true;
            sample(beam, light.lastSpot(), mc.player.getY());
        }
        if (!sawVehicleLight && vehicleSamples > 0 && !vehicleReported) {
            vehicleReported = true;
            Lightworks.LOGGER.info(String.format(
                    "[selftest] vehicle: %d samples, %d without a spot, spot moved %.2f blocks east, "
                            + "max horizontal gap to the light %.3f, max height error vs ground %.3f, %s",
                    vehicleSamples, vehicleNoSpot, lastSpotX - firstSpotX, maxHorizontalGap, maxGroundError,
                    DynamicLights.status()));
        }
    }

    /** The light points straight down, so its spot belongs directly below it, half a block above the ground. */
    private static void sample(BeamPose beam, BeamSpot spot, double groundY) {
        vehicleSamples++;
        if (spot == null) {
            vehicleNoSpot++;
            return;
        }
        double gap = Math.hypot(spot.spotX() - beam.origin().x, spot.spotZ() - beam.origin().z);
        maxHorizontalGap = Math.max(maxHorizontalGap, gap);
        maxGroundError = Math.max(maxGroundError, Math.abs(spot.spotY() - (groundY + 0.5)));
        if (Double.isNaN(firstSpotX)) {
            firstSpotX = spot.spotX();
        }
        lastSpotX = spot.spotX();
    }
}
