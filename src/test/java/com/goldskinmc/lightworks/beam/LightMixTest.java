package com.goldskinmc.lightworks.beam;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LightMixTest {
    /** Colours {r, g, b} at the given strengths, mixed. */
    private static float[] mix(float[][] colours, float[] strengths) {
        float r = 0;
        float g = 0;
        float b = 0;
        float total = 0;
        for (int i = 0; i < colours.length; i++) {
            r += colours[i][0] * strengths[i];
            g += colours[i][1] * strengths[i];
            b += colours[i][2] * strengths[i];
            total += strengths[i];
        }
        return new float[] {LightMix.channel(r, r, g, b, total), LightMix.channel(g, r, g, b, total),
                LightMix.channel(b, r, g, b, total)};
    }

    private static final float[] RED = {1, 0, 0};
    private static final float[] GREEN = {0, 1, 0};
    private static final float[] BLUE = {0, 0, 1};

    @Test
    void noLightChangesNothing() {
        assertArrayEquals(new float[] {1, 1, 1}, mix(new float[0][], new float[0]), 1e-6f);
    }

    @Test
    void oneLightFadesFromWhiteToItsColourAsItAlwaysDid() {
        for (float k = 0; k <= 1; k += 0.1f) {
            float[] mixed = mix(new float[][] {{1, 0.5f, 0}}, new float[] {k});
            assertEquals(1, mixed[0], 1e-5f);
            assertEquals(1 - k * 0.5f, mixed[1], 1e-5f);
            assertEquals(1 - k, mixed[2], 1e-5f);
        }
    }

    @Test
    void coloursAddUpInsteadOfGoingDark() {
        float[] full = {1, 1};
        assertArrayEquals(new float[] {1, 1, 0}, mix(new float[][] {RED, GREEN}, full), 1e-6f);
        assertArrayEquals(new float[] {1, 0, 1}, mix(new float[][] {RED, BLUE}, full), 1e-6f);
        assertArrayEquals(new float[] {0, 1, 1}, mix(new float[][] {GREEN, BLUE}, full), 1e-6f);
        assertArrayEquals(new float[] {1, 1, 1}, mix(new float[][] {RED, GREEN, BLUE}, new float[] {1, 1, 1}), 1e-6f);
    }

    @Test
    void theStrongerLightLeads() {
        float[] mixed = mix(new float[][] {RED, GREEN}, new float[] {1, 0.5f});
        assertEquals(1, mixed[0], 1e-6f);
        assertEquals(0.5f, mixed[1], 1e-6f);
        assertEquals(0, mixed[2], 1e-6f);
    }

    @Test
    void theOrderOfTheLightsDoesNotMatter() {
        assertArrayEquals(mix(new float[][] {RED, GREEN, BLUE}, new float[] {0.8f, 0.3f, 0.5f}),
                mix(new float[][] {BLUE, RED, GREEN}, new float[] {0.5f, 0.8f, 0.3f}), 1e-6f);
    }
}
