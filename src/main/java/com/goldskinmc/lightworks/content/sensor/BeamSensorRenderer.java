package com.goldskinmc.lightworks.content.sensor;

import com.goldskinmc.lightworks.render.MountFrame;
import com.goldskinmc.lightworks.render.PartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.DyeColor;

/**
 * Draws a Beam Sensor's lens in its dye's colour (clear glass undyed), lit while the sensor gives a signal; the housing is
 * the block model.
 */
public class BeamSensorRenderer implements BlockEntityRenderer<BeamSensorBlockEntity> {
    /** An undyed lens: pale glass. */
    private static final int CLEAR = 0xDDE8EC;

    public BeamSensorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BeamSensorBlockEntity sensor, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        if (sensor.getLevel() instanceof VirtualRenderWorld) {
            return;
        }
        DyeColor colour = sensor.lightState().colour();
        int rgb = colour == null ? CLEAR : colour.getTextureDiffuseColor() & 0xFFFFFF;
        int power = sensor.getBlockState().getValue(BeamSensorBlock.POWER);
        poseStack.pushPose();
        MountFrame.enter(poseStack, sensor.getBlockState().getValue(BeamSensorBlock.FACING));
        MountFrame.toCorner(poseStack);
        PartRenderer.lens(poseStack, buffers, "beam_sensor/lens", rgb, power > 0 ? 0.4f + 0.6f * power / 15f : 0,
                packedLight);
        poseStack.popPose();
    }
}
