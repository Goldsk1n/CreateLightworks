package com.goldskinmc.lightworks.registry;

import static com.goldskinmc.lightworks.Lightworks.REGISTRATE;

import com.goldskinmc.lightworks.content.searchlight.SearchlightBlockEntity;
import com.goldskinmc.lightworks.content.searchlight.SearchlightRenderer;
import com.goldskinmc.lightworks.light.BeamLightBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

public final class LightworksBlockEntities {
    public static final BlockEntityEntry<SearchlightBlockEntity> SEARCHLIGHT = REGISTRATE
            .blockEntity("searchlight", SearchlightBlockEntity::new)
            .validBlocks(LightworksBlocks.SEARCHLIGHT)
            .renderer(() -> SearchlightRenderer::new)
            .register();

    public static final BlockEntityEntry<BeamLightBlockEntity> BEAM_LIGHT = REGISTRATE
            .<BeamLightBlockEntity>blockEntity("beam_light", (type, pos, state) -> new BeamLightBlockEntity(pos, state))
            .validBlocks(LightworksBlocks.BEAM_LIGHT)
            .register();

    private LightworksBlockEntities() {
    }

    /** Loads this class so its entries are registered. */
    public static void register() {
    }
}
