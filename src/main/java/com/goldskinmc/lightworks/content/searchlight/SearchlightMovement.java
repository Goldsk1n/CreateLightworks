package com.goldskinmc.lightworks.content.searchlight;

import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.light.BeamVisual;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A Searchlight or Headlight carried by a contraption. Light blocks cannot keep up with moving blocks, so
 * on a contraption the light only has the client dynamic light and the cosmetic beam.
 */
public class SearchlightMovement implements MovementBehaviour {
    private final ConeFalloff shape;
    private final boolean followsMotion;

    /**
     * @param followsMotion headlight behaviour: lit only while the contraption moves the way the light
     *                      points, so lights at both ends of a train swap when it reverses
     */
    public SearchlightMovement(ConeFalloff shape, boolean followsMotion) {
        this.shape = shape;
        this.followsMotion = followsMotion;
    }

    /** Keep ticking while Contraption Controls disable it, to switch the light off. */
    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide) {
            MovingSearchlight.of(context, shape, followsMotion).tick();
        }
    }

    @Override
    public void stopMoving(MovementContext context) {
        // Also called when a piston or bearing reverses; the next tick starts a fresh light
        if (context.world.isClientSide && context.temporaryData instanceof MovingSearchlight light) {
            ClientBeams.remove(light);
            context.temporaryData = null;
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
            ContraptionMatrices matrices, MultiBufferSource buffer) {
        if (context.temporaryData instanceof MovingSearchlight light) {
            BeamVisual.renderInContraption(matrices, buffer, light.localFacing(), light.beam());
        }
    }
}
