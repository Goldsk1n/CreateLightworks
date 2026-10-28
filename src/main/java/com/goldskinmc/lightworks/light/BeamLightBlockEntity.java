package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.registry.LightworksBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Remembers which block placed this beam light, so the light can tell when it has been abandoned. */
public class BeamLightBlockEntity extends BlockEntity {
    private static final String OWNER_TAG = "Owner";
    @Nullable
    private BlockPos owner;

    public BeamLightBlockEntity(BlockPos pos, BlockState state) {
        super(LightworksBlockEntities.BEAM_LIGHT.get(), pos, state);
    }

    public void setOwner(BlockPos owner) {
        this.owner = owner.immutable();
        setChanged();
    }

    public @Nullable BlockPos owner() {
        return owner;
    }

    /**
     * True when the owner is loaded and no longer points its light here. An unloaded owner might still
     * want the light, so it counts as not orphaned until it can be checked.
     */
    public boolean isOrphaned(Level level) {
        if (owner == null) {
            return true;
        }
        if (!level.isLoaded(owner)) {
            return false;
        }
        return !(level.getBlockEntity(owner) instanceof BeamLightOwner light && light.ownsBeamLight(worldPosition));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putLong(OWNER_TAG, owner.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.contains(OWNER_TAG) ? BlockPos.of(tag.getLong(OWNER_TAG)) : null;
    }
}
