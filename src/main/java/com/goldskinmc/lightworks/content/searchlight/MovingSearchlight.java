package com.goldskinmc.lightworks.content.searchlight;

import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.host.ContraptionHost;
import com.goldskinmc.lightworks.light.BeamSource;
import com.goldskinmc.lightworks.light.ClientBeam;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.Direction;

/** Client side of a Searchlight on a contraption, kept in the actor's {@code temporaryData}. */
final class MovingSearchlight implements BeamSource {
    private final MovementContext context;
    private final ClientBeam beam = new ClientBeam(SearchlightBlockEntity.BEAM);

    private MovingSearchlight(MovementContext context) {
        this.context = context;
    }

    static MovingSearchlight of(MovementContext context) {
        if (context.temporaryData instanceof MovingSearchlight light) {
            return light;
        }
        MovingSearchlight light = new MovingSearchlight(context);
        context.temporaryData = light;
        ClientBeams.add(light);
        return light;
    }

    Direction localFacing() {
        return context.state.getValue(SearchlightBlock.FACING);
    }

    void tick() {
        if (context.disabled) {
            beam.off();
        } else {
            beam.update(context.world, tickBeam());
        }
    }

    @Override
    public BeamPose tickBeam() {
        return ContraptionHost.toWorld(context, localFacing());
    }

    @Override
    public BeamPose renderBeam(float partialTick) {
        return ContraptionHost.toWorldForRender(context, localFacing(), partialTick);
    }

    @Override
    public ClientBeam beam() {
        return beam;
    }

    /** The contraption entity was discarded or unloaded without a stopMoving call. */
    @Override
    public boolean isGone() {
        return context.contraption.entity == null || context.contraption.entity.isRemoved()
                || context.contraption.entity.level() != context.world;
    }
}
