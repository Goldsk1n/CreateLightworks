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
     * Time all moving lights together may spend finding their lit faces each tick. Over it they take turns, each
     * refreshing less often, so a scene full of them keeps its frame rate and only their spots step. Past
     * {@link #MAX_LAYERS_INTERVAL} ticks a spot would no longer follow its beam, so they go without until fewer want one.
     */
    private static final long LAYERS_BUDGET_NANOS = 1_500_000;
    private static final int MAX_LAYERS_INTERVAL = 10;
    private static final int LAYERS_SAMPLE_TICKS = 20;
    private static final int TOO_BIG_RETRY_TICKS = 10;
    /** A moving light's colour and spot fade from this far from the camera, in blocks, and are gone at the next. */
    private static final double LAYER_FADE_DISTANCE = 48;
    private static final double LAYER_DISTANCE = 64;
    /** Blocks past the lit area whose change still counts, for the faces they cover or uncover. */
    private static final double CHANGE_MARGIN = 2;
    private static long layersTick;
    private static int layersWanted;
    private static int layersInterval = 1;
    private static boolean layersOff;
    private static boolean layersSampled;
    private static double layersAverageNanos;

    private final ConeFalloff shape;
    /** Half the lens's width, in blocks: the square the beam starts as, flush on the glass. */
    private final float lensHalf;
    @Nullable
    private DynamicSpot spot;
    @Nullable
    private ProjectedTint tint;
    @Nullable
    private ProjectedTint spotLayer;
    private int layersRgb = -1;
    private int layersBrightness;
    private boolean layersShown;
    /** A beam found too big for the limit tries again at this game time. */
    private long retryAt;
    /** Moved since its lit faces were last found, waiting for its turn. */
    private boolean layersStale;
    private long changesSeen = BlockChanges.count();
    @Nullable
    private BeamSpot lastSpot;
    private double length;
    /** Corner length over centre length at the last corner trace, by {@link BeamFrame#corner} index. */
    private final float[] cornerRatios = new float[CORNERS];
    /** How far each ray round the visible beam's rim reached ({@link BeamFrame#rimU}), and that over the centre's length. */
    private final float[] rimLengths = new float[BeamFrame.RIM];
    private final float[] rimRatios = new float[BeamFrame.RIM];
    /** Whether each rim ray stopped on a block, rather than running out of range. */
    private final boolean[] rimHits = new boolean[BeamFrame.RIM];
    private int ticksToCorners;
    /** Nearest place a corner or edge ray landed at the last corner trace, for when the centre ray misses. */
    @Nullable
    private BeamTrace.Landing landing;
    private int brightness;
    private int tintRgb = -1;
    private float skyExposure;
    private boolean preview;

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

    /** Drawn in a Ponder scene, not the world. */
    public boolean preview() {
        return preview;
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
        this.tintRgb = tintRgb;
        preview = !ClientBeams.isRealWorld(level);
        // Open sky at the lens: daylight washes the visible beam out there (Ponder scenes never)
        skyExposure = ClientBeams.isRealWorld(level)
                ? level.getBrightness(LightLayer.SKY, BlockPos.containing(at.origin())) / 15f : 0;
        boolean moved = worthPublishing(lastSpot, next);
        if (moved) {
            lastSpot = next;
            setSpot(next);
        }
        if (ClientSafety.works(ClientSafety.LAYERS)) {
            try {
                updateLayers(level, at, lit, ends, brightness, tintRgb, moved);
            } catch (RuntimeException e) {
                ClientSafety.failed(ClientSafety.LAYERS, e);
                clearLayers();
            }
        }
    }

    private void setSpot(@Nullable BeamSpot to) {
        if (ClientSafety.works(ClientSafety.DYNAMIC_LIGHT)) {
            try {
                spot.set(to);
            } catch (RuntimeException e) {
                ClientSafety.failed(ClientSafety.DYNAMIC_LIGHT, e);
            }
        }
    }

    /**
     * What is painted on the faces the beam lights, its colour and its spot. Found again when the light changed, its
     * spot moved enough to be republished or a block in its way changed; moving lights take turns at this. A moving
     * light far off fades out.
     */
    private void updateLayers(Level level, BeamPose at, @Nullable BeamTrace.Landing lit, List<Vec3> ends, int brightness,
            int tintRgb, boolean moved) {
        double camera = at.moving() ? Math.sqrt(cameraDistanceSqr(at)) : 0;
        if (ends.isEmpty() || !ClientBeams.isRealWorld(level) || !LightworksClientConfig.colouredLight()
                || camera >= LAYER_DISTANCE) {
            clearLayers();
            return;
        }
        layersStale |= moved;
        if (changesSeen != BlockChanges.count()) {
            layersStale |= BlockChanges.any(changesSeen, pos -> reaches(at, lit, brightness, pos));
            changesSeen = BlockChanges.count();
        }
        if (!layersStale && tintRgb == layersRgb && brightness == layersBrightness && layersShown) {
            return;
        }
        long tick = level.getGameTime();
        boolean sample = false;
        if (at.moving()) {
            boolean turn = layersTurn(tick);
            if (layersOff) {
                clearLayers();
                // One light a second still times the work, or the lights would never find out they fit again
                sample = tick % LAYERS_SAMPLE_TICKS == 0 && !layersSampled;
                if (!sample) {
                    return;
                }
                layersSampled = true;
            } else if (!turn || tick < retryAt) {
                return;
            }
        }
        long start = System.nanoTime();
        ProjectedTint.newBeam();
        float strength = brightness / 15f
                * (float) Math.min(1, (LAYER_DISTANCE - camera) / (LAYER_DISTANCE - LAYER_FADE_DISTANCE));
        // The white light the beam leaves round its spot (light block or dynamic light)
        double glowRadius = lit == null ? 0 : lit.light() * brightness / 15.0 * GLOW_PER_LEVEL;
        Vec3 glowCentre = lit == null ? at.origin() : lit.hit().end().subtract(lit.direction().scale(0.5));
        if (tintRgb < 0) {
            if (tint != null) {
                tint.clear();
            }
        } else {
            if (tint == null) {
                tint = new ProjectedTint();
            }
            // That light is tinted too
            tint.beam(level, at.origin(), at.direction(), shape, lensHalf, ends, glowCentre, glowRadius, tintRgb, strength,
                    at.moving());
        }
        if (spotLayer == null) {
            spotLayer = new ProjectedTint();
        }
        spotLayer.spot(level, at.origin(), at.direction(), shape, lensHalf, ends, tintRgb < 0 ? WHITE_SPOT : tintRgb, strength,
                at.moving(), at.moving() && !DynamicLights.present(), glowCentre, glowRadius);
        if (at.moving()) {
            if (spotLayer.faces() > 0) {
                layersAverageNanos += (System.nanoTime() - start - layersAverageNanos) * (sample ? 0.5 : 0.1);
            } else {
                // Too big for the limit, as a beam over open ground usually stays for a while
                retryAt = tick + TOO_BIG_RETRY_TICKS;
            }
        }
        if (sample) {
            clearLayers();
            return;
        }
        layersStale = false;
        layersShown = true;
        layersRgb = tintRgb;
        layersBrightness = brightness;
    }

    /** True if a block here could change what the layers paint: inside the beam's square or the glow round its spot. */
    private boolean reaches(BeamPose at, @Nullable BeamTrace.Landing lit, int brightness, long pos) {
        Vec3 centre = Vec3.atCenterOf(BlockPos.of(pos));
        if (lit != null) {
            double glow = lit.light() * brightness / 15.0 * GLOW_PER_LEVEL + CHANGE_MARGIN;
            if (lit.hit().end().distanceToSqr(centre) < glow * glow) {
                return true;
            }
        }
        Vec3 fromLens = centre.subtract(at.origin());
        double along = fromLens.dot(at.direction());
        if (along < -CHANGE_MARGIN || along > BeamTrace.range(shape) + CHANGE_MARGIN) {
            return false;
        }
        double across = fromLens.subtract(at.direction().scale(along)).length();
        // The square's corners are further out than its sides
        return across < Math.tan(BeamTrace.visibleHalfAngle(shape)) * Math.max(0, along) * Math.sqrt(2) + lensHalf + CHANGE_MARGIN;
    }

    /** True when this moving light may find its faces this tick. */
    private boolean layersTurn(long tick) {
        if (tick != layersTick) {
            layersTick = tick;
            // Beside the first, which always fits: a lone light follows its beam every tick, however big its beam
            layersInterval = (int) Math.max(1, Math.ceil((layersWanted - 1) * layersAverageNanos / LAYERS_BUDGET_NANOS));
            layersWanted = 0;
            layersSampled = false;
            // Back on only well under the limit, so a scene near it does not flicker
            layersOff = layersInterval > MAX_LAYERS_INTERVAL || (layersOff && layersInterval > MAX_LAYERS_INTERVAL / 2);
        }
        layersWanted++;
        return (tick + System.identityHashCode(this)) % Math.min(layersInterval, MAX_LAYERS_INTERVAL) == 0;
    }

    private void clearLayers() {
        if (tint != null) {
            tint.clear();
        }
        if (spotLayer != null) {
            spotLayer.clear();
        }
        layersShown = false;
    }

    private static double cameraDistanceSqr(BeamPose at) {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(at.origin());
    }

    private static boolean farFromCamera(BeamPose at) {
        return cameraDistanceSqr(at) > CORNER_TRACE_DISTANCE * CORNER_TRACE_DISTANCE;
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
            for (int k = 0; k < BeamFrame.RIM; k++) {
                rimLengths[k] = (float) Math.min(range, rimRatios[k] * centre.distance());
            }
            return ends;
        }
        // Lights on one vehicle start their turns on different ticks
        ticksToCorners = MOVING_CORNER_INTERVAL + (ticksToCorners < 0 ? (System.identityHashCode(this) & 7) : 0);
        double[] u = BeamFrame.side(a.x, a.y, a.z);
        double[] v = BeamFrame.up(a.x, a.y, a.z, u);
        double spread = Math.tan(BeamTrace.visibleHalfAngle(shape));
        landing = null;
        float[] cornerLengths = new float[CORNERS];
        boolean[] cornerHits = new boolean[CORNERS];
        for (int i = 0; i < CORNERS; i++) {
            if (i == CENTRE || far) {
                cornerRatios[i] = 1;
                cornerLengths[i] = (float) centre.distance();
                cornerHits[i] = centre.hitBlock();
                continue;
            }
            Vec3[] ray = BeamTrace.cornerRay(i, at.origin(), a, u, v, spread, lensHalf);
            BeamTrace.Hit hit = BeamTrace.trace(level, ray[0], ray[1], range);
            cornerRatios[i] = (float) (hit.distance() / Math.max(0.01, centre.distance()));
            cornerLengths[i] = (float) hit.distance();
            cornerHits[i] = hit.hitBlock();
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
        traceRim(level, at, centre, range, u, v, spread, cornerLengths, cornerHits);
        return ends;
    }

    /**
     * The visible beam's outline. A fixed light traces every rim ray: it does so only when it changes or every few
     * ticks. A moving light takes them between its corner and edge rays instead: traced each time its corners are, 50
     * turning level beams ran their client work from 5 to 12 ms a tick (the self-test's load-level).
     */
    private void traceRim(Level level, BeamPose at, BeamTrace.Hit centre, double range, double[] u, double[] v,
            double spread, float[] cornerLengths, boolean[] cornerHits) {
        for (int k = 0; k < BeamFrame.RIM; k++) {
            double ru = BeamFrame.rimU(k);
            double rv = BeamFrame.rimV(k);
            if (at.moving()) {
                // Along the side the ray sits on, between the corner or edge rays either side of it
                boolean alongU = Math.abs(rv) == 1;
                double t = alongU ? ru : rv;
                int from = t <= 0 ? -1 : 0;
                double share = t - from;
                int a = alongU ? BeamFrame.corner(from, (int) rv) : BeamFrame.corner((int) ru, from);
                int b = alongU ? BeamFrame.corner(from + 1, (int) rv) : BeamFrame.corner((int) ru, from + 1);
                rimLengths[k] = (float) (cornerLengths[a] + (cornerLengths[b] - cornerLengths[a]) * share);
                rimHits[k] = share < 0.5 ? cornerHits[a] : cornerHits[b];
            } else {
                Vec3[] ray = BeamTrace.ray(ru, rv, at.origin(), at.direction(), u, v, spread, lensHalf);
                BeamTrace.Hit rim = BeamTrace.trace(level, ray[0], ray[1], range);
                rimLengths[k] = (float) rim.distance();
                rimHits[k] = rim.hitBlock();
            }
            rimRatios[k] = (float) (rimLengths[k] / Math.max(0.01, centre.distance()));
        }
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
            setSpot(null);
        }
        clearLayers();
    }

    /** Removes the dynamic light for good. */
    public void release() {
        if (spot != null) {
            spot.remove();
            spot = null;
        }
        clearLayers();
        tint = null;
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

    /** How far rim ray {@code k} ({@link BeamFrame#rimU}) reached at the last update, for the visible beam's sides. */
    public float rimLength(int k) {
        return length <= 0 ? 0 : rimLengths[k];
    }

    /** True when rim ray {@code k} stopped on a block at the last update, so the visible beam may run into it. */
    public boolean rimHit(int k) {
        return rimHits[k];
    }

    /** A dyed light's colour at the last update, 0xRRGGBB; -1 for an undyed light. */
    public int tintRgb() {
        return tintRgb;
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
