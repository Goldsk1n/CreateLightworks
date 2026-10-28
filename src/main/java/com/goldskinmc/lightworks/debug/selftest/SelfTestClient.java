package com.goldskinmc.lightworks.debug.selftest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.compat.DynamicLights;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.light.BeamSource;
import com.goldskinmc.lightworks.light.BeamTrace;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Client half of {@link SelfTest}: aims the camera, takes the screenshots and clip frames the scripts
 * ask for (saved under {@code screenshots/selftest/<time>/}), and measures moving lights' spots.
 * A shot logs the mean brightness of the centre of the screen, where the camera is aimed, so lit and
 * dark shots can be compared from the log alone.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class SelfTestClient {
    /** Client ticks between a shot request and the capture, so chunk sections can rebuild first. */
    private static final int SHOT_SETTLE_TICKS = 20;
    private static final int LOG_STATUS_AT = 120;
    /** Client ticks in the world before the scripts may start; the render thread runs no ticks while shaders compile. */
    private static final int READY_AFTER_TICKS = 100;
    private static final boolean QUIT_WHEN_DONE = Boolean.getBoolean("lightworks.selftest.quit");
    /** Ticks between the end of the test and quitting, so the last screenshots finish saving. */
    private static final int QUIT_DELAY_TICKS = 60;
    private static int ticksSinceFinished;

    private static int ticksInWorld;
    private static boolean wasActive;
    private static boolean savedHideGui;
    private static boolean suppressing;
    @Nullable
    private static Path shotDir;
    private static int shotHandled;
    private static int shotWaited;
    @Nullable
    private static String recordingName;
    private static int recordedFrames;
    @Nullable
    private static SpotProbe probe;

    private SelfTestClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!SelfTest.ENABLED || mc.level == null || mc.player == null) {
            return;
        }
        if (++ticksInWorld == READY_AFTER_TICKS) {
            SelfTestBridge.clientReady = true;
        }
        if (ticksInWorld == LOG_STATUS_AT) {
            Lightworks.LOGGER.info("[selftest] client: tracked beams {}, {}", ClientBeams.count(),
                    DynamicLights.status());
        }
        boolean active = SelfTestBridge.active;
        if (active != wasActive) {
            if (active) {
                savedHideGui = mc.options.hideGui;
                mc.options.hideGui = true;
                // A singleplayer game pauses when its window loses focus, which would stop the test
                mc.options.pauseOnLostFocus = false;
            } else {
                mc.options.hideGui = savedHideGui;
            }
            wasActive = active;
        }
        if (SelfTestBridge.dynamicLightsOff != suppressing) {
            suppressing = SelfTestBridge.dynamicLightsOff;
            DynamicLights.setSuppressed(suppressing);
        }
        takeRequestedShot(mc);
        recordFrame(mc);
        probeMovingSpots(mc.player);
        if (QUIT_WHEN_DONE && SelfTestBridge.finished && ++ticksSinceFinished == QUIT_DELAY_TICKS) {
            Lightworks.LOGGER.info("[selftest] quitting");
            mc.stop();
        }
    }

    /** Keeps the camera on the focus point every frame, so moving the player never spoils a shot. */
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        Vec3 focus = SelfTestBridge.focus;
        LocalPlayer player = Minecraft.getInstance().player;
        if (!SelfTest.ENABLED || focus == null || player == null) {
            return;
        }
        Vec3 d = focus.subtract(player.getEyePosition(event.getPartialTick().getGameTimeDeltaPartialTick(true)));
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
    }

    /** Per frame: how far the lit spot is from where the beam lands in this frame's render pose. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        SpotProbe current = probe;
        Minecraft mc = Minecraft.getInstance();
        if (current == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (BeamSource light : ClientBeams.snapshot()) {
            BeamSpot spot = light.lastSpot();
            BeamPose beam = light.renderBeam(partialTick);
            if (spot == null || !beam.moving()) {
                continue;
            }
            BeamTrace.Hit hit = BeamTrace.trace(mc.level, beam.origin(), beam.direction(), light.beam().shape().range());
            BeamSpot drawn = BeamTrace.spotFor(hit, beam.direction(), spot.peak());
            if (drawn != null) {
                current.frame(Math.sqrt(sq(drawn.spotX() - spot.spotX()) + sq(drawn.spotY() - spot.spotY())
                        + sq(drawn.spotZ() - spot.spotZ())));
            }
        }
    }

    private static void probeMovingSpots(LocalPlayer player) {
        String name = SelfTestBridge.probe;
        if (probe != null && !probe.name.equals(name)) {
            probe.report();
            probe = null;
        }
        if (name == null) {
            return;
        }
        if (probe == null) {
            probe = new SpotProbe(name, SelfTestBridge.probeGroundY);
        }
        for (BeamSource light : ClientBeams.snapshot()) {
            BeamPose beam = light.tickBeam();
            if (beam.moving()) {
                probe.tick(beam, light.lastSpot());
            }
        }
    }

    private static void takeRequestedShot(Minecraft mc) {
        int requested = SelfTestBridge.shotRequested;
        if (requested <= shotHandled) {
            return;
        }
        if (++shotWaited < SHOT_SETTLE_TICKS) {
            return;
        }
        String name = SelfTestBridge.shotName;
        NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        Lightworks.LOGGER.info(String.format("[selftest] shot %s: centre brightness %.1f, whole frame %.1f (0-255)",
                name, meanLuma(image, 6), meanLuma(image, 1)));
        save(image, name);
        shotHandled = requested;
        shotWaited = 0;
        SelfTestBridge.shotDone = requested;
    }

    private static void recordFrame(Minecraft mc) {
        String name = SelfTestBridge.recording;
        if (name == null) {
            if (recordingName != null) {
                Lightworks.LOGGER.info("[selftest] clip {}: {} frames at 20 per second", recordingName, recordedFrames);
                recordingName = null;
            }
            return;
        }
        if (!name.equals(recordingName)) {
            recordingName = name;
            recordedFrames = 0;
        }
        save(Screenshot.takeScreenshot(mc.getMainRenderTarget()), String.format("%s/%04d", name, recordedFrames++));
    }

    private static void save(NativeImage image, String name) {
        if (shotDir == null) {
            shotDir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("selftest")
                    .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            Lightworks.LOGGER.info("[selftest] screenshots go to {}", shotDir);
        }
        Path file = shotDir.resolve(name + ".png");
        Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                Lightworks.LOGGER.warn("[selftest] could not save {}", file, e);
            }
        });
    }

    /** Mean brightness (Rec. 709 luma, 0-255) of the centre 1/{@code fraction} of the image in each axis. */
    private static double meanLuma(NativeImage image, int fraction) {
        int w = image.getWidth() / fraction;
        int h = image.getHeight() / fraction;
        int x0 = (image.getWidth() - w) / 2;
        int y0 = (image.getHeight() - h) / 2;
        double sum = 0;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                int abgr = image.getPixelRGBA(x, y);
                sum += 0.2126 * (abgr & 0xFF) + 0.7152 * ((abgr >> 8) & 0xFF) + 0.0722 * ((abgr >> 16) & 0xFF);
            }
        }
        return sum / ((double) w * h);
    }

    private static double sq(double v) {
        return v * v;
    }

    /**
     * Measures a moving light pointing straight down: its spot belongs directly below it, half a block
     * above the ground. Per tick that is the logical pose; per frame, the render pose (see onRenderLevel).
     */
    private static final class SpotProbe {
        final String name;
        final double groundY;
        int samples;
        int noSpot;
        double maxHorizontalGap;
        double maxGroundError;
        double firstSpotX = Double.NaN;
        double lastSpotX = Double.NaN;
        int frames;
        double maxFrameGap;
        double sumFrameGap;

        SpotProbe(String name, double groundY) {
            this.name = name;
            this.groundY = groundY;
        }

        void tick(BeamPose beam, @Nullable BeamSpot spot) {
            samples++;
            if (spot == null) {
                noSpot++;
                return;
            }
            maxHorizontalGap = Math.max(maxHorizontalGap, Math.hypot(spot.spotX() - beam.origin().x,
                    spot.spotZ() - beam.origin().z));
            maxGroundError = Math.max(maxGroundError, Math.abs(spot.spotY() - (groundY + 0.5)));
            if (Double.isNaN(firstSpotX)) {
                firstSpotX = spot.spotX();
            }
            lastSpotX = spot.spotX();
        }

        void frame(double gap) {
            frames++;
            sumFrameGap += gap;
            maxFrameGap = Math.max(maxFrameGap, gap);
        }

        void report() {
            Lightworks.LOGGER.info(String.format(
                    "[selftest] probe %s: %d ticks, %d without a spot, spot moved %.2f blocks east, "
                            + "max horizontal gap to the light %.3f, max height error vs ground %.3f; "
                            + "%d frames, spot behind the drawn beam by %.3f on average, %.3f at most; %s",
                    name, samples, noSpot, lastSpotX - firstSpotX, maxHorizontalGap, maxGroundError,
                    frames, frames == 0 ? 0 : sumFrameGap / frames, maxFrameGap, DynamicLights.status()));
        }
    }
}
