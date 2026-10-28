package com.goldskinmc.lightworks.content.searchlight;

import com.goldskinmc.lightworks.debug.TrackedLights;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SearchlightBlockEntity extends SmartBlockEntity {
    public SearchlightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public Direction getFacing() {
        return getBlockState().getValue(SearchlightBlock.FACING);
    }

    /**
     * Centre of the lens face, in this block entity's level coordinates. On a Sable vehicle these are
     * plot coordinates, not world coordinates.
     */
    public Vec3 beamOrigin() {
        return Vec3.atCenterOf(worldPosition).relative(getFacing(), 0.5);
    }

    /** Unit vector the beam points along, in the same coordinates as {@link #beamOrigin()}. */
    public Vec3 beamDirection() {
        return Vec3.atLowerCornerOf(getFacing().getNormal());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            TrackedLights.add(this);
        }
    }

    /** SmartBlockEntity calls this when the block is removed and when its chunk unloads. */
    @Override
    public void invalidate() {
        super.invalidate();
        TrackedLights.remove(this);
    }
}
