package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.compat.Shaders;
import com.goldskinmc.lightworks.render.MainTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The visible beam and lens glow drawn through Minecraft's beacon beam shader under the packs in
 * {@code shaderPacksWithBeaconBeams}. Solas has no program for lightning (Iris fell back to its entity program, which lit
 * the beam like a surface: nearly invisible at night) and squares the alpha of particles, but draws beacon beams with a
 * plain pass-through (colour x 1.5, no lighting, an alpha test of 1/10000). Bliss and Photon drew nothing this way: they
 * take {@link PackParticleBeam}.
 */
final class PackBeaconBeam {
    private static final ResourceLocation WHITE = Lightworks.id("textures/misc/white.png");
    /** Solas brightens beacon beams half again. */
    private static final float PACK_GAIN = 1.5f;

    static final RenderType BEAM = type("lightworks_beam_pack_beacon", 1536, false);
    static final RenderType GLOW = type("lightworks_lens_glow_pack_beacon", 512, true);

    private PackBeaconBeam() {
    }

    private static RenderType type(String name, int size, boolean overEverything) {
        RenderType.CompositeState.CompositeStateBuilder state = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_BEACON_BEAM_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(WHITE, false, false))
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                .setOutputState(MainTarget.OUTPUT)
                .setCullState(overEverything ? RenderStateShard.NO_CULL : RenderStateShard.CULL);
        if (overEverything) {
            state.setDepthTestState(RenderStateShard.NO_DEPTH_TEST);
        }
        return RenderType.create(name, DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, size, false, true,
                state.createCompositeState(false));
    }

    static boolean active() {
        return !LateBeams.active() && Shaders.inUse() && LightworksClientConfig.packWantsBeaconBeams();
    }

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
            out.setColor(Math.round((red & 0xFF) / PACK_GAIN), Math.round((green & 0xFF) / PACK_GAIN),
                            Math.round((blue & 0xFF) / PACK_GAIN), alpha & 0xFF)
                    .setUv(0.5f, 0.5f)
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
