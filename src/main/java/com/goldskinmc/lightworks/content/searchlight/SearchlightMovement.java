package com.goldskinmc.lightworks.content.searchlight;

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
 * A Searchlight carried by a contraption. Light blocks cannot keep up with moving blocks, so on a
 * contraption the Searchlight only has the client dynamic light and the cosmetic beam.
 */
public class SearchlightMovement implements MovementBehaviour {
    /** Keep ticking while Contraption Controls disable it, to switch the light off. */
    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide) {
            MovingSearchlight.of(context).tick();
        }
    }

    @Override
    public void stopMoving(MovementContext context) {
        if (context.world.isClientSide && context.temporaryData instanceof MovingSearchlight light) {
            ClientBeams.remove(light);
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
