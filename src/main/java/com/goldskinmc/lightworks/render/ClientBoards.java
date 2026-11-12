package com.goldskinmc.lightworks.render;

import com.goldskinmc.lightworks.LightworksClientConfig;
import com.simibubi.create.AllBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;

/** Client half of {@link com.goldskinmc.lightworks.content.BoardGate}: reads the config and the local player's hand. */
public final class ClientBoards {
    private ClientBoards() {
    }

    public static boolean open() {
        if (!LightworksClientConfig.boardsOnlyWithWrench()) {
            return true;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return true;
        }
        ItemStack held = player.getMainHandItem();
        return held.is(Tags.Items.TOOLS_WRENCH) || AllBlocks.CLIPBOARD.isIn(held);
    }
}
