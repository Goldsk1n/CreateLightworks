package com.goldskinmc.lightworks.debug.selftest;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.compat.DynamicLights;
import com.goldskinmc.lightworks.debug.Timer;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.light.BeamSource;
import com.goldskinmc.lightworks.light.BeamTrace;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
    /** Multiplayer: how often to log what the client sees, since no script runs in this process. */
    private static final int MULTIPLAYER_LOG_INTERVAL = 200;
    private static boolean wasInWorld;

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
    @Nullable
    private static FrameStats frameStats;
    private static boolean beamsOff;
    private static int savedFramerateLimit;
    private static boolean savedVsync;

    private SelfTestClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!SelfTest.ENABLED) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            // On a dedicated server the script ends by stopping the server; leave once disconnected
            if (wasInWorld && QUIT_WHEN_DONE) {
                Lightworks.LOGGER.info("[selftest] client: disconnected, quitting");
                mc.stop();
            }
            return;
        }
        wasInWorld = true;
        if (!mc.isLocalServer() && ++ticksInWorld % MULTIPLAYER_LOG_INTERVAL == 0) {
            long moving = ClientBeams.snapshot().stream().filter(b -> b.tickBeam().moving()).count();
            long drawn = ClientBeams.snapshot().stream().filter(b -> b.beam().length() > 0).count();
            Lightworks.LOGGER.info("[selftest] client on {}: beams {} ({} moving, {} drawn), {}",
                    mc.getCurrentServer() == null ? "?" : mc.getCurrentServer().ip, ClientBeams.count(), moving, drawn,
                    DynamicLights.status());
            return;
        }
        if (!mc.isLocalServer()) {
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
                // Uncapped, so frame times show the work done rather than the frame limit
                savedFramerateLimit = mc.options.framerateLimit().get();
                savedVsync = mc.options.enableVsync().get();
                mc.options.framerateLimit().set(260);
                mc.options.enableVsync().set(false);
            } else {
                mc.options.hideGui = savedHideGui;
                mc.options.framerateLimit().set(savedFramerateLimit);
                mc.options.enableVsync().set(savedVsync);
            }
            wasActive = active;
        }
        if (SelfTestBridge.beamsOff != beamsOff) {
            beamsOff = SelfTestBridge.beamsOff;
            ClientBeams.setEnabled(!beamsOff);
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
        recordFrameTime();
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

    private static void recordFrameTime() {
        String name = SelfTestBridge.frameStats;
        if (frameStats != null && !frameStats.name.equals(name)) {
            frameStats.report();
            frameStats = null;
        }
        if (name != null) {
            if (frameStats == null) {
                frameStats = new FrameStats(name);
            }
            frameStats.frame();
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
            probe = new SpotProbe(name, SelfTestBridge.probeGroundY, SelfTestBridge.probeAxis);
        }
        probe.ticks++;
        for (BeamSource light : ClientBeams.snapshot()) {
            BeamPose beam = light.tickBeam();
            if (beam.moving()) {
                probe.tick(light, beam);
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

    /** Frame times over one load phase, and the cost of our client code and of LambDynamicLights calling us. */
    private static final class FrameStats {
        final String name;
        final long[] timerStart = new long[Timer.values().length];
        final long[] callsStart = new long[Timer.values().length];
        final long startNanos = System.nanoTime();
        long[] frames = new long[1024];
        int count;
        long lastFrame;

        FrameStats(String name) {
            this.name = name;
            for (Timer timer : Timer.values()) {
                timerStart[timer.ordinal()] = timer.nanos();
                callsStart[timer.ordinal()] = timer.calls();
            }
        }

        void frame() {
            long now = System.nanoTime();
            if (lastFrame != 0) {
                if (count == frames.length) {
                    frames = Arrays.copyOf(frames, count * 2);
                }
                frames[count++] = now - lastFrame;
            }
            lastFrame = now;
        }

        void report() {
            if (count == 0) {
                return;
            }
            long[] sorted = Arrays.copyOf(frames, count);
            Arrays.sort(sorted);
            double mean = Arrays.stream(sorted).average().orElse(0) / 1e6;
            double seconds = (System.nanoTime() - startNanos) / 1e9;
            StringBuilder work = new StringBuilder();
            for (Timer timer : Timer.values()) {
                if (timer == Timer.SERVER_BLOCK) {
                    continue;
                }
                long calls = timer.calls() - callsStart[timer.ordinal()];
                double ms = (timer.nanos() - timerStart[timer.ordinal()]) / 1e6;
                work.append(String.format(", %s %d calls %.1f ms/s", timer.name().toLowerCase(), calls, ms / seconds));
            }
            Lightworks.LOGGER.info(String.format("[selftest] frames %s: %d frames, mean %.2f ms (%.0f FPS), "
                            + "median %.2f, 95th %.2f, 99th %.2f ms%s",
                    name, count, mean, 1000 / mean, sorted[count / 2] / 1e6, sorted[(int) (count * 0.95)] / 1e6,
                    sorted[(int) (count * 0.99)] / 1e6, work));
        }
    }

    /**
     * Measures moving lights. A light pointing straight down should have its spot directly below it,
     * half a block above the ground: per tick with the logical pose, per frame with the render pose (see
     * onRenderLevel). Every light is also watched for front/rear swaps: the ticks between the light
     * reversing along its beam and switching on or off.
     */
    private static final class SpotProbe {
        /** Blocks per tick along the beam that count as moving, like the headlight's own threshold. */
        private static final double MIN_MOTION = 0.005;

        final String name;
        final double groundY;
        @Nullable
        final Vec3 axis;
        final Map<BeamSource, Swaps> swaps = new IdentityHashMap<>();
        final Map<BeamSource, Circle> circles = new IdentityHashMap<>();
        int ticks;
        int samples;
        int noSpot;
        double maxHorizontalGap;
        double maxGroundError;
        double firstSpotX = Double.NaN;
        double lastSpotX = Double.NaN;
        int frames;
        double maxFrameGap;
        double sumFrameGap;

        SpotProbe(String name, double groundY, @Nullable Vec3 axis) {
            this.name = name;
            this.groundY = groundY;
            this.axis = axis;
        }

        void tick(BeamSource light, BeamPose beam) {
            swaps.computeIfAbsent(light, l -> new Swaps()).tick(ticks, beam, light.beam().length() > 0);
            if (axis != null && light.lastSpot() != null) {
                circles.computeIfAbsent(light, l -> new Circle()).add(light.lastSpot(), axis);
            }
            if (beam.direction().y > -0.99) {
                return;
            }
            BeamSpot spot = light.lastSpot();
            samples++;
            if (spot == null) {
                noSpot++;
                return;
            }
            maxHorizontalGap = Math.max(maxHorizontalGap, Math.hypot(spot.spotX() - beam.origin().x,
                    spot.spotZ() - beam.origin().z));
            double groundError = Math.abs(spot.spotY() - (groundY + 0.5));
            if (groundError > 0.5 && maxGroundError <= 0.5) {
                Lightworks.LOGGER.info(String.format("[selftest] probe %s tick %d: spot at %.2f %.2f %.2f, light at %.2f %.2f %.2f",
                        name, ticks, spot.spotX(), spot.spotY(), spot.spotZ(), beam.origin().x, beam.origin().y,
                        beam.origin().z));
            }
            maxGroundError = Math.max(maxGroundError, groundError);
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
                    "[selftest] probe %s: %d ticks, %d down-facing samples, %d without a spot, spot moved %.2f blocks east, "
                            + "max horizontal gap to the light %.3f, max height error vs ground %.3f; "
                            + "%d frames, spot behind the drawn beam by %.3f on average, %.3f at most; %s",
                    name, ticks, samples, noSpot, lastSpotX - firstSpotX, maxHorizontalGap, maxGroundError,
                    frames, frames == 0 ? 0 : sumFrameGap / frames, maxFrameGap, DynamicLights.status()));
            int c = 0;
            for (Circle circle : circles.values()) {
                Lightworks.LOGGER.info(String.format("[selftest] probe %s light %d circle: %d samples, spot %.2f to "
                                + "%.2f blocks from the axis, went %.0f degrees round (%.2f a tick)", name, ++c,
                        circle.samples, circle.minRadius, circle.maxRadius, circle.swept, circle.swept / ticks));
            }
            int n = 0;
            for (Swaps s : swaps.values()) {
                Lightworks.LOGGER.info("[selftest] probe {} light {}: {} reversals, {} switches, ticks from reversal to "
                        + "switch {}, lit {} of {} ticks", name, ++n, s.reversals, s.switches, s.delays, s.litTicks, s.seen);
            }
        }

        /** How far one light's spot is from the probe axis, and the angle it has gone round it in total. */
        private static final class Circle {
            int samples;
            double minRadius = Double.MAX_VALUE;
            double maxRadius;
            double swept;
            double lastAngle = Double.NaN;

            void add(BeamSpot spot, Vec3 axis) {
                double dx = spot.spotX() - axis.x;
                double dz = spot.spotZ() - axis.z;
                double radius = Math.hypot(dx, dz);
                samples++;
                minRadius = Math.min(minRadius, radius);
                maxRadius = Math.max(maxRadius, radius);
                double angle = Math.toDegrees(Math.atan2(dz, dx));
                if (!Double.isNaN(lastAngle)) {
                    swept += Math.abs(Mth.wrapDegrees(angle - lastAngle));
                }
                lastAngle = angle;
            }
        }

        /** One light's front/rear behaviour. */
        private static final class Swaps {
            @Nullable
            Vec3 lastOrigin;
            int lastSign;
            int reversedAt = -1;
            boolean lit;
            int seen;
            int litTicks;
            int reversals;
            int switches;
            final List<Integer> delays = new ArrayList<>();

            void tick(int tick, BeamPose beam, boolean litNow) {
                seen++;
                if (litNow) {
                    litTicks++;
                }
                if (lastOrigin != null) {
                    double along = beam.origin().subtract(lastOrigin).dot(beam.direction());
                    int sign = Math.abs(along) > MIN_MOTION ? (int) Math.signum(along) : 0;
                    if (sign != 0) {
                        if (lastSign != 0 && sign != lastSign) {
                            reversals++;
                            reversedAt = tick;
                        }
                        lastSign = sign;
                    }
                    if (litNow != lit) {
                        switches++;
                        if (reversedAt >= 0) {
                            delays.add(tick - reversedAt);
                            reversedAt = -1;
                        }
                    }
                }
                lit = litNow;
                lastOrigin = beam.origin();
            }
        }
    }
}
