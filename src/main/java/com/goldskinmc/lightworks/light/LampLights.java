package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.compat.DynamicLights;
import com.goldskinmc.lightworks.compat.DynamicSpot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Dynamic light for Warning, Tail and Brake Lights that move, on a Create contraption or a Sable vehicle, where their
 * block light stays behind (they lit nothing round them from 1.0.0 to 1.1). Each lamp offers itself every client tick
 * ({@link #offer}); after the level's tick the {@value #MAX_LIGHTS} nearest the camera get one light each, at the lamp
 * (a lamp next to one already lit shares its light),
 * through whichever dynamic light path there is (LambDynamicLights, Sodium Dynamic Lights or our client light block), as
 * strong as the lamp's placed block light (Warning Light 12, Tail Light 6).
 * Steady: a pattern does not change it, since a strobe would rebuild chunks up to 10 times a second; the lens still
 * shows the pattern, and the coloured glow round the lamp gives it the lamp's colour. Moved only once the lamp has moved
 * {@value #MIN_MOVE} blocks, as a beam's spot is. Client config {@code movingLampLight}. Client thread only.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class LampLights {
    /** How many moving lamps get a light: the nearest to the camera. */
    public static final int MAX_LIGHTS = 16;
    /**
     * A lamp this close (blocks) to one already lit, at most as bright, gets no light of its own: dynamic lights combine
     * by the brightest, so it would add next to nothing, while each light costs LambDynamicLights work for every block
     * round it on every rebuild (50 lamps on one deck, 16 of them lit: 900 ms a second of its threads, lamps-moving).
     */
    private static final double SHARE_DISTANCE = 2.5;
    /** The light moves once its lamp has moved this far, in blocks: each move rebuilds chunk sections. */
    private static final double MIN_MOVE = 0.25;

    private record Offer(Object key, Vec3 at, Vec3 facing, double peak) {
    }

    private static final class Shown {
        final DynamicSpot spot = DynamicLights.createSpot();
        BeamSpot last;
    }

    private static final List<Offer> OFFERS = new ArrayList<>();
    /**
     * The game time at the last update. Lamps offer themselves as the world ticks, and under {@code /tick rate} or
     * {@code /tick freeze} the client ticks without it: those ticks bring no offers and change nothing, where before
     * every light went out on them and came back on the next (the ship clip at tick rate 5, Nov 21).
     */
    private static long lastGameTime = Long.MIN_VALUE;
    private static final Map<Object, Shown> SHOWN = new IdentityHashMap<>();

    private LampLights() {
    }

    /**
     * A moving lamp's offer for this tick. {@code key} stays the same object while the lamp lives (its movement context
     * or block entity), {@code at} is the lamp's centre and {@code facing} the unit vector out of its mount, both in the
     * world; {@code placedLight} is what the lamp gives when placed and {@code brightness} its brightness, 0 to 15.
     */
    public static void offer(Object key, Vec3 at, Vec3 facing, int placedLight, int brightness) {
        // The level it gives placed: LambDynamicLights fades a light twice as fast as block light, so a smaller one (8 was
        // planned) left the ground under a lamp 4 blocks up at level 1, unseen (lamps-moving, Nov 21)
        double peak = placedLight * Math.max(0, Math.min(15, brightness)) / 15.0;
        if (peak >= 1) {
            OFFERS.add(new Offer(key, at, facing, peak));
        }
    }

    /** How many moving lamps have a light now, for the debug overlay and tests. */
    public static int shown() {
        return SHOWN.size();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !LightworksClientConfig.movingLampLight() || !DynamicLights.present()) {
            OFFERS.clear();
            clear();
            return;
        }
        long time = mc.level.getGameTime();
        if (time == lastGameTime) {
            OFFERS.clear();
            return;
        }
        lastGameTime = time;
        if (ClientSafety.works(ClientSafety.DYNAMIC_LIGHT)) {
            try {
                update(mc.gameRenderer.getMainCamera().getPosition());
            } catch (RuntimeException e) {
                ClientSafety.failed(ClientSafety.DYNAMIC_LIGHT, e);
                clear();
            }
        }
        OFFERS.clear();
    }

    private static void update(Vec3 camera) {
        OFFERS.sort(Comparator.comparingDouble(offer -> offer.at().distanceToSqr(camera)));
        Set<Object> lit = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Offer> chosen = new ArrayList<>();
        for (Offer offer : OFFERS) {
            if (chosen.size() >= MAX_LIGHTS) {
                break;
            }
            if (lit.contains(offer.key()) || sharesLight(chosen, offer)) {
                continue;
            }
            lit.add(offer.key());
            chosen.add(offer);
            Shown shown = SHOWN.computeIfAbsent(offer.key(), key -> new Shown());
            BeamSpot spot = BeamSpot.lamp(offer.at().x, offer.at().y, offer.at().z, offer.facing().x, offer.facing().y,
                    offer.facing().z, offer.peak());
            if (shown.last == null || spot.differsFrom(shown.last, MIN_MOVE, 0.5, 0.9)) {
                shown.spot.set(spot);
                shown.last = spot;
            }
        }
        SHOWN.entrySet().removeIf(entry -> {
            if (lit.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().spot.remove();
            return true;
        });
    }

    /** True when a lamp already lit, at least as bright, is near enough to light this one's surroundings for it. */
    private static boolean sharesLight(List<Offer> chosen, Offer offer) {
        for (Offer other : chosen) {
            if (other.peak() >= offer.peak() && other.at().distanceToSqr(offer.at()) < SHARE_DISTANCE * SHARE_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private static void clear() {
        lastGameTime = Long.MIN_VALUE;
        SHOWN.values().forEach(shown -> shown.spot.remove());
        SHOWN.clear();
    }

    @SubscribeEvent
    public static void onLeaveWorld(ClientPlayerNetworkEvent.LoggingOut event) {
        OFFERS.clear();
        clear();
    }
}
