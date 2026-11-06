package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.compat.DynamicLights;
import com.goldskinmc.lightworks.compat.Shaders;
import com.goldskinmc.lightworks.host.BeamPose;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Once a session, tells a player with a shader pack and no dynamic lights mod why a moving light leaves the world dark.
 * Under a shader pack our coloured light and spots are off, and a light on a vehicle, a contraption or a turning head
 * places no light blocks, so only LambDynamicLights or Sodium Dynamic Lights can light the world for it: without one,
 * all the player sees is its beam. A player reported a "weak" Searchlight on a walker this way.
 */
final class ShaderHint {
    private static boolean shown;

    private ShaderHint() {
    }

    /** Called each client tick: shows the hint the first time a lit moving light is about in that setup. */
    static void check(Iterable<BeamSource> sources) {
        if (shown || !LightworksClientConfig.shaderHint() || !Shaders.inUse() || DynamicLights.present()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        for (BeamSource source : sources) {
            if (source.isGone() || source.beam().length() <= 0) {
                continue;
            }
            BeamPose pose = source.tickBeam();
            if (pose != null && pose.moving()) {
                shown = true;
                mc.player.displayClientMessage(Component.translatable("lightworks.hint.moving_lights_shaders")
                        .withStyle(ChatFormatting.GRAY), false);
                Lightworks.LOGGER.info("Told the player that moving lights need a dynamic lights mod under a shader pack");
                return;
            }
        }
    }
}
