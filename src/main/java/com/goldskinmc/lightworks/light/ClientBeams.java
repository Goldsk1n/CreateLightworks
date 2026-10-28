package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Every {@link BeamSource} in the client world. Some lights vanish without telling anyone (a contraption
 * entity that unloads or is discarded), so each client tick releases the dynamic light of any source
 * that reports itself gone, and leaving a world releases them all.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class ClientBeams {
    private static final Set<BeamSource> SOURCES = new LinkedHashSet<>();
    private static boolean enabled = true;

    private ClientBeams() {
    }

    public static void add(BeamSource source) {
        SOURCES.add(source);
    }

    public static void remove(BeamSource source) {
        if (SOURCES.remove(source)) {
            source.beam().release();
        }
    }

    /** A copy, safe to iterate while lights come and go. */
    public static List<BeamSource> snapshot() {
        return new ArrayList<>(SOURCES);
    }

    public static int count() {
        return SOURCES.size();
    }

    /** Debug switch for load comparisons: while off, every beam stays dark and draws nothing. */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean enabled() {
        return enabled;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        SOURCES.removeIf(source -> {
            if (source.isGone()) {
                source.beam().release();
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onLeaveWorld(ClientPlayerNetworkEvent.LoggingOut event) {
        SOURCES.forEach(source -> source.beam().release());
        SOURCES.clear();
    }
}
