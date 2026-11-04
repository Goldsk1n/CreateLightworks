package com.goldskinmc.lightworks.content.directional;

import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.debug.Timer;
import com.goldskinmc.lightworks.light.BeamTrace;
import com.goldskinmc.lightworks.light.BeamVisual;
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
 * Draws a directional light's moving parts: the yoke turned by the aim's yaw, the head and lens turned by
 * yaw and elevation (the base is the block model, except on a Pan-Tilt Head, where it turns with the rest). A placed light also draws its lens glow and cosmetic
 * beam; on a Sable vehicle, Sable applies the vehicle's pose. Inside a contraption Create draws this
 * renderer for the block entity copy, which shows the head; lens and beam come from the movement
 * behaviour there, which knows whether the light is lit.
 */
public class DirectionalLightRenderer implements BlockEntityRenderer<DirectionalLightBlockEntity> {
    public DirectionalLightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DirectionalLightBlockEntity light, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float[] aim = light.aimAngles(partialTick);
        boolean inContraption = light.getLevel() instanceof VirtualRenderWorld;
        float lit = inContraption ? -1 : light.beam().brightness() / 15f;
        if (light.getBlockState().getValue(DirectionalLightBlock.ON_HEAD)) {
            renderBase(poseStack, buffers, light.lightType(), light.mountFacing(), aim[0], packedLight);
        }
        renderHead(poseStack, buffers, light.lightType(), light.mountFacing(), aim[0], aim[1],
                light.lightState().rgb(light.lightType()), lit, packedLight);
        if (inContraption) {
            return;
        }
        long start = Timer.start();
        Vec3 direction = light.beamDirection(partialTick);
        Vector3f axis = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        Vector3f lens = new Vector3f(0.5f).add(new Vector3f(axis).mul((float) light.lightType().lensDistance()));
        BeamVisual.render(poseStack, buffers, lens, axis, light.beam(), light.lightState().rgb(light.lightType()));
        Timer.LIGHT_RENDER.stop(start);
    }

    /** The base of a light on a Pan-Tilt Head, bolted to the turntable; on anything else it is the block model. */
    private static void renderBase(PoseStack poseStack, MultiBufferSource buffers, LightType type, Direction facing,
            float yaw, int packedLight) {
        poseStack.pushPose();
        MountFrame.enter(poseStack, facing);
        MountFrame.yaw(poseStack, yaw);
        MountFrame.toCorner(poseStack);
        PartRenderer.solid(poseStack, buffers, Partials.get(type.name() + "/base"), packedLight);
        poseStack.popPose();
    }

    /**
     * Yoke, head and lens at the given aim, in block coordinates.
     *
     * @param lit lens brightness 0 to 1, or negative to leave the lens out
     */
    static void renderHead(PoseStack poseStack, MultiBufferSource buffers, LightType type, Direction facing, float yaw,
            float elevation, int rgb, float lit, int packedLight) {
        String name = type.name();
        poseStack.pushPose();
        MountFrame.enter(poseStack, facing);
        MountFrame.yaw(poseStack, yaw);
        if (Partials.has(name + "/yoke")) {
            poseStack.pushPose();
            MountFrame.toCorner(poseStack);
            PartRenderer.solid(poseStack, buffers, Partials.get(name + "/yoke"), packedLight);
            poseStack.popPose();
        }
        MountFrame.tilt(poseStack, elevation);
        MountFrame.toCorner(poseStack);
        PartRenderer.solid(poseStack, buffers, Partials.get(name + "/head"), packedLight);
        if (lit >= 0) {
            PartRenderer.lens(poseStack, buffers, name + "/lens", rgb, lit, packedLight);
        }
        poseStack.popPose();
    }

    /** Lens only, for lights drawn inside a contraption (the head comes from the block entity copy). */
    static void renderLens(PoseStack poseStack, MultiBufferSource buffers, LightType type, Direction facing, float yaw,
            float elevation, int rgb, float lit, int packedLight) {
        poseStack.pushPose();
        MountFrame.enter(poseStack, facing);
        MountFrame.yaw(poseStack, yaw);
        MountFrame.tilt(poseStack, elevation);
        MountFrame.toCorner(poseStack);
        PartRenderer.lens(poseStack, buffers, type.name() + "/lens", rgb, lit, packedLight);
        poseStack.popPose();
    }

    /**
     * Drawn while the beam is on screen, not only while the lamp's own section is: Sodium draws a block entity only with
     * its section, and Entity Culling only while the block itself can be seen, unless asked like this, as the vanilla
     * beacon does. Looking along a beam with the lamp out of sight, the beam went with it.
     */
    @Override
    public boolean shouldRenderOffScreen(DirectionalLightBlockEntity light) {
        return true;
    }

    /** The beam reaches far outside the block; keep drawing it while any of it is on screen. */
    @Override
    public AABB getRenderBoundingBox(DirectionalLightBlockEntity light) {
        double range = BeamTrace.range(light.beam().shape());
        Vec3 centre = Vec3.atCenterOf(light.getBlockPos());
        if (light.head() != null) {
            // A head can turn the beam anywhere in front of it
            return new AABB(centre, centre).inflate(range);
        }
        Vec3 lens = light.beamOrigin();
        return new AABB(light.getBlockPos()).minmax(new AABB(lens, lens.add(light.beamDirection().scale(range))))
                .inflate(range * 0.2);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
