package com.goldskinmc.lightworks.content.searchlight;

import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.host.SableHost;
import com.goldskinmc.lightworks.compat.DynamicLights;
import com.goldskinmc.lightworks.compat.DynamicSpot;
import com.goldskinmc.lightworks.debug.TrackedLights;
import com.goldskinmc.lightworks.light.BeamLightOwner;
import com.goldskinmc.lightworks.light.BeamTrace;
import com.goldskinmc.lightworks.light.ServerBeamLight;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class SearchlightBlockEntity extends SmartBlockEntity implements BeamLightOwner {
    /** Narrow beam: full brightness to 32 blocks, dark at 48. */
    public static final ConeFalloff BEAM = new ConeFalloff(6, 4, 32, 48, 15);
    /** How often the beam is re-traced to notice blocks placed or removed in its path. */
    private static final int RETRACE_INTERVAL_TICKS = 5;

    private final ServerBeamLight serverLight = new ServerBeamLight();
    /** Client only: the dynamic light that follows this beam's spot. */
    private DynamicSpot dynamicSpot = DynamicSpot.NONE;
    @Nullable
    private BeamSpot lastSpot;
    @Nullable
    private Direction tracedFacing;
    private boolean retraceDue = true;

    public SearchlightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(RETRACE_INTERVAL_TICKS);
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

    /** Light level the beam leaves where it lands, from the beam shape and the distance to the hit. */
    public static int lightLevelAt(BeamTrace.Hit hit) {
        return hit.hitBlock() ? Mth.clamp((int) Math.round(BEAM.onAxis(hit.distance())), 0, 15) : 0;
    }

    /** The beam in world coordinates, following the vehicle when this Searchlight is on one. */
    public BeamPose worldBeam() {
        return SableHost.toWorld(level, worldPosition, beamOrigin(), beamDirection());
    }

    /** Client only: the spot most recently handed to the dynamic light, or null if the beam hits nothing. */
    public @Nullable BeamSpot lastSpot() {
        return lastSpot;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        BeamPose beam = worldBeam();
        // On a vehicle the beam moves every tick; a fixed one is re-traced when re-aimed or periodically
        if (!beam.onVehicle() && !retraceDue && getFacing() == tracedFacing) {
            return;
        }
        BeamTrace.Hit hit = BeamTrace.trace(level, beam.origin(), beam.direction(), BEAM.range());
        if (level.isClientSide) {
            lastSpot = BeamTrace.spotFor(hit, beam.direction(), BEAM.onAxis(hit.distance()));
            dynamicSpot.set(lastSpot);
        } else if (LightworksConfig.serverLightBlocks() && !beam.onVehicle()) {
            serverLight.update(level, worldPosition, hit, beam.direction(), lightLevelAt(hit));
        } else {
            // Light blocks cannot keep up with a moving vehicle: vehicles rely on the client dynamic light
            serverLight.clear(level);
        }
        tracedFacing = getFacing();
        retraceDue = false;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        retraceDue = true;
    }

    @Override
    public @Nullable BlockPos beamLightPos() {
        return serverLight.pos();
    }

    /** Block destroyed, replaced or picked up by a contraption: take the light with it. */
    @Override
    public void remove() {
        super.remove();
        if (level != null && !level.isClientSide) {
            serverLight.clear(level);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            TrackedLights.add(this);
            dynamicSpot = DynamicLights.createSpot();
            retraceDue = true;
        }
    }

    /** SmartBlockEntity calls this when the block is removed and when its chunk unloads. */
    @Override
    public void invalidate() {
        super.invalidate();
        TrackedLights.remove(this);
        dynamicSpot.remove();
        dynamicSpot = DynamicSpot.NONE;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) {
            serverLight.save(tag);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket) {
            serverLight.load(tag);
        }
    }
}
