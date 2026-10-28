package com.goldskinmc.lightworks.debug;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.searchlight.SearchlightBlockEntity;
import com.goldskinmc.lightworks.light.BeamTrace;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Spike debug view, shown while the F3 screen is open: each searchlight's beam as a line from lens to
 * hit point with a box around the hit block, plus details of the searchlight under the crosshair.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class BeamDebugRenderer {
    private static final double RANGE = 48;
    private static final double MAX_VIEW_DISTANCE_SQR = 96 * 96;

    private BeamDebugRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null
                || !mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (SearchlightBlockEntity light : TrackedLights.snapshot()) {
            if (light.getLevel() != mc.level || light.isRemoved()
                    || light.getBlockPos().distToCenterSqr(camera) > MAX_VIEW_DISTANCE_SQR) {
                continue;
            }
            Vec3 origin = light.beamOrigin();
            BeamTrace.Hit hit = BeamTrace.trace(mc.level, origin, light.beamDirection(), RANGE);
            line(poseStack, lines, origin, hit.end(), 1f, 0.85f, 0.2f);
            if (hit.block() != null) {
                LevelRenderer.renderLineBox(poseStack, lines, new AABB(hit.block()).inflate(0.002),
                        1f, 0.4f, 0.1f, 1f);
            }
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        event.getRight().add("");
        event.getRight().add("[Lightworks] searchlights tracked: " + TrackedLights.snapshot().size());
        if (mc.hitResult instanceof BlockHitResult target && target.getType() == HitResult.Type.BLOCK) {
            BlockEntity be = mc.level.getBlockEntity(target.getBlockPos());
            if (be instanceof SearchlightBlockEntity light) {
                Vec3 origin = light.beamOrigin();
                BeamTrace.Hit hit = BeamTrace.trace(mc.level, origin, light.beamDirection(), RANGE);
                event.getRight().add("Beam " + light.getFacing().getSerializedName() + " from " + format(origin));
                event.getRight().add(hit.block() == null
                        ? String.format("No hit within %.0f blocks", RANGE)
                        : String.format("Hits %s at %.2f blocks, block light %d", shortPos(hit.block()),
                                hit.distance(), lightInFrontOf(mc, hit, light.beamDirection())));
            }
        }
    }

    /** Block light in the air the beam passed through just before it hit: what S1 measures. */
    private static int lightInFrontOf(Minecraft mc, BeamTrace.Hit hit, Vec3 direction) {
        BlockPos air = BlockPos.containing(hit.end().subtract(direction.normalize().scale(0.5)));
        return mc.level.getBrightness(LightLayer.BLOCK, air);
    }

    private static void line(PoseStack poseStack, VertexConsumer lines, Vec3 from, Vec3 to,
            float r, float g, float b) {
        PoseStack.Pose pose = poseStack.last();
        Vec3 normal = to.subtract(from).normalize();
        lines.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(r, g, b, 1f).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        lines.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(r, g, b, 1f).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static String format(Vec3 v) {
        return String.format("%.2f, %.2f, %.2f", v.x, v.y, v.z);
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
