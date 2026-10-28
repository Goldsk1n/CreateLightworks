package com.goldskinmc.lightworks.compat;

import com.goldskinmc.lightworks.beam.BeamSpot;
import org.jetbrains.annotations.Nullable;

/** A client-side dynamic light that follows one beam's lit spot. Client thread only. */
public interface DynamicSpot {
    /** Does nothing: used when no dynamic-light mod is installed. */
    DynamicSpot NONE = new DynamicSpot() {
        @Override
        public void set(@Nullable BeamSpot spot) {
        }

        @Override
        public void remove() {
        }
    };

    /** Moves the light to {@code spot}, or switches it off when null. */
    void set(@Nullable BeamSpot spot);

    /** Removes the light for good. */
    void remove();
}
