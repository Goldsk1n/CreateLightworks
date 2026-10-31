package com.goldskinmc.lightworks.mixin;

import com.goldskinmc.lightworks.light.BlockChanges;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game has no event for a block changing on the client, and what we paint on blocks has to hear of it. */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void lightworks$blockChanged(BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo info) {
        BlockChanges.changed((ClientLevel) (Object) this, pos, before, after);
    }
}
