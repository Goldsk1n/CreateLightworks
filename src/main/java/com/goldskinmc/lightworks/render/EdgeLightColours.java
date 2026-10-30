package com.goldskinmc.lightworks.render;

import com.goldskinmc.lightworks.content.signal.EdgeLightBlock;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Tints an Edge Light's lens from its block state: the dye, dimmed by the brightness. */
public final class EdgeLightColours {
    /** How much of its colour a lens keeps when off, like {@code PartRenderer}'s unlit lenses. */
    private static final float UNLIT = 0.3f;

    private EdgeLightColours() {
    }

    public static void register(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? colour(state) : -1,
                LightworksBlocks.EDGE_LIGHT.get());
    }

    private static int colour(BlockState state) {
        int rgb = state.getValue(EdgeLightBlock.COLOUR).getTextureDiffuseColor();
        float k = UNLIT + (1 - UNLIT) * state.getValue(EdgeLightBlock.BRIGHTNESS) / 15;
        int red = Math.round((rgb >> 16 & 0xFF) * k);
        int green = Math.round((rgb >> 8 & 0xFF) * k);
        int blue = Math.round((rgb & 0xFF) * k);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
