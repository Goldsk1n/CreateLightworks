package com.goldskinmc.lightworks.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

/**
 * Draws {@link Partials} at the current pose: solid parts shaded like blocks (by the direction each face
 * points after the pose's rotation), lenses unshaded and full-bright while lit, so they glow at night and
 * under shader packs.
 */
public final class PartRenderer {
    private static final Direction[] SIDES_AND_NONE = {
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null
    };
    /** How much of its colour an unlit lens keeps, so it reads as tinted glass. */
    private static final float UNLIT_LENS = 0.3f;
    private static final RandomSource RANDOM = RandomSource.create();

    private PartRenderer() {
    }

    public static void solid(PoseStack poseStack, MultiBufferSource buffers, BakedModel model, int packedLight) {
        draw(poseStack, buffers.getBuffer(RenderType.cutout()), model, 1, 1, 1, packedLight, true);
    }

    /**
     * @param rgb        lens colour, 0xRRGGBB
     * @param intensity  0 (unlit glass) to 1 (fully lit)
     * @param worldLight light at the block, used while unlit
     */
    public static void lens(PoseStack poseStack, MultiBufferSource buffers, BakedModel model, int rgb, float intensity,
            int worldLight) {
        float k = UNLIT_LENS + (1 - UNLIT_LENS) * Math.max(0, Math.min(1, intensity));
        float red = (rgb >> 16 & 0xFF) / 255f * k;
        float green = (rgb >> 8 & 0xFF) / 255f * k;
        float blue = (rgb & 0xFF) / 255f * k;
        int light = intensity > 0.02f ? LightTexture.FULL_BRIGHT : worldLight;
        draw(poseStack, buffers.getBuffer(RenderType.cutout()), model, red, green, blue, light, false);
    }

    private static void draw(PoseStack poseStack, VertexConsumer out, BakedModel model, float red, float green,
            float blue, int light, boolean shade) {
        PoseStack.Pose pose = poseStack.last();
        Vector3f normal = new Vector3f();
        for (Direction side : SIDES_AND_NONE) {
            RANDOM.setSeed(42);
            for (BakedQuad quad : model.getQuads(null, side, RANDOM, ModelData.EMPTY, null)) {
                float k = 1;
                if (shade && quad.isShade()) {
                    pose.normal().transform(normal.set(quad.getDirection().step()));
                    k = shade(normal);
                }
                out.putBulkData(pose, quad, red * k, green * k, blue * k, 1f, light, OverlayTexture.NO_OVERLAY);
            }
        }
    }

    /** Vanilla's block face shading (top 1, sides 0.8 and 0.6, bottom 0.5), blended for tilted faces. */
    private static float shade(Vector3f n) {
        return n.x * n.x * 0.6f + n.z * n.z * 0.8f + n.y * n.y * (n.y > 0 ? 1f : 0.5f);
    }
}
