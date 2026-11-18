package com.goldskinmc.lightworks.content.carried;

import net.minecraft.world.InteractionHand;

/**
 * The thumb press on the Flashlight's switch, seen in first person when the local player turns it on or off: the lamp
 * tips its lens down slightly and comes back, rather than the punch swing a plain use would play. Written by
 * {@link FlashlightItem#use} on the client and read by the hand renderer ({@code render/FlashlightHand}); no client
 * classes here, since the item is loaded on servers too.
 */
public final class FlashlightPress {
    /** Ticks from pressing to back at rest. */
    public static final float TICKS = 4;
    /** Ticks into the press when the lamp is lowest: pressed down quickly, let back up slowly. */
    private static final float DOWN = 1.2f;

    private static InteractionHand hand = InteractionHand.MAIN_HAND;
    private static long pressedAt = Long.MIN_VALUE / 2;

    private FlashlightPress() {
    }

    static void press(InteractionHand pressedHand, long gameTime) {
        hand = pressedHand;
        pressedAt = gameTime;
    }

    /**
     * How far down the press is in that hand, 0 at rest to 1 at its lowest, eased at both ends. The game time counts
     * whole ticks; the partial tick makes it smooth between them.
     */
    public static float depth(InteractionHand inHand, long gameTime, float partialTick) {
        if (inHand != hand) {
            return 0;
        }
        float t = gameTime - pressedAt + partialTick;
        if (t <= 0 || t >= TICKS) {
            return 0;
        }
        float x = t < DOWN ? t / DOWN : 1 - (t - DOWN) / (TICKS - DOWN);
        return x * x * (3 - 2 * x);
    }
}
