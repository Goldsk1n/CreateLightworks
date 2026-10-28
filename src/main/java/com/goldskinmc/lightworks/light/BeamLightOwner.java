package com.goldskinmc.lightworks.light;

import net.minecraft.core.BlockPos;

/** A block entity that places {@link BeamLightBlock}s where its beams land. */
public interface BeamLightOwner {
    /** True while one of this owner's beams keeps its light at {@code pos}. */
    boolean ownsBeamLight(BlockPos pos);
}
