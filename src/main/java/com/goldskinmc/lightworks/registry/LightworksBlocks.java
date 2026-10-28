package com.goldskinmc.lightworks.registry;

import static com.goldskinmc.lightworks.Lightworks.REGISTRATE;

import com.goldskinmc.lightworks.content.searchlight.SearchlightBlock;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;

public final class LightworksBlocks {
    public static final BlockEntry<SearchlightBlock> SEARCHLIGHT = REGISTRATE
            .block("searchlight", SearchlightBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f).sound(SoundType.COPPER).noOcclusion())
            // directionalBlock rotates an up-facing model, so the lens goes on the top face
            .blockstate((c, p) -> p.directionalBlock(c.get(), p.models().orientableVertical(c.getName(),
                    p.modLoc("block/searchlight_side"),
                    p.modLoc("block/searchlight_front"))))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Searchlight")
            .simpleItem()
            .register();

    private LightworksBlocks() {
    }

    /** Loads this class so its entries are registered. */
    public static void register() {
    }
}
