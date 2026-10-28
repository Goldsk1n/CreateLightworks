package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.compat.DynamicLights;
import com.goldskinmc.lightworks.compat.DynamicSpot;
import com.goldskinmc.lightworks.host.BeamPose;
import net.minecraft.world.level.Level;
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

    private final ConeFalloff shape;
    @Nullable
    private DynamicSpot spot;
    @Nullable
    private BeamSpot lastSpot;
    private double length;
    private int brightness;

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
     */
    public void update(Level level, BeamPose beam, BeamPose drawn, int brightness) {
        if (!ClientBeams.enabled() || brightness <= 0) {
            off();
            return;
        }
        if (spot == null) {
            // Ponder scenes and contraption render worlds draw beams too, but only the real world gets light
            spot = ClientBeams.isRealWorld(level) ? DynamicLights.createSpot() : DynamicSpot.NONE;
        }
        BeamPose at = beam.moving() ? drawn : beam;
        BeamTrace.Hit hit = BeamTrace.trace(level, at.origin(), at.direction(), BeamTrace.range(shape));
        BeamSpot next = BeamTrace.spotFor(hit, at.direction(), shape.onAxis(hit.distance()) * brightness / 15.0);
        // The cosmetic beam follows every tick; the lit spot only when it has moved enough to show
        length = hit.distance();
        this.brightness = brightness;
        if (worthPublishing(lastSpot, next)) {
            lastSpot = next;
            spot.set(next);
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
            spot.set(null);
        }
    }

    /** Removes the dynamic light for good. */
    public void release() {
        if (spot != null) {
            spot.remove();
            spot = null;
        }
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
}
