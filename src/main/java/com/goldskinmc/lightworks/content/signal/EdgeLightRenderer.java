package com.goldskinmc.lightworks.content.signal;

import com.goldskinmc.lightworks.render.PartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws an Edge Light's lens in Ponder scenes only, which light every block alike and so lose the baked lens's
 * glow. In the world and on vehicles the baked model does it all, and this returns at once.
 */
public class EdgeLightRenderer implements BlockEntityRenderer<EdgeLightBlockEntity> {
    private static final float LENS_SCALE = 1.05f;

    public EdgeLightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EdgeLightBlockEntity light, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        if (!(light.getLevel() instanceof PonderLevel)) {
            return;
        }
        // Scenes change the block state, not the block entity's copy of it
        BlockState state = light.getLevel().getBlockState(light.getBlockPos());
        Direction facing = state.getValue(EdgeLightBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(facing.getRotation());
        // Just outside the baked lens, or that one covers it
        poseStack.translate(0, -0.5, 0);
        poseStack.scale(LENS_SCALE, LENS_SCALE, LENS_SCALE);
        poseStack.translate(-0.5, 0, -0.5);
        PartRenderer.lens(poseStack, buffers, "edge_light/lens",
                state.getValue(EdgeLightBlock.COLOUR).getTextureDiffuseColor() & 0xFFFFFF,
                state.getValue(EdgeLightBlock.BRIGHTNESS) / 15f, packedLight);
        poseStack.popPose();
    }
}
