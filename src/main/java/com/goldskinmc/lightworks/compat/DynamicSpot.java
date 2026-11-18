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

    /**
     * True when the light may be moved every frame, for a lamp turned with the mouse. Only LambDynamicLights' is: under
     * Sodium Dynamic Lights the game ran at half its frame rate with it, and a client light block made the ground pulse
     * (self-test handheld-motion, Nov 18).
     */
    default boolean movesEveryFrame() {
        return false;
    }
}
