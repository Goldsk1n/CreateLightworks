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
    private final ConeFalloff shape;
    @Nullable
    private DynamicSpot spot;
    @Nullable
    private BeamSpot lastSpot;
    private double length;

    public ClientBeam(ConeFalloff shape) {
        this.shape = shape;
    }

    public ConeFalloff shape() {
        return shape;
    }

    public void update(Level level, BeamPose beam) {
        if (spot == null) {
            spot = DynamicLights.createSpot();
        }
        BeamTrace.Hit hit = BeamTrace.trace(level, beam.origin(), beam.direction(), shape.range());
        lastSpot = BeamTrace.spotFor(hit, beam.direction(), shape.onAxis(hit.distance()));
        length = hit.distance();
        spot.set(lastSpot);
    }

    /** Light switched off (disabled by Contraption Controls, say): no spot and no beam until the next update. */
    public void off() {
        lastSpot = null;
        length = 0;
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
    }

    public @Nullable BeamSpot lastSpot() {
        return lastSpot;
    }

    /** How far the beam reached at the last update: to the hit, or its full range. 0 when off. */
    public double length() {
        return length;
    }
}
