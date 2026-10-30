package com.goldskinmc.lightworks.beam;

/**
 * Which rays of a beam make its light on fixed lights. A real light has no main ray: every part of its cone
 * lands where it lands, so a gap in a wall only darkens the gap. Block light has one point per light block,
 * so a wide beam gets several, one where each of these rays lands: {@code across} rays along the beam's side
 * axis and {@code down} along its other axis. A narrow beam really does land in one place.
 * <p>
 * Pure maths with no Minecraft classes, so it is unit-tested without booting the game.
 */
public record BeamRays(int across, int down) {
    public static final BeamRays SINGLE = new BeamRays(1, 1);

    public BeamRays {
        if (across != 1 && across != 3 || down != 1 && down != 3) {
            throw new IllegalArgumentException("Rays per side must be 1 or 3 to match the beam's corner rays");
        }
    }

    public boolean single() {
        return across == 1 && down == 1;
    }

    /** Offsets {u, v}, each -1, 0 or 1, of the rays: the centre first. */
    public int[][] offsets() {
        int[][] all = new int[across * down][];
        all[0] = new int[] {0, 0};
        int next = 1;
        for (int v = -(down / 2); v <= down / 2; v++) {
            for (int u = -(across / 2); u <= across / 2; u++) {
                if (u != 0 || v != 0) {
                    all[next++] = new int[] {u, v};
                }
            }
        }
        return all;
    }
}
