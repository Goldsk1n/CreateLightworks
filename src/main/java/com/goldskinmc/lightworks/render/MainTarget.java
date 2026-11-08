package com.goldskinmc.lightworks.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;

/**
 * Where the visible beam, the lens glow and the colour layers are drawn: the main picture. Fabulous graphics draws
 * see-through things into targets of their own and merges them by depth at the end of the frame. The beam writes no
 * depth, so in the weather target, as vanilla's lightning has it, the merge put it behind everything and it showed only
 * against the sky; and the colour layers, drawn after particles, landed on the empty particles target and came out black.
 * Without Fabulous (and under shader packs, which turn it off) the main picture is bound already and this does nothing.
 */
public final class MainTarget {
    public static final RenderStateShard.OutputStateShard OUTPUT = new RenderStateShard.OutputStateShard(
            "lightworks_main_target", MainTarget::bind, MainTarget::bind);

    private MainTarget() {
    }

    private static void bind() {
        if (Minecraft.useShaderTransparency()) {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        }
    }
}
