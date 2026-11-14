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
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps a {@link CarriedBeam} for every player in the client world holding a lit lamp, the main hand's first. The game
 * already sends held items to everyone nearby, so other players' lamps need nothing from the server.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class CarriedLamps {
    private static final Map<UUID, CarriedBeam> BEAMS = new HashMap<>();

    private CarriedLamps() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        Set<UUID> lit = new HashSet<>();
        for (AbstractClientPlayer player : mc.level.players()) {
            InteractionHand hand = litHand(player);
            if (hand == null || player.isSpectator()) {
                continue;
            }
            lit.add(player.getUUID());
            CarriedBeam beam = BEAMS.get(player.getUUID());
            if (beam == null || beam.player() != player) {
                if (beam != null) {
                    drop(beam);
                }
                beam = new CarriedBeam(player, LightType.FLASHLIGHT);
                BEAMS.put(player.getUUID(), beam);
                ClientBeams.add(beam);
            }
            beam.tick(hand, FlashlightItem.colour(player.getItemInHand(hand)));
        }
        BEAMS.entrySet().removeIf(entry -> {
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
        BEAMS.values().forEach(CarriedLamps::drop);
        BEAMS.clear();
    }

    @SubscribeEvent
    public static void onLeaveWorld(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
