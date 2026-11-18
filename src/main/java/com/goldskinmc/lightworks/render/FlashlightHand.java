package com.goldskinmc.lightworks.render;

import com.goldskinmc.lightworks.content.carried.FlashlightPress;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * The Flashlight in first person: Minecraft's own hand pose, swing and equip (we return false, so it still applies
 * them), with the {@link FlashlightPress} put in front of them, a slight dip of the lamp about the hand when its switch
 * is pressed. Turning about the hand, where Minecraft's pose puts it at rest, keeps the fist in place and tips the lens.
 * Kept small (owner, Nov 18, over no motion, 4 degrees, a Potato Cannon-style kick and a Linked Controller-style raised
 * pose while lit): the switch sliding and the light coming on say the most.
 */
public final class FlashlightHand implements IClientItemExtensions {
    /** Degrees the lens tips down at the press's lowest. */
    private static final float TIP = 2;
    /** Blocks the hand sinks at the press's lowest. */
    private static final float SINK = 0.01f;

    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand,
            float partialTick, float equipProcess, float swingProcess) {
        InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        float depth = FlashlightPress.depth(hand, player.level().getGameTime(), partialTick);
        if (depth > 0) {
            // Minecraft's rest pose for a held item (ItemInHandRenderer.applyItemArmTransform)
            float side = arm == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.translate(side * 0.56f, -0.52f - SINK * depth, -0.72f);
            poseStack.mulPose(Axis.XP.rotationDegrees(-TIP * depth));
            poseStack.translate(side * -0.56f, 0.52f, 0.72f);
        }
        return false;
    }
}
