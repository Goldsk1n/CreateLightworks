package com.goldskinmc.lightworks.content.beacon;

import com.goldskinmc.lightworks.light.BeamTrace;
import com.goldskinmc.lightworks.light.BeamVisual;
import com.goldskinmc.lightworks.render.KineticParts;
import com.goldskinmc.lightworks.render.MountFrame;
import com.goldskinmc.lightworks.render.PartRenderer;
import com.goldskinmc.lightworks.render.Partials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws the input shaft, the rotor at its angle, its two lenses and, for a placed beacon, both cosmetic
 * beams. Inside a contraption lenses and beams come from the movement behaviour.
 */
public class RotatingBeaconRenderer implements BlockEntityRenderer<RotatingBeaconBlockEntity> {
    public RotatingBeaconRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RotatingBeaconBlockEntity beacon, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        boolean inContraption = beacon.getLevel() instanceof VirtualRenderWorld;
        int rgb = beacon.lightState().rgb(beacon.lightType());
        float lit = inContraption ? -1 : beacon.beams()[0].beam().brightness() / 15f;
        KineticParts.shaftHalf(beacon, beacon.facing().getOpposite(), poseStack, buffers, packedLight);
        renderRotor(poseStack, buffers, beacon.facing(), beacon.angle(partialTick), rgb, lit, packedLight, true);
        if (inContraption) {
            return;
        }
        for (RotatingBeaconBlockEntity.Beam beam : beacon.beams()) {
            Vec3 d = beam.direction(partialTick);
            Vector3f axis = new Vector3f((float) d.x, (float) d.y, (float) d.z);
            Vector3f lens = new Vector3f(0.5f).add(new Vector3f(axis).mul((float) beacon.lightType().lensDistance()));
            BeamVisual.render(poseStack, buffers, lens, axis, beam.beam(), rgb);
        }
    }

    /**
     * Rotor and lenses at {@code angle}.
     *
     * @param lit   lens brightness 0 to 1, or negative to leave the lenses out
     * @param rotor false to draw only the lenses (contraptions, where the block entity copy draws the rotor)
     */
    static void renderRotor(PoseStack poseStack, MultiBufferSource buffers, Direction facing, float angle, int rgb,
            float lit, int packedLight, boolean rotor) {
        poseStack.pushPose();
        MountFrame.enter(poseStack, facing);
        MountFrame.yaw(poseStack, angle);
        MountFrame.toCorner(poseStack);
        if (rotor) {
            PartRenderer.solid(poseStack, buffers, Partials.get("rotating_beacon/rotor"), packedLight);
        }
        if (lit >= 0) {
            PartRenderer.lens(poseStack, buffers, "rotating_beacon/lens", rgb, lit, packedLight);
        }
        poseStack.popPose();
    }

    /** Its beams reach far outside the block: drawn while they are on screen, as the Searchlight's are. */
    @Override
    public boolean shouldRenderOffScreen(RotatingBeaconBlockEntity beacon) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(RotatingBeaconBlockEntity beacon) {
        Vec3 centre = Vec3.atCenterOf(beacon.getBlockPos());
        return new AABB(centre, centre).inflate(BeamTrace.range(beacon.beams()[0].beam().shape()));
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
