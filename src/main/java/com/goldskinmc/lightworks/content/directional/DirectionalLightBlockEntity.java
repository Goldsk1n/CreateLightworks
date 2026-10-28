package com.goldskinmc.lightworks.content.directional;

import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.beam.Aim;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.debug.Timer;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.host.SableHost;
import com.goldskinmc.lightworks.light.BeamLightOwner;
import com.goldskinmc.lightworks.light.BeamSource;
import com.goldskinmc.lightworks.light.BeamTrace;
import com.goldskinmc.lightworks.light.ClientBeam;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.goldskinmc.lightworks.light.ServerBeamLight;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A placed Searchlight, Floodlight or Headlight, also when it rides a Sable vehicle (where its position
 * is in plot coordinates and {@link SableHost} maps the beam to the world). A fixed light re-traces its
 * beam when re-aimed, dimmed, or every few ticks to notice blocks placed in its path, and lights the hit
 * through a server light block and the client dynamic light. On a vehicle it re-traces every tick on the
 * client only.
 */
public class DirectionalLightBlockEntity extends SmartBlockEntity
        implements BeamLightOwner, BeamSource, TransformableBlockEntity {
    /** How often the beam is re-traced to notice blocks placed or removed in its path. */
    private static final int RETRACE_INTERVAL_TICKS = 5;
    /** The lens sits this far from the block's centre, along the beam. */
    public static final double LENS_RADIUS = 0.5;

    private final LightType type;
    private final ConeFalloff shape;
    private final LightState state = new LightState();
    private final ServerBeamLight serverLight = new ServerBeamLight();
    /** Client only: the dynamic light that follows this beam's spot. */
    private final ClientBeam clientBeam;
    @Nullable
    private Vec3 tracedDirection;
    private int tracedBrightness = -1;
    private boolean retraceDue = true;

    public DirectionalLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.type = blockState.getBlock() instanceof DirectionalLightBlock block ? block.lightType() : LightType.SEARCHLIGHT;
        this.shape = Objects.requireNonNull(this.type.beam());
        this.clientBeam = new ClientBeam(shape);
        setLazyTickRate(RETRACE_INTERVAL_TICKS);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public LightType lightType() {
        return type;
    }

    public LightState lightState() {
        return state;
    }

    /** The side the light is mounted on: its aim is relative to this. */
    public Direction mountFacing() {
        return getBlockState().getValue(DirectionalLightBlock.FACING);
    }

    public Aim aim() {
        return state.aim();
    }

    /** Sets the aim; true if it changed. The server syncs it to clients. */
    public boolean setAim(Aim aim) {
        if (aim.equals(state.aim())) {
            return false;
        }
        state.setAim(aim);
        setChanged();
        if (level != null && !level.isClientSide) {
            sendData();
        }
        return true;
    }

    /** Aims the beam along {@code look} (a world direction), mirrored out of the mount if it points into it. */
    public boolean aimAlong(Vec3 look) {
        Direction facing = mountFacing();
        return setAim(Aim.fromDirection(facing.getStepX(), facing.getStepY(), facing.getStepZ(), look.x, look.y, look.z));
    }

    /** Unit vector the beam points along, in the same coordinates as {@link #beamOrigin()}. */
    public Vec3 beamDirection() {
        return direction(mountFacing(), state.aim());
    }

    /**
     * Centre of the lens, in this block entity's level coordinates. On a Sable vehicle these are plot
     * coordinates, not world coordinates.
     */
    public Vec3 beamOrigin() {
        return Vec3.atCenterOf(worldPosition).add(beamDirection().scale(LENS_RADIUS));
    }

    static Vec3 direction(Direction facing, Aim aim) {
        double[] d = aim.direction(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        return new Vec3(d[0], d[1], d[2]);
    }

    /** Brightness the light shows now, 0 (off) to 15. */
    public int brightness() {
        return state.brightness();
    }

    /** Light level the beam leaves where it lands, from the beam shape, the distance and the brightness. */
    public int lightLevelAt(BeamTrace.Hit hit) {
        if (!hit.hitBlock()) {
            return 0;
        }
        return Mth.clamp((int) Math.round(shape.onAxis(hit.distance()) * brightness() / 15.0), 0, 15);
    }

    /** The beam in world coordinates, following the vehicle when this light is on one. */
    @Override
    public BeamPose tickBeam() {
        return SableHost.toWorld(level, worldPosition, beamOrigin(), beamDirection());
    }

    @Override
    public BeamPose renderBeam(float partialTick) {
        return SableHost.toWorldForRender(worldPosition, beamOrigin(), beamDirection(), partialTick);
    }

    @Override
    public ClientBeam beam() {
        return clientBeam;
    }

    @Override
    public boolean isGone() {
        return isRemoved() || level == null;
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            updateSignal();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        long start = Timer.start();
        updateBeam();
        (level.isClientSide ? Timer.CLIENT_BLOCK : Timer.SERVER_BLOCK).stop(start);
    }

    private void updateBeam() {
        BeamPose beam = tickBeam();
        int brightness = brightness();
        boolean changed = !beam.direction().equals(tracedDirection) || brightness != tracedBrightness;
        // On a vehicle the beam moves every tick; a fixed one is re-traced when it changes or periodically
        if (!beam.moving() && !retraceDue && !changed) {
            return;
        }
        if (level.isClientSide) {
            clientBeam.update(level, beam, beam.moving() ? renderBeam(0.5f) : beam, brightness);
        } else if (LightworksConfig.serverLightBlocks() && !beam.moving()) {
            BeamTrace.Hit hit = BeamTrace.trace(level, beam.origin(), beam.direction(), BeamTrace.range(shape));
            serverLight.update(level, worldPosition, hit, beam.direction(), lightLevelAt(hit));
        } else {
            // Light blocks cannot keep up with a moving vehicle: vehicles rely on the client dynamic light
            serverLight.clear(level);
        }
        tracedDirection = beam.direction();
        tracedBrightness = brightness;
        retraceDue = false;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        retraceDue = true;
        if (level != null && !level.isClientSide) {
            // Picks up a changed invertRedstone setting
            refreshBrightness();
        }
    }

    /** Reads the redstone around the light (server). */
    public void updateSignal() {
        if (level == null || level.isClientSide) {
            return;
        }
        int signal = level.getBestNeighborSignal(worldPosition);
        if (signal != state.signal()) {
            state.setSignal(signal);
            setChanged();
        }
        refreshBrightness();
    }

    private void refreshBrightness() {
        int brightness = LightworksConfig.brightnessFor(state.signal());
        if (brightness != state.brightness()) {
            state.setBrightness(brightness);
            setChanged();
            sendData();
        }
    }

    @Override
    public boolean ownsBeamLight(BlockPos pos) {
        return pos.equals(serverLight.pos());
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
        if (level != null && level.isClientSide && ClientBeams.isRealWorld(level)) {
            ClientBeams.add(this);
            retraceDue = true;
        }
    }

    /** SmartBlockEntity calls this when the block is removed and when its chunk unloads. */
    @Override
    public void invalidate() {
        super.invalidate();
        // ClientBeams is client-only: never load it on a dedicated server
        if (level != null && level.isClientSide) {
            ClientBeams.remove(this);
        }
    }

    /**
     * Placed back by a contraption that turned or was mirrored: the block state has already turned, and
     * the aim (relative to the mount) is turned with it so the beam keeps pointing the same way on the block.
     */
    @Override
    public void transform(BlockEntity blockEntity, StructureTransform transform) {
        Direction now = mountFacing();
        Vec3 normal = Vec3.atLowerCornerOf(now.getNormal());
        // unapplyWithoutOffset turns about the block centre; shift so it turns the bare vector
        Vec3 centre = new Vec3(0.5, 0.5, 0.5);
        Vec3 normalBefore = transform.unapplyWithoutOffset(normal.add(centre)).subtract(centre);
        Direction before = Direction.getNearest(normalBefore.x, normalBefore.y, normalBefore.z);
        Vec3 moved = transform.applyWithoutOffsetUncentered(direction(before, state.aim()));
        state.setAim(Aim.fromDirection(now.getStepX(), now.getStepY(), now.getStepZ(), moved.x, moved.y, moved.z));
        notifyUpdate();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        state.write(tag);
        if (!clientPacket) {
            serverLight.save(tag);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        state.read(tag);
        if (!clientPacket) {
            serverLight.load(tag);
        }
    }
}
