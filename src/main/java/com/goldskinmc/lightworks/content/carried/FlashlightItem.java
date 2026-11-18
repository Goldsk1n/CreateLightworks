package com.goldskinmc.lightworks.content.carried;

import com.goldskinmc.lightworks.registry.LightworksComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LayeredCauldronBlock;
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
        } else {
            FlashlightPress.press(hand, level.getGameTime());
        }
        // Consumed on both sides, not a success: a success swings the arm as a punch does, for everyone to see
        return InteractionResultHolder.consume(stack);
    }

    /** A dyed Flashlight says its colour under its name, the colour's name drawn in that colour. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DyeColor colour = colour(stack);
        if (colour != null) {
            tooltip.add(Component.translatable("item.lightworks.flashlight.colour",
                    Component.translatable("color.minecraft." + colour.getName())
                            .withStyle(style -> style.withColor(readable(colour.getTextColor()))))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** A dark colour (black, blue, brown) mixed with white until it reads on the tooltip's near-black background. */
    private static int readable(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        double luma = 0.299 * r + 0.587 * g + 0.114 * b;
        double wanted = 110;
        if (luma >= wanted) {
            return rgb;
        }
        double t = (wanted - luma) / (255 - luma);
        return (int) (r + (255 - r) * t) << 16 | (int) (g + (255 - g) * t) << 8 | (int) (b + (255 - b) * t);
    }

    /**
     * Used on a water cauldron, a dyed Flashlight comes out undyed and the water drops a level, as dyed leather does.
     * Everything else on it stays. Call once, on the main thread (common setup's {@code enqueueWork}).
     */
    public static void registerCauldronWashing(Item flashlight) {
        CauldronInteraction.WATER.map().put(flashlight, (state, level, pos, player, hand, stack) -> {
            if (!stack.has(LightworksComponents.LAMP_COLOUR.get())) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                stack.remove(LightworksComponents.LAMP_COLOUR.get());
                LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        });
    }

    /** Switching it on or off keeps it where it is in the hand: a changed stack would otherwise replay the equip. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }
}
