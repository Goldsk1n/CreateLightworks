package com.goldskinmc.lightworks.content.searchlight;

import com.goldskinmc.lightworks.registry.LightworksBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * A short, wide beam for vehicles. Placed, it works like a Searchlight; on a contraption it lights only
 * while moving the way it points, so one at each end of a train swaps when the train reverses.
 */
public class HeadlightBlock extends SearchlightBlock {
    public static final MapCodec<HeadlightBlock> CODEC = simpleCodec(HeadlightBlock::new);

    public HeadlightBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntityType<? extends SearchlightBlockEntity> getBlockEntityType() {
        return LightworksBlockEntities.HEADLIGHT.get();
    }
}
