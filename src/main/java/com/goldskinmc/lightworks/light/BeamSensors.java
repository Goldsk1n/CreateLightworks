package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.beam.BeamSensing;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.content.sensor.BeamSensorBlock;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.host.SableHost;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Beam Sensors loaded in each world, on the server, where redstone is worked out. Every light that shines asks first
 * whether any sensor is within its reach ({@link #anyWithin}: none loaded, and it costs nothing more), and if so tells
 * them where its beam is ({@link #shine}); each sensor keeps the strongest signal of its colour this tick. After the
 * world's tick every sensor shows what it got, held {@value #HOLD_TICKS} ticks so a beam sweeping past gives a clean
 * pulse ({@link BeamSensorBlock#POWER}). Sensors on a Sable vehicle are found where the vehicle shows them.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID)
public final class BeamSensors {
    /** A sensor keeps its signal this many ticks after the last beam left it. */
    public static final int HOLD_TICKS = 2;
    /**
     * Where the lens counts as being, out from the block's middle towards its facing, in blocks: the middle of its dome,
     * 4.5 pixels above its foot.
     */
    private static final double LENS_OUT = -0.22;
    /** Something this much nearer than the lens along the way to it blocks the beam (the sensor stops the ray itself). */
    private static final double BLOCKED_BEFORE = 0.6;

    private static final class Sensor {
        final BlockPos pos;
        Direction facing;
        @Nullable
        DyeColor filter;
        /** The lens's middle and face in the world, worked out once a tick. */
        Vec3 lens;
        Vec3 face;
        long placedAt = Long.MIN_VALUE;
        int best;
        int held;
        long litAt = Long.MIN_VALUE;

        Sensor(BlockPos pos, Direction facing, @Nullable DyeColor filter) {
            this.pos = pos;
            this.facing = facing;
            this.filter = filter;
        }
    }

    private static final Map<Level, Map<BlockPos, Sensor>> SENSORS = new WeakHashMap<>();

    private BeamSensors() {
    }

    /** A sensor loaded or changed (server); its facing and dye filter. */
    public static void put(Level level, BlockPos pos, Direction facing, @Nullable DyeColor filter) {
        if (level.isClientSide) {
            return;
        }
        Sensor sensor = SENSORS.computeIfAbsent(level, l -> new HashMap<>())
                .computeIfAbsent(pos.immutable(), p -> new Sensor(p, facing, filter));
        sensor.facing = facing;
        sensor.filter = filter;
        sensor.placedAt = Long.MIN_VALUE;
    }

    /** A sensor removed or unloaded (server). */
    public static void remove(Level level, BlockPos pos) {
        Map<BlockPos, Sensor> sensors = SENSORS.get(level);
        if (sensors != null) {
            sensors.remove(pos);
        }
    }

    /** How many sensors are loaded in a world (for tests). */
    public static int count(Level level) {
        Map<BlockPos, Sensor> sensors = SENSORS.get(level);
        return sensors == null ? 0 : sensors.size();
    }

    /** True when a sensor's lens lies within {@code range} of {@code origin} (server). */
    public static boolean anyWithin(Level level, Vec3 origin, double range) {
        Map<BlockPos, Sensor> sensors = SENSORS.get(level);
        if (sensors == null || sensors.isEmpty()) {
            return false;
        }
        double reach = (range + 1) * (range + 1);
        for (Sensor sensor : sensors.values()) {
            place(level, sensor);
            if (sensor.lens.distanceToSqr(origin) <= reach) {
                return true;
            }
        }
        return false;
    }

    /** Where each light pointed the tick before, to sweep the arc between (server thread only). */
    private record Last(Vec3 direction, long tick) {
    }

    private static final Map<Object, Last> LAST = new WeakHashMap<>();
    /** A sweep is checked in at most this many directions a tick. */
    private static final int MAX_SWEEP_STEPS = 12;

    /**
     * A beam shining this tick (server): every sensor in its reach whose lens it lights, turned towards it, with nothing
     * between, of the sensor's colour, keeps its signal if it is the strongest so far. A light that turned since the last
     * tick is checked along the whole arc it swept, in steps no wider than its beam: a beacon turning fast swept past a
     * sensor between two ticks and was never seen (at 0.3 degrees a tick per RPM, 38 degrees a tick at 128 RPM).
     *
     * @param source     the light, the same object every tick (its block entity, its moving beam, the player carrying it),
     *                   so the sweep since its last tick is known; null to check only where it points now
     * @param brightness the light's brightness, 0 to 15
     * @param colour     the light's dye, or null for an undyed (white) light
     */
    public static void shine(Level level, @Nullable Object source, BeamPose beam, ConeFalloff shape, double lensHalf,
            int brightness, @Nullable DyeColor colour) {
        Vec3 axis = beam.direction().normalize();
        Vec3 from = null;
        if (source != null) {
            long now = level.getGameTime();
            Last last = LAST.get(source);
            if (last != null && last.tick() == now - 1) {
                from = last.direction();
            }
            LAST.put(source, new Last(axis, now));
        }
        int steps = 1;
        if (from != null) {
            double angle = Math.acos(Math.max(-1, Math.min(1, from.dot(axis))));
            steps = (int) Math.max(1, Math.min(MAX_SWEEP_STEPS, Math.ceil(angle / BeamTrace.visibleHalfAngle(shape))));
        }
        for (int k = 0; k < steps; k++) {
            // k = 0 is where it points now, then back along the arc towards where it pointed a tick ago
            double back = k / (double) steps;
            Vec3 direction = k == 0 ? axis : axis.scale(1 - back).add(from.scale(back)).normalize();
            shineAlong(level, beam.origin(), direction, beam.sideHint(), shape, lensHalf, brightness, colour);
        }
    }

    private static void shineAlong(Level level, Vec3 origin, Vec3 axis, double @Nullable [] sideHint, ConeFalloff shape,
            double lensHalf, int brightness, @Nullable DyeColor colour) {
        Map<BlockPos, Sensor> sensors = SENSORS.get(level);
        if (sensors == null || sensors.isEmpty() || brightness <= 0) {
            return;
        }
        double range = BeamTrace.range(shape);
        for (Sensor sensor : sensors.values()) {
            // Undyed, any beam; dyed, only beams of its dye (an undyed beam is white, no dye's colour)
            if (sensor.filter != null && sensor.filter != colour) {
                continue;
            }
            place(level, sensor);
            double light = BeamSensing.light(sensor.lens.x, sensor.lens.y, sensor.lens.z, sensor.face.x, sensor.face.y,
                    sensor.face.z, origin.x, origin.y, origin.z, axis.x, axis.y, axis.z, sideHint, shape, lensHalf, range);
            int signal = BeamSensing.signal(light, brightness);
            if (signal <= sensor.best) {
                continue;
            }
            Vec3 to = sensor.lens.subtract(origin);
            double distance = to.length();
            BeamTrace.Hit hit = BeamTrace.trace(level, origin, to, distance + BLOCKED_BEFORE);
            if (hit.hitBlock() && hit.distance() < distance - BLOCKED_BEFORE) {
                continue;
            }
            sensor.best = signal;
        }
    }

    /** Works out where a sensor's lens is in the world this tick. */
    private static void place(Level level, Sensor sensor) {
        long now = level.getGameTime();
        if (sensor.placedAt == now && sensor.lens != null) {
            return;
        }
        Vec3 out = Vec3.atLowerCornerOf(sensor.facing.getNormal());
        BeamPose pose = SableHost.toWorld(level, sensor.pos, Vec3.atCenterOf(sensor.pos).add(out.scale(LENS_OUT)), out);
        sensor.lens = pose.origin();
        sensor.face = pose.direction().normalize();
        sensor.placedAt = now;
    }

    /** After the world's tick: every sensor shows the strongest beam it got, or keeps it a moment after. */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, Sensor> sensors = SENSORS.get(level);
        if (sensors == null || sensors.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        // A copy: a sensor's signal changing updates its neighbours, which may break or load sensors
        for (Sensor sensor : new java.util.ArrayList<>(sensors.values())) {
            if (sensor.best > 0) {
                sensor.held = sensor.best;
                sensor.litAt = now;
            } else if (now - sensor.litAt > HOLD_TICKS) {
                sensor.held = 0;
            }
            sensor.best = 0;
            if (!level.isLoaded(sensor.pos)) {
                continue;
            }
            BlockState state = level.getBlockState(sensor.pos);
            if (state.getBlock() instanceof BeamSensorBlock block) {
                block.setPower(level, sensor.pos, state, sensor.held);
            }
        }
    }
}
