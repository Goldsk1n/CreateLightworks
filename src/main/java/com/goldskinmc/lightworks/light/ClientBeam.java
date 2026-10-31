package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.beam.BeamFrame;
import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.compat.DynamicLights;
import com.goldskinmc.lightworks.compat.DynamicSpot;
import com.goldskinmc.lightworks.host.BeamPose;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The client half of one beam: traces it, moves its dynamic spot to where it lands, and remembers how far
 * it reaches for the cosmetic beam. The visible beam is a square truncated pyramid ({@link BeamFrame}), so
 * besides its centre a beam traces the rays along its corners and edge middles: a beam grazing a wall
 * stops on it where it touches it and goes on beside it. Client thread only.
 */
public final class ClientBeam {
    /**
     * Each spot change makes LambDynamicLights rebuild every chunk section the spot touches, so a spot is
     * only moved once it is this far (in blocks) from where it was last published.
     */
    private static final double MIN_SPOT_MOVE = 0.25;
    private static final double MIN_PEAK_CHANGE = 0.5;
    /** About 1 degree. */
    private static final double MIN_DIRECTION_DOT = 0.99985;
    /** Longest beam outside the real world (Ponder scenes), so beams stay inside the scene. */
    private static final double PREVIEW_RANGE = 8;
    /** Moving lights further from the camera than this trace only their centre (corners would not show). */
    private static final double CORNER_TRACE_DISTANCE = 32;
    /**
     * A moving light whose centre ray lands traces its corner rays only every this many ticks (lights take
     * turns); in between each corner keeps its last length relative to the centre, which holds while the beam
     * sweeps one surface.
     */
    private static final int MOVING_CORNER_INTERVAL = 8;
    private static final int CORNERS = BeamFrame.GRID * BeamFrame.GRID;
    private static final int CENTRE = BeamFrame.corner(0, 0);
    /** Blocks of tinted glow round the spot per level of light there: block light fades one level a block. */
    private static final double GLOW_PER_LEVEL = 0.5;
    /** An undyed beam's spot, warm white like its lens. */
    private static final int WHITE_SPOT = 0xFFF4DC;
    /**
     * Time all moving lights together may spend finding their spots' faces each tick. Over it they take turns,
     * each refreshing less often, so a scene full of them keeps its frame rate and only their spots step. Past
     * {@link #MAX_SPOT_INTERVAL} ticks a spot would no longer follow its beam, so they go without until fewer want one.
     */
    private static final long SPOT_BUDGET_NANOS = 1_500_000;
    private static final int MAX_SPOT_INTERVAL = 10;
    private static long spotTick;
    private static int spotsWanted;
    private static int spotInterval = 1;
    private static double spotAverageNanos;

    private final ConeFalloff shape;
    /** Half the lens's width, in blocks: the square the beam starts as, flush on the glass. */
    private final float lensHalf;
    @Nullable
    private DynamicSpot spot;
    @Nullable
    private ProjectedTint tint;
    private int tintedRgb = -1;
    private int tintedBrightness;
    @Nullable
    private ProjectedTint spotLayer;
    private int spotRgb = -1;
    private int spotBrightness;
    /** Moved since its spot's faces were last found, waiting for its turn. */
    private boolean spotStale;
    @Nullable
    private BeamSpot lastSpot;
    private double length;
    /** How far each corner ray reached, by {@link BeamFrame#corner} index (the centre is {@link #length}). */
    private final float[] cornerLengths = new float[CORNERS];
    /** Corner length over centre length at the last corner trace. */
    private final float[] cornerRatios = new float[CORNERS];
    private int ticksToCorners;
    /** Nearest place a corner or edge ray landed at the last corner trace, for when the centre ray misses. */
    @Nullable
    private BeamTrace.Landing landing;
    private int brightness;
    private float skyExposure;

    public ClientBeam(ConeFalloff shape, float lensHalf) {
        this.shape = shape;
        this.lensHalf = lensHalf;
    }

    public float lensHalf() {
        return lensHalf;
    }

    public ConeFalloff shape() {
        return shape;
    }

    /**
     * @param beam  the beam as of this tick
     * @param drawn for a moving light, where its host will draw it halfway to the next tick. The dynamic
     *              light changes 20 times a second while the beam is drawn every frame, so the spot is
     *              placed where the beam will be on average until the next update. Hosts interpolate
     *              differently (Sable vehicles lag a tick behind, bearings extrapolate ahead), so this
     *              comes from the host's own render pose.
     * @param brightness 0 (off) to 15; scales the spot and the cosmetic beam
     * @param tintRgb    a dyed light's colour, 0xRRGGBB, painted onto what the beam lights
     *                   ({@link ProjectedTint}); -1 for an undyed light, whose light stays white
     */
    public void update(Level level, BeamPose beam, BeamPose drawn, int brightness, int tintRgb) {
        if (!ClientBeams.enabled() || brightness <= 0) {
            off();
            return;
        }
        if (spot == null) {
            // Ponder scenes and contraption render worlds draw beams too, but only the real world gets light
            spot = ClientBeams.isRealWorld(level) ? DynamicLights.createSpot() : DynamicSpot.NONE;
        }
        BeamPose at = beam.moving() ? drawn : beam;
        double range = BeamTrace.range(shape);
        BeamTrace.Hit hit = BeamTrace.trace(level, at.origin(), at.direction(),
                ClientBeams.isRealWorld(level) ? range : Math.min(range, PREVIEW_RANGE));
        // The cosmetic beam follows every tick; the lit spot only when it has moved enough to show
        length = hit.distance();
        List<Vec3> ends = traceCorners(level, at, hit, ClientBeams.isRealWorld(level) ? range : Math.min(range, PREVIEW_RANGE));
        // Centre ray in the open but part of the beam on something: light where that part lands
        BeamTrace.Landing lit = hit.hitBlock()
                ? new BeamTrace.Landing(hit, at.direction(), shape.onAxis(hit.distance()))
                : landing;
        BeamSpot next = lit == null ? null : BeamTrace.spotFor(lit.hit(), lit.direction(), lit.light() * brightness / 15.0);
        this.brightness = brightness;
        // Open sky at the lens: daylight washes the visible beam out there (Ponder scenes never)
        skyExposure = ClientBeams.isRealWorld(level)
                ? level.getBrightness(LightLayer.SKY, BlockPos.containing(at.origin())) / 15f : 0;
        boolean moved = worthPublishing(lastSpot, next);
        if (moved) {
            lastSpot = next;
            spot.set(next);
        }
        updateTint(level, at, lit, ends, brightness, tintRgb, moved);
        updateSpotLayer(level, at, ends, brightness, tintRgb < 0 ? WHITE_SPOT : tintRgb, moved);
    }

    /** The beam's brightened square; a moving light too far off to show it, or with shaders on, skips the work. */
    private void updateSpotLayer(Level level, BeamPose at, List<Vec3> ends, int brightness, int rgb, boolean moved) {
        if (ends.isEmpty() || !ClientBeams.isRealWorld(level) || !LightworksClientConfig.colouredLight()
                || (at.moving() && farFromCamera(at))) {
            clearSpotLayer();
            return;
        }
        if (spotLayer == null) {
            spotLayer = new ProjectedTint();
        }
        spotStale |= moved;
        if (spotStale || rgb != spotRgb || brightness != spotBrightness) {
            if (at.moving()) {
                boolean turn = spotTurn(level.getGameTime());
                if (spotInterval > MAX_SPOT_INTERVAL) {
                    clearSpotLayer();
                    return;
                }
                if (!turn) {
                    return;
                }
            }
            long start = System.nanoTime();
            spotLayer.spot(level, at.origin(), at.direction(), shape, lensHalf, ends, rgb, brightness / 15f, at.moving(),
                    rgb != WHITE_SPOT);
            if (at.moving()) {
                spotAverageNanos += (System.nanoTime() - start - spotAverageNanos) * 0.1;
            }
            spotStale = false;
            spotRgb = rgb;
            spotBrightness = brightness;
        }
    }

    /** True when this moving light may find its spot's faces this tick. */
    private boolean spotTurn(long tick) {
        if (tick != spotTick) {
            spotTick = tick;
            spotInterval = (int) Math.max(1, Math.ceil(spotsWanted * spotAverageNanos / SPOT_BUDGET_NANOS));
            spotsWanted = 0;
        }
        spotsWanted++;
        return (tick + System.identityHashCode(this)) % spotInterval == 0;
    }

    private void clearSpotLayer() {
        if (spotLayer != null) {
            spotLayer.clear();
        }
        spotRgb = -1;
    }

    private static boolean farFromCamera(BeamPose at) {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(at.origin()) > CORNER_TRACE_DISTANCE * CORNER_TRACE_DISTANCE;
    }


    /**
     * Traces the corner and edge-middle rays of the square beam; returns where every ray that hit a block
     * ended (the centre's hit included), for the coloured spot.
     */
    private List<Vec3> traceCorners(Level level, BeamPose at, BeamTrace.Hit centre, double range) {
        List<Vec3> ends = new ArrayList<>(CORNERS);
        if (centre.hitBlock()) {
            ends.add(centre.end());
        }
        Vec3 a = at.direction();
        boolean far = at.moving() && farFromCamera(at);
        // A centre ray that lands nowhere gives corners no length to keep, and the light goes where they land
        if (at.moving() && !far && centre.hitBlock() && --ticksToCorners > 0) {
            for (int i = 0; i < CORNERS; i++) {
                cornerLengths[i] = (float) Math.min(range, cornerRatios[i] * centre.distance());
            }
            return ends;
        }
        // Lights on one vehicle start their turns on different ticks
        ticksToCorners = MOVING_CORNER_INTERVAL + (ticksToCorners < 0 ? (System.identityHashCode(this) & 7) : 0);
        double[] u = BeamFrame.side(a.x, a.y, a.z);
        double[] v = BeamFrame.up(a.x, a.y, a.z, u);
        double spread = Math.tan(BeamTrace.visibleHalfAngle(shape));
        landing = null;
        for (int i = 0; i < CORNERS; i++) {
            if (i == CENTRE || far) {
                cornerLengths[i] = (float) centre.distance();
                cornerRatios[i] = 1;
                continue;
            }
            Vec3[] ray = BeamTrace.cornerRay(i, at.origin(), a, u, v, spread, lensHalf);
            BeamTrace.Hit hit = BeamTrace.trace(level, ray[0], ray[1], range);
            cornerLengths[i] = (float) hit.distance();
            cornerRatios[i] = (float) (hit.distance() / Math.max(0.01, centre.distance()));
            if (hit.hitBlock()) {
                ends.add(hit.end());
                if (BeamTrace.landsUsefully(hit) && (landing == null || hit.distance() < landing.hit().distance())) {
                    landing = new BeamTrace.Landing(hit, ray[1], BeamTrace.edgeLight(shape, hit.distance()));
                }
            }
        }
        if (!centre.hitBlock() && !far) {
            // Through a gap: the full-strength inner rays come before the weaker edge
            BeamTrace.Landing inner = BeamTrace.halfLanding(level, at.origin(), a, shape, lensHalf, range);
            if (inner != null) {
                landing = inner;
            }
        }
        return ends;
    }

    /** Finds the tinted faces again only when the light changed or its spot moved enough to be republished. */
    private void updateTint(Level level, BeamPose at, @Nullable BeamTrace.Landing lit, List<Vec3> ends,
            int brightness, int tintRgb, boolean moved) {
        if (tintRgb < 0 || ends.isEmpty() || !ClientBeams.isRealWorld(level)) {
            clearTint();
            return;
        }
        if (tint == null) {
            tint = new ProjectedTint();
        }
        if (moved || tint.faces() == 0 || tintRgb != tintedRgb || brightness != tintedBrightness) {
            // The white light the beam leaves round its spot (light block or dynamic light) is tinted too
            double glowRadius = lit == null ? 0 : lit.light() * brightness / 15.0 * GLOW_PER_LEVEL;
            Vec3 glowCentre = lit == null ? at.origin() : lit.hit().end().subtract(lit.direction().scale(0.5));
            tint.beam(level, at.origin(), at.direction(), shape, lensHalf, ends, glowCentre, glowRadius, tintRgb,
                    brightness / 15f, at.moving());
            tintedRgb = tintRgb;
            tintedBrightness = brightness;
        }
    }

    private void clearTint() {
        if (tint != null) {
            tint.clear();
        }
        tintedRgb = -1;
    }

    private static boolean worthPublishing(@Nullable BeamSpot current, @Nullable BeamSpot next) {
        if (current == null || next == null) {
            return current != next;
        }
        return next.differsFrom(current, MIN_SPOT_MOVE, MIN_PEAK_CHANGE, MIN_DIRECTION_DOT);
    }

    /** Light switched off (disabled by Contraption Controls, say): no spot and no beam until the next update. */
    public void off() {
        lastSpot = null;
        length = 0;
        brightness = 0;
        if (spot != null) {
            spot.set(null);
        }
        clearTint();
        clearSpotLayer();
    }

    /** Removes the dynamic light for good. */
    public void release() {
        if (spot != null) {
            spot.remove();
            spot = null;
        }
        clearTint();
        tint = null;
        clearSpotLayer();
        spotLayer = null;
        lastSpot = null;
        length = 0;
        brightness = 0;
    }

    /** The spot as last handed to the dynamic light: within {@link #MIN_SPOT_MOVE} of the traced one. */
    public @Nullable BeamSpot lastSpot() {
        return lastSpot;
    }

    /** How far the beam reached at the last update: to the hit, or its full range. 0 when off. */
    public double length() {
        return length;
    }

    /** How far corner ray {@code index} ({@link BeamFrame#corner}) reached at the last update. */
    public float cornerLength(int index) {
        return length <= 0 ? 0 : cornerLengths[index];
    }

    /** Brightness at the last update, 0 to 15; 0 when off. */
    public int brightness() {
        return brightness;
    }

    /** Sky light at the lens at the last update, 0 (cave, Ponder) to 1 (open sky). */
    public float skyExposure() {
        return skyExposure;
    }
}
