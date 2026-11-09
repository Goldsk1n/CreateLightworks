package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.compat.Shaders;
import com.goldskinmc.lightworks.render.MainTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The visible beam and lens glow in the End under a shader pack. Packs replace vanilla's lightning program, which the
 * beam is drawn with elsewhere, and Complementary treats anything drawn with it that is not a lightning bolt as the Ender
 * Dragon's death beams: in the End it drops four pixels in five and brightens the rest, which turned the beam's haze
 * into static. So there the beam goes through the glowing-eyes program instead (spider and Enderman eyes): additive,
 * full-bright, no dithering, a white texture standing in for none. Complementary bends colour on the way, out = c^1.5 *
 * (1.5 - 0.5 c^1.5) for a grey c, so each channel is sent through that curve's inverse first, which gives back the
 * colour the beam asked for; strong dyes shift a little more (its tweaks for particular mobs). The beam's alpha goes into
 * the colour, which the additive blend multiplies by it anyway, since a pack's alpha test drops what is fainter than a
 * tenth: all of the haze. A beam of our own shader
 * was tried and dropped: with a pack on, Iris draws nothing from a shader it does not know.
 */
final class EndPackBeam {
    private static final ResourceLocation WHITE = Lightworks.id("textures/misc/white.png");

    static final RenderType BEAM = RenderType.create("lightworks_beam_end_pack", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(WHITE, false, false))
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setOutputState(MainTarget.OUTPUT)
                    .setCullState(RenderStateShard.CULL)
                    .createCompositeState(false));

    static final RenderType GLOW = RenderType.create("lightworks_lens_glow_end_pack", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 512, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(WHITE, false, false))
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(MainTarget.OUTPUT)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /**
     * The eyes program's light also counts for more once the pack has finished with the picture: through it the beam lit
     * a wall 1.7 times as much as the same beam does in the overworld under the same pack (measured in testing).
     */
    private static final float MATCH_OVERWORLD = 0.6f;

    /** For each colour channel 0-255 the value the pack's curve turns into it. */
    private static final int[] INVERSE = new int[256];

    static {
        for (int c = 0; c < 256; c++) {
            double want = c / 255.0;
            double low = 0;
            double high = 1;
            for (int i = 0; i < 40; i++) {
                double mid = (low + high) / 2;
                if (curve(mid) < want) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            INVERSE[c] = (int) Math.round(high * 255);
        }
    }

    private EndPackBeam() {
    }

    /** Complementary's glowing-eyes colour for a grey channel. */
    static double curve(double c) {
        double c15 = Math.pow(c, 1.5);
        return c15 * (1.5 - 0.5 * c15);
    }

    /** True while a shader pack draws the world and the camera is in the End: the beam takes this route then. */
    static boolean active() {
        Level level = Minecraft.getInstance().level;
        return level != null && level.dimension() == Level.END && Shaders.inUse() && !LateBeams.active();
    }

    /** Takes the beam's position-and-colour vertices and gives the eyes program what it needs as well. */
    static VertexConsumer wrap(VertexConsumer out) {
        return new Adapter(out);
    }

    private record Adapter(VertexConsumer out) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            // Added in proportion to alpha either way, so it goes into the colour: the haze is fainter than the alpha test
            // most of a pack's programs make (a tenth), which dropped all of it
            float a = (alpha & 0xFF) / 255f * MATCH_OVERWORLD;
            out.setColor(INVERSE[Math.round((red & 0xFF) * a)], INVERSE[Math.round((green & 0xFF) * a)],
                            INVERSE[Math.round((blue & 0xFF) * a)], 255)
                    .setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(0, 1, 0);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
