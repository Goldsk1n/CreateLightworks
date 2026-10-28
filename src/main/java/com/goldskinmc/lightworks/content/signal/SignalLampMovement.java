package com.goldskinmc.lightworks.content.signal;

import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.content.LightType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A signal lamp carried by a contraption: only its lens shows (no dynamic light). Warning patterns run off
 * game time; a Tail and Brake Light watches the contraption's motion and brightens for a second when it
 * slows or stops. Contraption Controls switch the lamp off.
 */
public class SignalLampMovement implements MovementBehaviour {
    private final LightType type;

    public SignalLampMovement(LightType type) {
        this.type = type;
    }

    /** Client state, kept in the actor's {@code temporaryData}. */
    private static final class Moving {
        final LightState state;
        final BrakeLight brake = new BrakeLight();

        Moving(LightState state) {
            this.state = state;
        }
    }

    private static Moving moving(MovementContext context) {
        if (context.temporaryData instanceof Moving moving) {
            return moving;
        }
        Moving moving = new Moving(LightState.of(context.blockEntityData));
        context.temporaryData = moving;
        return moving;
    }

    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide) {
            moving(context).brake.tick(context.motion);
        }
    }

    /** Also called when a piston or bearing reverses: a stop, so the brake light shows. */
    @Override
    public void stopMoving(MovementContext context) {
        if (context.world.isClientSide) {
            moving(context).brake.stopped();
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
            ContraptionMatrices matrices, MultiBufferSource buffer) {
        Moving moving = moving(context);
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float intensity = context.disabled ? 0 : SignalLampBlockEntity.intensity(type, moving.state.brightness(),
                WarningPattern.of(moving.state.pattern()), moving.brake.braking(), context.world, partialTick);
        int rgb = context.state.hasProperty(NavigationLightBlock.COLOUR)
                ? context.state.getValue(NavigationLightBlock.COLOUR).rgb() : moving.state.rgb(type);
        int worldLight = context.position == null ? LightTexture.FULL_BRIGHT
                : LevelRenderer.getLightColor(context.world, BlockPos.containing(context.position));
        PoseStack poseStack = matrices.getViewProjection();
        poseStack.pushPose();
        poseStack.last().pose().mul(matrices.getModel().last().pose());
        SignalLampRenderer.renderLens(poseStack, buffer, type, context.state.getValue(SignalLampBlock.FACING), rgb,
                intensity, worldLight);
        poseStack.popPose();
    }
}
