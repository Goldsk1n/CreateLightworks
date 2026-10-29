package com.goldskinmc.lightworks.content.signal;

import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.render.MountFrame;
import com.goldskinmc.lightworks.render.PartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * Draws a signal lamp's lens with its pattern or brake brightness; the housing is the block model. Inside a
 * contraption the movement behaviour draws the lens instead.
 */
public class SignalLampRenderer implements BlockEntityRenderer<SignalLampBlockEntity> {
    public SignalLampRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SignalLampBlockEntity lamp, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (lamp.getLevel() instanceof VirtualRenderWorld) {
            return;
        }
        renderLens(poseStack, buffers, lamp.lightType(), lamp.facing(), lamp.rgb(), lamp.intensity(partialTick),
                packedLight);
    }

    static void renderLens(PoseStack poseStack, MultiBufferSource buffers, LightType type, Direction facing, int rgb,
            float intensity, int packedLight) {
        poseStack.pushPose();
        MountFrame.enter(poseStack, facing);
        MountFrame.toCorner(poseStack);
        PartRenderer.lens(poseStack, buffers, type.name() + "/lens", rgb, intensity, packedLight);
        poseStack.popPose();
    }
}
