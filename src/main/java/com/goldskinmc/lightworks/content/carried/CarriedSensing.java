package com.goldskinmc.lightworks.content.carried;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.light.BeamSensors;
import com.goldskinmc.lightworks.light.BeamTrace;
import java.util.Objects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Carried lamps as the server sees them, for Beam Sensors: only clients trace a Flashlight's or headlamp's beam, so the
 * server takes its way from the player's eyes along where they look (where the client aims it too, at the crosshair) and
 * tells sensors in reach. Nothing more without a sensor near.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID)
public final class CarriedSensing {
    /** The beam starts this far ahead of the eyes, past the face. */
    private static final double AHEAD = 0.4;

    private CarriedSensing() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.isSpectator()) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (FlashlightItem.isOn(stack)) {
                shine(player, LightType.FLASHLIGHT, FlashlightItem.colour(stack));
                break;
            }
        }
        ItemStack head = Headlamps.worn(player);
        if (Headlamps.isOn(head)) {
            shine(player, LightType.HEADLAMP, Headlamps.colour(head));
        }
    }

    /** Tells sensors in reach of a lamp carried by {@code player} where its beam is. */
    public static void shine(Player player, LightType type, @Nullable DyeColor colour) {
        ConeFalloff shape = Objects.requireNonNull(type.beam());
        Vec3 look = player.getViewVector(1);
        Vec3 origin = player.getEyePosition().add(look.scale(AHEAD));
        if (!BeamSensors.anyWithin(player.level(), origin, BeamTrace.range(shape))) {
            return;
        }
        BeamSensors.shine(player.level(), new BeamPose(origin, look, true), shape, type.lensHalf(), 15, colour);
    }
}
