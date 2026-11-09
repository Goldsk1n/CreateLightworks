package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.compat.Shaders;
import com.goldskinmc.lightworks.render.MainTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The visible beam and lens glow drawn through Minecraft's particle shader under the shader packs in
 * {@code shaderPacksWithParticleBeams}. Without a program for lightning, or blending translucents their own way, those packs
 * drew the usual beam black (Bliss: Iris falls back to its entity program, which it draws opaque) or not at all (Photon),
 * but every pack has to draw particles well (rain, smoke, flames). The particle shader drops fragments fainter than a
 * tenth, as packs do too, so each vertex carries at least that alpha and its colour is scaled down to keep the light it
 * adds; full-bright light, a white texture. Solas drew nothing this way (it squares alpha and then drops what is under
 * 0.02): {@link PackBeaconBeam} serves it.
 */
final class PackParticleBeam {
    private static final ResourceLocation WHITE = Lightworks.id("textures/misc/white.png");
    private static final RenderStateShard.ShaderStateShard PARTICLE_SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getParticleShader);
    /** The alpha every vertex has at least: above the tenth the particle shader and packs drop. */
    private static final int MIN_ALPHA = 31;

    static final RenderType BEAM = type("lightworks_beam_pack_particle", 1536, false);
    static final RenderType GLOW = type("lightworks_lens_glow_pack_particle", 512, true);

    private PackParticleBeam() {
    }

    private static RenderType type(String name, int size, boolean overEverything) {
        RenderType.CompositeState.CompositeStateBuilder state = RenderType.CompositeState.builder()
                .setShaderState(PARTICLE_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(WHITE, false, false))
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                .setOutputState(MainTarget.OUTPUT)
                .setCullState(overEverything ? RenderStateShard.NO_CULL : RenderStateShard.CULL);
        if (overEverything) {
            state.setDepthTestState(RenderStateShard.NO_DEPTH_TEST);
        }
        return RenderType.create(name, DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, size, false, true,
                state.createCompositeState(false));
    }

    /** True while a pack listed in {@code shaderPacksWithParticleBeams} draws the world. */
    static boolean active() {
        return Shaders.inUse() && LightworksClientConfig.packWantsParticleBeams();
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
            int a = Math.max(alpha & 0xFF, MIN_ALPHA);
            // The light added stays colour x alpha: a raised alpha takes it out of the colour
            float keep = (alpha & 0xFF) / (float) a;
            out.setUv(0.5f, 0.5f)
                    .setColor(Math.round((red & 0xFF) * keep), Math.round((green & 0xFF) * keep),
                            Math.round((blue & 0xFF) * keep), a)
                    .setLight(LightTexture.FULL_BRIGHT);
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
