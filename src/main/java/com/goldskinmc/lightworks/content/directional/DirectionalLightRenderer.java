package com.goldskinmc.lightworks.content.directional;

import com.goldskinmc.lightworks.light.BeamTrace;
import com.goldskinmc.lightworks.light.BeamVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws a placed directional light's cosmetic beam. On a Sable vehicle, Sable applies the vehicle's pose.
 * Inside a contraption the block entity copy never traces (its beam length is 0), and the beam comes from
 * the movement behaviour instead.
 */
public class DirectionalLightRenderer implements BlockEntityRenderer<DirectionalLightBlockEntity> {
    public DirectionalLightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DirectionalLightBlockEntity light, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Vec3 direction = light.beamDirection();
        Vector3f axis = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        Vector3f lens = new Vector3f(0.5f).add(new Vector3f(axis).mul((float) DirectionalLightBlockEntity.LENS_RADIUS));
        BeamVisual.render(poseStack, buffers, lens, axis, light.beam(), light.lightState().rgb(light.lightType()));
    }

    /** The beam reaches far outside the block; keep drawing it while any of it is on screen. */
    @Override
    public AABB getRenderBoundingBox(DirectionalLightBlockEntity light) {
        Vec3 lens = light.beamOrigin();
        double range = BeamTrace.range(light.beam().shape());
        return new AABB(light.getBlockPos()).minmax(new AABB(lens, lens.add(light.beamDirection().scale(range))))
                .inflate(range * 0.2);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
