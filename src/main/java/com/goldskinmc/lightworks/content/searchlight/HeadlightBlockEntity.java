package com.goldskinmc.lightworks.content.searchlight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class HeadlightBlockEntity extends SearchlightBlockEntity {
    public HeadlightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, HEADLIGHT_BEAM);
    }
}
