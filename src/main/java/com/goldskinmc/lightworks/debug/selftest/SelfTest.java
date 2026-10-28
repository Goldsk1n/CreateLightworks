package com.goldskinmc.lightworks.debug.selftest;

import com.goldskinmc.lightworks.Lightworks;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dev-only checks that run in a real game without anyone at the keyboard, enabled with
 * {@code -Pselftest=true} (the default scenarios) or {@code -Pselftest=fixed,vehicle,...}. When the first
 * player joins, the scenarios in {@link Scenarios} run one after another at night; {@link SelfTestClient}
 * aims the camera, takes screenshots and measures the lit spots. Every change is undone afterwards and
 * the log ends with {@code [selftest] done}.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID)
public final class SelfTest {
    private static final String SELECTION = System.getProperty("lightworks.selftest", "false");
    public static final boolean ENABLED = !SELECTION.isEmpty() && !SELECTION.equals("false");

    /** How long to wait for the client to be ready: in singleplayer it shares this JVM. */
    private static final int CLIENT_READY_TIMEOUT_TICKS = 1200;

    private static final Deque<Script> queue = new ArrayDeque<>();
    private static int waitedForClient;
    private static Stage stage;
    private static boolean started;

    private SelfTest() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || started || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        started = true;
        String selection = SELECTION.equals("true") ? Scenarios.DEFAULT : SELECTION;
        for (String name : selection.split(",")) {
            Supplier<Script> scenario = Scenarios.ALL.get(name.trim());
            if (scenario == null) {
                Lightworks.LOGGER.warn("[selftest] unknown scenario '{}'; known: {}", name, Scenarios.ALL.keySet());
            } else {
                queue.add(scenario.get());
            }
        }
        stage = new Stage(player);
        SelfTestBridge.active = true;
        stage.log("running %s from %s", selection, stage.origin);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (stage == null) {
            return;
        }
        if (!SelfTestBridge.clientReady && !stage.level.getServer().isDedicatedServer()
                && ++waitedForClient < CLIENT_READY_TIMEOUT_TICKS) {
            return;
        }
        Script current = queue.peek();
        if (current == null) {
            stage.restoreAll();
            SelfTestBridge.reset();
            SelfTestBridge.finished = true;
            stage.log("done");
            MinecraftServer server = stage.level.getServer();
            stage = null;
            if (server.isDedicatedServer() && Boolean.getBoolean("lightworks.selftest.quit")) {
                server.halt(false);
            }
            return;
        }
        if (current.tick(stage)) {
            stage.log("%s: finished", current.name);
            queue.poll();
        }
    }
}
