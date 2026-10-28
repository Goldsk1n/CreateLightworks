package com.goldskinmc.lightworks.content.beacon;

import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.content.MovingBeam;
import com.goldskinmc.lightworks.light.BeamVisual;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A Rotating Beacon carried by a contraption. Contraptions do not run kinetics, so the rotor stays at the
 * angle it had when the contraption assembled and its two beams ride along like Searchlights.
 */
public class RotatingBeaconMovement implements MovementBehaviour {
    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    @Override
    public void tick(MovementContext context) {
        if (!context.world.isClientSide) {
            return;
        }
        MovingBeam[] beams;
        if (context.temporaryData instanceof MovingBeam[] existing) {
            beams = existing;
        } else {
            CompoundTag data = context.blockEntityData;
            LightState state = LightState.of(data);
            Direction facing = context.state.getValue(RotatingBeaconBlock.FACING);
            float angle = data == null ? 0 : data.getFloat(RotatingBeaconBlockEntity.ANGLE);
            beams = new MovingBeam[] {
                    new MovingBeam(context, LightType.ROTATING_BEACON, state, facing, angle, RotatingBeaconBlockEntity.ELEVATION),
                    new MovingBeam(context, LightType.ROTATING_BEACON, state, facing, angle + 180,
                            RotatingBeaconBlockEntity.ELEVATION)};
            context.temporaryData = beams;
            for (MovingBeam beam : beams) {
                ClientBeams.add(beam);
            }
        }
        for (MovingBeam beam : beams) {
            beam.tick();
        }
    }

    @Override
    public void stopMoving(MovementContext context) {
        if (context.world.isClientSide && context.temporaryData instanceof MovingBeam[] beams) {
            for (MovingBeam beam : beams) {
                ClientBeams.remove(beam);
            }
            context.temporaryData = null;
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
            ContraptionMatrices matrices, MultiBufferSource buffer) {
        if (!(context.temporaryData instanceof MovingBeam[] beams)) {
            return;
        }
        MovingBeam first = beams[0];
        PoseStack poseStack = matrices.getViewProjection();
        poseStack.pushPose();
        poseStack.last().pose().mul(matrices.getModel().last().pose());
        int worldLight = context.position == null ? 0
                : LevelRenderer.getLightColor(context.world, BlockPos.containing(context.position));
        RotatingBeaconRenderer.renderRotor(poseStack, buffer, first.facing(), first.yaw(), first.rgb(),
                first.beam().brightness() / 15f, worldLight, false);
        poseStack.popPose();
        for (MovingBeam beam : beams) {
            BeamVisual.renderInContraption(matrices, buffer, beam.localLens(), beam.localAxis(), beam.beam(), beam.rgb());
        }
    }
}
