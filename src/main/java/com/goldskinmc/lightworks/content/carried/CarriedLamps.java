package com.goldskinmc.lightworks.content.carried;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.light.ClientBeams;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps a {@link CarriedBeam} for every player in the client world holding a lit lamp, the main hand's first, and one
 * more for a lit headlamp on their head: a player may carry both. The game already sends held and worn items to everyone
 * nearby, so other players' lamps need nothing from the server.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class CarriedLamps {
    private static final Map<UUID, CarriedBeam> HELD = new HashMap<>();
    private static final Map<UUID, CarriedBeam> WORN = new HashMap<>();

    private CarriedLamps() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        Set<UUID> held = new HashSet<>();
        Set<UUID> worn = new HashSet<>();
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            InteractionHand hand = litHand(player);
            if (hand != null) {
                held.add(player.getUUID());
                beam(HELD, player, LightType.FLASHLIGHT, false)
                        .tick(hand, FlashlightItem.colour(player.getItemInHand(hand)));
            }
            ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
            if (Headlamps.isOn(head)) {
                worn.add(player.getUUID());
                beam(WORN, player, LightType.HEADLAMP, true).tick(null, Headlamps.colour(head));
            }
        }
        dropAllBut(HELD, held);
        dropAllBut(WORN, worn);
    }

    /** The player's beam in {@code beams}, made anew when there is none or it was made for an earlier player object. */
    private static CarriedBeam beam(Map<UUID, CarriedBeam> beams, AbstractClientPlayer player, LightType type,
            boolean onHead) {
        CarriedBeam beam = beams.get(player.getUUID());
        if (beam == null || beam.player() != player) {
            if (beam != null) {
                drop(beam);
            }
            beam = new CarriedBeam(player, type, onHead);
            beams.put(player.getUUID(), beam);
            ClientBeams.add(beam);
        }
        return beam;
    }

    private static void dropAllBut(Map<UUID, CarriedBeam> beams, Set<UUID> lit) {
        beams.entrySet().removeIf(entry -> {
            if (lit.contains(entry.getKey())) {
                return false;
            }
            drop(entry.getValue());
            return true;
        });
    }

    private static @Nullable InteractionHand litHand(AbstractClientPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (FlashlightItem.isOn(stack)) {
                return hand;
            }
        }
        return null;
    }

    private static void drop(CarriedBeam beam) {
        beam.markGone();
        ClientBeams.remove(beam);
    }

    private static void clear() {
        HELD.values().forEach(CarriedLamps::drop);
        WORN.values().forEach(CarriedLamps::drop);
        HELD.clear();
        WORN.clear();
    }

    @SubscribeEvent
    public static void onLeaveWorld(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
