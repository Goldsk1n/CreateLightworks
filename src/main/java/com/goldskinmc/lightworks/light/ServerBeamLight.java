package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.registry.LightworksBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps one {@link BeamLightBlock} in the air just in front of where a beam lands, moving or removing it
 * as the beam changes. Owned by the light's block entity; server side only.
 */
public final class ServerBeamLight {
    private static final String POS_TAG = "BeamLight";
    @Nullable
    private BlockPos pos;

    public @Nullable BlockPos pos() {
        return pos;
    }

    /**
     * @param owner      position of the block entity that owns this light
     * @param hit        where the beam stopped
     * @param direction  the beam's direction, used to step back from the hit face into the air
     * @param lightLevel brightness to place, 0 removes the light
     */
    public void update(Level level, BlockPos owner, BeamTrace.Hit hit, Vec3 direction, int lightLevel) {
        BlockPos target = null;
        if (hit.hitBlock() && lightLevel > 0) {
            BlockPos inFront = BlockPos.containing(hit.end().subtract(direction.normalize().scale(0.5)));
            if (!inFront.equals(owner) && canUse(level, inFront, owner)) {
                target = inFront;
            }
        }

        if (target != null && target.equals(pos) && level.getBlockState(target).is(LightworksBlocks.BEAM_LIGHT.get())) {
            // Same spot: only the brightness can change, and changing the state keeps the block entity
            BlockState state = level.getBlockState(target);
            if (state.getValue(BeamLightBlock.LEVEL) != lightLevel) {
                level.setBlock(target, state.setValue(BeamLightBlock.LEVEL, lightLevel), BeamLightBlock.QUIET_FLAGS);
            }
            return;
        }

        clear(level);
        if (target != null) {
            level.setBlock(target, LightworksBlocks.BEAM_LIGHT.getDefaultState().setValue(BeamLightBlock.LEVEL, lightLevel),
                    BeamLightBlock.QUIET_FLAGS);
            if (level.getBlockEntity(target) instanceof BeamLightBlockEntity light) {
                light.setOwner(owner);
            }
            pos = target;
        }
    }

    /** Removes the light if it is still ours and its chunk is loaded. */
    public void clear(Level level) {
        if (pos != null && level.isLoaded(pos) && level.getBlockState(pos).is(LightworksBlocks.BEAM_LIGHT.get())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), BeamLightBlock.QUIET_FLAGS);
        }
        pos = null;
    }

    /** Only empty air, or a beam light this owner placed, may hold the light. */
    private static boolean canUse(Level level, BlockPos target, BlockPos owner) {
        BlockState state = level.getBlockState(target);
        if (state.isAir()) {
            return true;
        }
        return state.is(LightworksBlocks.BEAM_LIGHT.get())
                && level.getBlockEntity(target) instanceof BeamLightBlockEntity light
                && owner.equals(light.owner());
    }

    public void save(CompoundTag tag) {
        if (pos != null) {
            tag.putLong(POS_TAG, pos.asLong());
        }
    }

    public void load(CompoundTag tag) {
        pos = tag.contains(POS_TAG) ? BlockPos.of(tag.getLong(POS_TAG)) : null;
    }
}
