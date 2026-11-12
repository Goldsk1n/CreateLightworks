package com.goldskinmc.lightworks.content;

import net.minecraft.world.level.Level;

/**
 * Whether our value boards (Pan, Tilt, a head's sweep, a Warning Light's pattern) are shown and take clicks. With the
 * client config {@code boardsOnlyWithWrench} on, they do only while the player holds a Wrench (or a Clipboard, which
 * pastes into them), as Create's own {@code requiresWrench} boards do. Client side only: the server always answers
 * yes, and a click the client keeps for the block itself never reaches a board there.
 */
public final class BoardGate {
    private BoardGate() {
    }

    public static boolean open(Level level) {
        return level == null || !level.isClientSide || com.goldskinmc.lightworks.render.ClientBoards.open();
    }
}
