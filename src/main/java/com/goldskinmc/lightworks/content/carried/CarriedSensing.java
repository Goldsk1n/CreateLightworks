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

    /** One key per player and lamp (a Flashlight and a headlamp sweep apart), kept while the player is. */
    private static final java.util.Map<Player, Object[]> KEYS = new java.util.WeakHashMap<>();

    private static Object key(Player player, LightType type) {
        Object[] keys = KEYS.computeIfAbsent(player, p -> new Object[] {new Object(), new Object()});
        return keys[type == LightType.HEADLAMP ? 1 : 0];
    }

    /** Tells sensors in reach of a lamp carried by {@code player} where its beam is. */
    public static void shine(Player player, LightType type, @Nullable DyeColor colour) {
        ConeFalloff shape = Objects.requireNonNull(type.beam());
        Vec3 look = player.getViewVector(1);
        Vec3 origin = player.getEyePosition().add(look.scale(AHEAD));
        if (!BeamSensors.anyWithin(player.level(), origin, BeamTrace.range(shape))) {
            return;
        }
        // Its square keeps to the player's right, as the client's does (CarriedBeam)
        double yaw = Math.toRadians(player.getViewYRot(1));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        BeamSensors.shine(player.level(), key(player, type), new BeamPose(origin, look, true, right), shape,
                type.lensHalf(), 15, colour);
    }
}
