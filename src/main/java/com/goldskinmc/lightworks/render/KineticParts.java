package com.goldskinmc.lightworks.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;

/**
 * Create's own moving parts on our kinetic blocks, turned the way Create turns them, so they line up with
 * the shafts next to them. Drawn by our block entity renderers: we register no Flywheel visuals.
 */
public final class KineticParts {
    private KineticParts() {
    }

    /** Create's half shaft from the block centre out through {@code side}, turning with the block's speed. */
    public static void shaftHalf(KineticBlockEntity be, Direction side, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight) {
        KineticBlockEntityRenderer.standardKineticRotationTransform(
                        CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(), side), be, packedLight)
                .renderInto(poseStack, buffers.getBuffer(RenderType.solid()));
    }
}
