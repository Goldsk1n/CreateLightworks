package com.goldskinmc.lightworks.light;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** A block entity that places a {@link BeamLightBlock} where its beam lands. */
public interface BeamLightOwner {
    /** Where this owner's beam light currently is, or null if it has none. */
    @Nullable
    BlockPos beamLightPos();
}
