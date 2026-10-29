package com.goldskinmc.lightworks.content.pantilt;

import com.goldskinmc.lightworks.render.KineticParts;
import com.goldskinmc.lightworks.render.MountFrame;
import com.goldskinmc.lightworks.render.PartRenderer;
import com.goldskinmc.lightworks.render.Partials;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Draws the input shaft and the head's turntable at its pan angle; the housing is the block model. */
public class PanTiltHeadRenderer implements BlockEntityRenderer<PanTiltHeadBlockEntity> {
    public PanTiltHeadRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PanTiltHeadBlockEntity head, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        KineticParts.shaftHalf(head, head.facing().getOpposite(), poseStack, buffers, packedLight);
        poseStack.pushPose();
        MountFrame.enter(poseStack, head.facing());
        MountFrame.yaw(poseStack, head.yaw(partialTick));
        MountFrame.toCorner(poseStack);
        PartRenderer.solid(poseStack, buffers, Partials.get("pan_tilt_head/turntable"), packedLight);
        poseStack.popPose();
    }
}
