package com.goldskinmc.lightworks.beam;

/**
 * The colour a block face is multiplied by when coloured lights reach it. Light adds up and a surface then
 * multiplies it, so the lights' colours are added first and the one result is multiplied over the face, once:
 * red and green make yellow and red, green and blue make white, where multiplying each light in turn would
 * make them darker and darker. Whatever share of the face no light has claimed stays white (the block's own
 * lighting); the result is scaled so its brightest channel is 1, because the layer can only take light away.
 * <p>
 * Pure maths with no Minecraft classes, so it is unit-tested without booting the game.
 *
 * @see #channel
 */
public final class LightMix {
    private LightMix() {
    }

    /**
     * One channel of the mix.
     *
     * @param channel  the sum of this channel over the lights (each light's colour times its strength)
     * @param red      the same sum for red
     * @param green    the same sum for green
     * @param blue     the same sum for blue
     * @param strength the sum of the lights' strengths, each 0 to 1
     * @return the channel to multiply by, 0 to 1
     */
    public static float channel(float channel, float red, float green, float blue, float strength) {
        float white = Math.max(0, 1 - strength);
        float brightest = Math.max(red, Math.max(green, blue)) + white;
        return brightest <= 0 ? 1 : (channel + white) / brightest;
    }
}
