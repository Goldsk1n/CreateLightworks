package com.goldskinmc.lightworks.content.searchlight;

import com.goldskinmc.lightworks.light.BeamVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws a placed Searchlight's cosmetic beam. On a Sable vehicle, Sable applies the vehicle's pose. */
public class SearchlightRenderer implements BlockEntityRenderer<SearchlightBlockEntity> {
    public SearchlightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SearchlightBlockEntity light, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        BeamVisual.render(poseStack, buffers, light.getFacing(), light.beam());
    }

    /** The beam reaches far outside the block; keep drawing it while any of it is on screen. */
    @Override
    public AABB getRenderBoundingBox(SearchlightBlockEntity light) {
        Vec3 lens = light.beamOrigin();
        return new AABB(light.getBlockPos()).minmax(new AABB(lens, lens.add(light.beamDirection()
                .scale(SearchlightBlockEntity.BEAM.range())))).inflate(SearchlightBlockEntity.BEAM.range() * 0.2);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
