package com.goldskinmc.lightworks.content;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.light.BeamSource;
import com.goldskinmc.lightworks.light.BeamVisual;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the beams of lights on contraptions, trains and turning heads' contraptions in the world, each frame, from where
 * they are this frame. Inside the contraption's own renderer they vanished whenever Minecraft judged the contraption out
 * of view, though its beams reached into it (looking down at a lit floor with the lamps above the screen). Drawn after
 * block entities, as fixed lights' beams are, and at once.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class MovingBeamPass {
    private MovingBeamPass() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || mc.level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (BeamSource source : ClientBeams.snapshot()) {
            if (source instanceof MovingBeam moving && moving.level() == mc.level && !moving.isGone()
                    && moving.beam().length() > 0) {
                BeamPose pose = moving.renderBeam(partialTick);
                BeamVisual.renderInWorld(poseStack, buffers, camera, pose.origin(), pose.direction(), moving.beam(),
                        moving.rgb());
                drew = true;
            }
        }
        if (drew) {
            BeamVisual.flush(buffers);
        }
    }
}
