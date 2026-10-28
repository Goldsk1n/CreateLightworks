package com.goldskinmc.lightworks.content;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Right-clicking a light with a dye tints its lens and beam, using up the dye like dyeing a sign. Colour is
 * cosmetic: block light and dynamic light stay white.
 */
public final class Dyeing {
    private Dyeing() {
    }

    /** Dyes {@code light} if {@code stack} is a dye of another colour; otherwise lets the click through. */
    public static ItemInteractionResult use(ItemStack stack, Level level, BlockPos pos, Player player,
            SmartBlockEntity light, LightState state) {
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        DyeColor colour = dye.getDyeColor();
        if (colour == state.colour()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            state.setColour(colour);
            light.notifyUpdate();
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1, 1);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
