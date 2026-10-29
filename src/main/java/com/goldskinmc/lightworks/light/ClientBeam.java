package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.compat.DynamicLights;
import com.goldskinmc.lightworks.compat.DynamicSpot;
import com.goldskinmc.lightworks.host.BeamPose;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.Nullable;

/**
 * The client half of one beam: traces it, moves its dynamic spot to where it lands, and remembers the
 * beam's length for the cosmetic beam. Client thread only.
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

    private final ConeFalloff shape;
    @Nullable
    private DynamicSpot spot;
    @Nullable
    private ProjectedTint tint;
    private int tintedRgb = -1;
    private int tintedBrightness;
    @Nullable
    private BeamSpot lastSpot;
    private double length;
    private int brightness;
    private float skyExposure;

    public ClientBeam(ConeFalloff shape) {
        this.shape = shape;
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
        BeamSpot next = BeamTrace.spotFor(hit, at.direction(), shape.onAxis(hit.distance()) * brightness / 15.0);
        // The cosmetic beam follows every tick; the lit spot only when it has moved enough to show
        length = hit.distance();
        this.brightness = brightness;
        // Open sky at the lens: daylight washes the visible beam out there (Ponder scenes never)
        skyExposure = ClientBeams.isRealWorld(level)
                ? level.getBrightness(LightLayer.SKY, BlockPos.containing(at.origin())) / 15f : 0;
        boolean moved = worthPublishing(lastSpot, next);
        if (moved) {
            lastSpot = next;
            spot.set(next);
        }
        updateTint(level, at, hit, brightness, tintRgb, moved);
    }

    /** Finds the tinted faces again only when the light changed or its spot moved enough to be republished. */
    private void updateTint(Level level, BeamPose at, BeamTrace.Hit hit, int brightness, int tintRgb, boolean moved) {
        if (tintRgb < 0 || !hit.hitBlock() || !ClientBeams.isRealWorld(level)) {
            clearTint();
            return;
        }
        if (tint == null) {
            tint = new ProjectedTint();
        }
        if (moved || tint.faces() == 0 || tintRgb != tintedRgb || brightness != tintedBrightness) {
            tint.beam(level, at.origin(), at.direction(), shape, hit.end(), tintRgb, brightness / 15f);
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
    }

    /** Removes the dynamic light for good. */
    public void release() {
        if (spot != null) {
            spot.remove();
            spot = null;
        }
        clearTint();
        tint = null;
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

    /** Brightness at the last update, 0 to 15; 0 when off. */
    public int brightness() {
        return brightness;
    }

    /** Sky light at the lens at the last update, 0 (cave, Ponder) to 1 (open sky). */
    public float skyExposure() {
        return skyExposure;
    }
}
