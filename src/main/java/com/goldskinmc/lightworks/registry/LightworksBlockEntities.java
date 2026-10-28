package com.goldskinmc.lightworks.registry;

import static com.goldskinmc.lightworks.Lightworks.REGISTRATE;

import com.goldskinmc.lightworks.content.searchlight.SearchlightBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

public final class LightworksBlockEntities {
    public static final BlockEntityEntry<SearchlightBlockEntity> SEARCHLIGHT = REGISTRATE
            .blockEntity("searchlight", SearchlightBlockEntity::new)
            .validBlocks(LightworksBlocks.SEARCHLIGHT)
            .register();

    private LightworksBlockEntities() {
    }

    /** Loads this class so its entries are registered. */
    public static void register() {
    }
}
