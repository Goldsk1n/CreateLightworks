package com.goldskinmc.lightworks.registry;

import static com.goldskinmc.lightworks.Lightworks.REGISTRATE;

import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.content.directional.DirectionalLightRenderer;
import com.goldskinmc.lightworks.light.BeamLightBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;

public final class LightworksBlockEntities {
    public static final BlockEntityEntry<DirectionalLightBlockEntity> SEARCHLIGHT = directional("searchlight",
            LightworksBlocks.SEARCHLIGHT);
    public static final BlockEntityEntry<DirectionalLightBlockEntity> HEADLIGHT = directional("headlight",
            LightworksBlocks.HEADLIGHT);

    public static final BlockEntityEntry<BeamLightBlockEntity> BEAM_LIGHT = REGISTRATE
            .<BeamLightBlockEntity>blockEntity("beam_light", (type, pos, state) -> new BeamLightBlockEntity(pos, state))
            .validBlocks(LightworksBlocks.BEAM_LIGHT)
            .register();

    private LightworksBlockEntities() {
    }

    /** One block entity type per directional light block, all with the same class and renderer. */
    private static BlockEntityEntry<DirectionalLightBlockEntity> directional(String name,
            BlockEntry<? extends DirectionalLightBlock> block) {
        return REGISTRATE.blockEntity(name, DirectionalLightBlockEntity::new)
                .validBlocks(block)
                .renderer(() -> DirectionalLightRenderer::new)
                .register();
    }

    /** Loads this class so its entries are registered. */
    public static void register() {
    }
}
