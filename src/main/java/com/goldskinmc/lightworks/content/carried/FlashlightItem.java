package com.goldskinmc.lightworks.content.carried;

import com.goldskinmc.lightworks.registry.LightworksComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A brass hand lamp: right-click turns it on and off, in either hand. While lit it throws a narrow beam where its holder
 * looks ({@link CarriedBeam}), a moving light drawn and lit on the client only: no server light blocks, no mob-proofing.
 */
public class FlashlightItem extends Item {
    public FlashlightItem(Properties properties) {
        super(properties);
    }

    public static boolean isOn(ItemStack stack) {
        return stack.getItem() instanceof FlashlightItem && stack.getOrDefault(LightworksComponents.LAMP_ON.get(), false);
    }

    public static @Nullable DyeColor colour(ItemStack stack) {
        return stack.get(LightworksComponents.LAMP_COLOUR.get());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean on = !isOn(stack);
            stack.set(LightworksComponents.LAMP_ON.get(), on);
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LEVER_CLICK,
                    SoundSource.PLAYERS, 0.3f, on ? 1.4f : 1.1f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Switching it on or off keeps it where it is in the hand: a changed stack would otherwise replay the equip. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }
}
