package com.goldskinmc.lightworks.content.directional;

import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.content.MovingBeam;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A directional light carried by a contraption. Light blocks cannot keep up with moving blocks, so on a
 * contraption the light only has the client dynamic light and the cosmetic beam. A Headlight lights
 * only while the contraption moves the way it points, so lights at both ends of a train swap when it
 * reverses.
 */
public class DirectionalLightMovement implements MovementBehaviour {
    private final LightType type;

    public DirectionalLightMovement(LightType type) {
        this.type = type;
    }

    /** Keep ticking while Contraption Controls disable it, to switch the light off. */
    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide) {
            moving(context).tick();
        }
    }

    /** This actor's beam, made on its first client tick from the block entity's data at assembly. */
    private MovingBeam moving(MovementContext context) {
        if (context.temporaryData instanceof MovingBeam beam) {
            return beam;
        }
        CompoundTag data = context.blockEntityData;
        LightState state = LightState.of(data);
        Direction facing = context.state.getValue(DirectionalLightBlock.FACING);
        // On a Pan-Tilt Head the head's angles at assembly win; heads do not turn on contraptions
        boolean onHead = data != null && data.contains(DirectionalLightBlockEntity.HEAD_ELEVATION);
        float yaw = onHead ? data.getFloat(DirectionalLightBlockEntity.HEAD_YAW) : state.aim().yaw();
        float elevation = onHead ? data.getFloat(DirectionalLightBlockEntity.HEAD_ELEVATION) : state.aim().elevation();
        MovingBeam beam = new MovingBeam(context, type, state, facing, yaw, elevation);
        context.temporaryData = beam;
        ClientBeams.add(beam);
        return beam;
    }

    @Override
    public void stopMoving(MovementContext context) {
        // Also called when a piston or bearing reverses; the next tick starts a fresh light
        if (context.world.isClientSide && context.temporaryData instanceof MovingBeam beam) {
            ClientBeams.remove(beam);
            context.temporaryData = null;
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
            ContraptionMatrices matrices, MultiBufferSource buffer) {
        if (context.temporaryData instanceof MovingBeam light) {
            PoseStack poseStack = matrices.getViewProjection();
            poseStack.pushPose();
            poseStack.last().pose().mul(matrices.getModel().last().pose());
            int worldLight = context.position == null ? 0
                    : LevelRenderer.getLightColor(context.world, BlockPos.containing(context.position));
            DirectionalLightRenderer.renderLens(poseStack, buffer, light.type(), light.facing(), light.yaw(),
                    light.elevation(), light.rgb(), light.beam().brightness() / 15f, worldLight);
            poseStack.popPose();
            // The beam itself is drawn in the world (MovingBeamPass): this is skipped while the contraption is out of view
        }
    }
}
