package com.goldskinmc.lightworks.content.signal;

import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.goldskinmc.lightworks.light.ProjectedTint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * A signal lamp carried by a contraption: its lens shows, and it colours the blocks round it like a placed
 * lamp ({@link ProjectedTint}, found again whenever it has moved half a block; no dynamic light, which would
 * rebuild chunks). Warning patterns run off game time; a Tail and Brake Light watches the contraption's
 * motion and brightens for a second when it slows or stops. Contraption Controls switch the lamp off.
 */
public class SignalLampMovement implements MovementBehaviour {
    private final LightType type;

    public SignalLampMovement(LightType type) {
        this.type = type;
    }

    /** Client state, kept in the actor's {@code temporaryData}. */
    /** The glow's faces are found again once the lamp is this far (blocks) from where they were found. */
    private static final double GLOW_MOVE = 1;
    /** A moving lamp's glow reaches at most this far: finding the faces is the costly part. */
    private static final double MAX_MOVING_RADIUS = 4;
    /** Lamps further from the camera than this colour nothing (the tint would not show). */
    private static final double GLOW_DISTANCE = 48;

    private static final class Moving {
        final LightState state;
        final BrakeLight brake = new BrakeLight();
        final ProjectedTint glow = new ProjectedTint();
        @Nullable
        Vec3 glowAt;
        int glowRgb;

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

    /** True while a moving Tail and Brake Light shows its brake light (client). */
    public static boolean braking(MovementContext context) {
        return context.temporaryData instanceof Moving moving && moving.brake.braking();
    }

    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide) {
            Moving moving = moving(context);
            moving.brake.tick(context.motion);
            updateGlow(context, moving);
        }
    }

    /** Client: colours the blocks round the lamp where the contraption has it this tick. */
    private void updateGlow(MovementContext context, Moving moving) {
        Vec3 at = context.position;
        if (at == null || type.placedLight() <= 0 || !ClientBeams.isRealWorld(context.world)
                || Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(at)
                        > GLOW_DISTANCE * GLOW_DISTANCE) {
            moving.glow.clear();
            moving.glowAt = null;
            return;
        }
        float intensity = context.disabled ? 0 : SignalLampBlockEntity.intensity(type, moving.state.brightness(),
                WarningPattern.of(moving.state.pattern()), moving.brake.braking(), context.world.getGameTime(), 0);
        int rgb = rgb(context, moving);
        if (moving.glowAt == null || moving.glowAt.distanceToSqr(at) > GLOW_MOVE * GLOW_MOVE || rgb != moving.glowRgb) {
            moving.glow.glow(context.world, at, Math.min(MAX_MOVING_RADIUS, SignalLampBlockEntity.glowRadius(type)), rgb,
                    intensity);
            moving.glowAt = at;
            moving.glowRgb = rgb;
        } else {
            moving.glow.setBrightness(intensity);
        }
        moving.glow.keepAlive(context.world);
    }

    private int rgb(MovementContext context, Moving moving) {
        return context.state.hasProperty(NavigationLightBlock.COLOUR)
                ? context.state.getValue(NavigationLightBlock.COLOUR).rgb() : moving.state.rgb(type);
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
                WarningPattern.of(moving.state.pattern()), moving.brake.braking(), context.world.getGameTime(),
                partialTick);
        int rgb = rgb(context, moving);
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
