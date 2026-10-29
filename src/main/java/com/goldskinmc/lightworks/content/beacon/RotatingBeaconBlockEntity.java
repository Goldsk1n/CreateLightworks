package com.goldskinmc.lightworks.content.beacon;

import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.beam.Aim;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.content.LightGoggles;
import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.content.SpinAngle;
import com.goldskinmc.lightworks.debug.Timer;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.host.SableHost;
import com.goldskinmc.lightworks.light.BeamLightOwner;
import com.goldskinmc.lightworks.light.BeamSource;
import com.goldskinmc.lightworks.light.BeamTrace;
import com.goldskinmc.lightworks.light.ClientBeam;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.goldskinmc.lightworks.light.ServerBeamLight;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Two opposite beams on a rotor turned by a shaft, level with the mount. While the rotor turns the beams
 * are moving lights (client dynamic light and cosmetic beam, re-traced every tick); stopped, each lights
 * where it lands through a server light block, like a fixed Searchlight.
 */
public class RotatingBeaconBlockEntity extends KineticBlockEntity implements BeamLightOwner {
    static final String ANGLE = "Angle";
    /** Beams leave the rotor level with the mount. */
    static final float ELEVATION = 0;
    private static final int RETRACE_INTERVAL_TICKS = 5;

    private final LightType type = LightType.ROTATING_BEACON;
    private final ConeFalloff shape = Objects.requireNonNull(type.beam());
    private final LightState state = new LightState();
    private final SpinAngle rotor = new SpinAngle();
    private final Beam[] beams = {new Beam(0), new Beam(180)};
    private int lazyTicks;

    public RotatingBeaconBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    public LightType lightType() {
        return type;
    }

    public LightState lightState() {
        return state;
    }

    public Direction facing() {
        return getBlockState().getValue(RotatingBeaconBlock.FACING);
    }

    /** Degrees the rotor turns this tick; signed so it turns the same way as the shaft driving it. */
    float step() {
        float step = getSpeed() * SpinAngle.DEGREES_PER_RPM_TICK;
        return facing().getAxisDirection() == Direction.AxisDirection.POSITIVE ? step : -step;
    }

    /** Rotor angle {@code partialTick} into the current tick. */
    public float angle(float partialTick) {
        return rotor.at(partialTick, step(), level);
    }

    public boolean turning() {
        return getSpeed() != 0;
    }

    Beam[] beams() {
        return beams;
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
        rotor.tick(step(), level.getGameTime());
        long start = Timer.start();
        boolean retrace = ++lazyTicks >= RETRACE_INTERVAL_TICKS;
        if (retrace) {
            lazyTicks = 0;
            if (!level.isClientSide) {
                refreshBrightness();
            }
        }
        for (Beam beam : beams) {
            beam.update(retrace);
        }
        (level.isClientSide ? Timer.CLIENT_BLOCK : Timer.SERVER_BLOCK).stop(start);
    }

    /** Reads the redstone around the beacon (server). */
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
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        // Let clients start from the server's angle
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    @Override
    public boolean ownsBeamLight(BlockPos pos) {
        for (Beam beam : beams) {
            if (pos.equals(beam.serverLight.pos())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void remove() {
        super.remove();
        if (level != null && !level.isClientSide) {
            for (Beam beam : beams) {
                beam.serverLight.clear(level);
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide && ClientBeams.isRealWorld(level)) {
            for (Beam beam : beams) {
                ClientBeams.add(beam);
            }
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null && level.isClientSide) {
            for (Beam beam : beams) {
                ClientBeams.remove(beam);
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        LightGoggles.title(tooltip, getBlockState().getBlock().getName());
        LightGoggles.brightness(tooltip, state);
        LightGoggles.line(tooltip, turning() ? "beacon_turning" : "beacon_stopped", ChatFormatting.AQUA);
        LightGoggles.colour(tooltip, state.colour());
        if (isPlayerSneaking) {
            LightGoggles.hint(tooltip, "beacon");
            LightGoggles.hint(tooltip, "dye");
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        state.write(tag);
        tag.putFloat(ANGLE, rotor.get());
        if (!clientPacket) {
            beams[0].serverLight.save(tag);
            beams[1].serverLight.save(tag);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        state.read(tag);
        rotor.set(tag.getFloat(ANGLE));
        if (!clientPacket) {
            beams[0].serverLight.load(tag);
            beams[1].serverLight.load(tag);
        }
    }

    /** One of the two beams: {@code offset} degrees round the rotor from the first. */
    final class Beam implements BeamSource {
        final float offset;
        final ServerBeamLight serverLight;
        final ClientBeam clientBeam = new ClientBeam(shape, (float) type.lensHalf());
        @Nullable
        private Vec3 tracedDirection;
        private int tracedBrightness = -1;
        private int tracedTint = -1;
        private boolean tracedMoving;

        Beam(float offset) {
            this.offset = offset;
            this.serverLight = new ServerBeamLight(offset == 0 ? "BeamLight" : "BeamLight2");
        }

        Vec3 direction(float partialTick) {
            Direction f = facing();
            double[] d = Aim.direction(f.getStepX(), f.getStepY(), f.getStepZ(), angle(partialTick) + offset, ELEVATION);
            return new Vec3(d[0], d[1], d[2]);
        }

        Vec3 origin(float partialTick) {
            return Vec3.atCenterOf(worldPosition).add(direction(partialTick).scale(type.lensDistance()));
        }

        private BeamPose moving(BeamPose pose) {
            return pose.moving() || !turning() ? pose : new BeamPose(pose.origin(), pose.direction(), true);
        }

        @Override
        public BeamPose tickBeam() {
            return moving(SableHost.toWorld(level, worldPosition, origin(0), direction(0)));
        }

        @Override
        public BeamPose renderBeam(float partialTick) {
            return moving(SableHost.toWorldForRender(worldPosition, origin(partialTick), direction(partialTick),
                    partialTick));
        }

        @Override
        public ClientBeam beam() {
            return clientBeam;
        }

        @Override
        public boolean isGone() {
            return isRemoved() || level == null;
        }

        void update(boolean retraceDue) {
            BeamPose beam = tickBeam();
            int brightness = state.brightness();
            int tint = state.colour() == null ? -1 : state.rgb(type);
            boolean changed = !beam.direction().equals(tracedDirection) || brightness != tracedBrightness
                    || beam.moving() != tracedMoving || tint != tracedTint;
            if (!beam.moving() && !retraceDue && !changed) {
                return;
            }
            if (level.isClientSide) {
                clientBeam.update(level, beam, beam.moving() ? renderBeam(0.5f) : beam, brightness, tint);
            } else if (LightworksConfig.serverLightBlocks() && !beam.moving()) {
                BeamTrace.Hit hit = BeamTrace.trace(level, beam.origin(), beam.direction(), BeamTrace.range(shape));
                BeamTrace.Landing edge = hit.hitBlock() ? null : BeamTrace.edgeLanding(level, beam.origin(),
                        beam.direction(), shape, type.lensHalf(), BeamTrace.range(shape));
                if (edge != null) {
                    serverLight.update(level, worldPosition, edge.hit(), edge.direction(),
                            Mth.clamp((int) Math.round(edge.light() * brightness / 15.0), 0, 15));
                } else {
                    int lightLevel = hit.hitBlock()
                            ? Mth.clamp((int) Math.round(shape.onAxis(hit.distance()) * brightness / 15.0), 0, 15) : 0;
                    serverLight.update(level, worldPosition, hit, beam.direction(), lightLevel);
                }
            } else {
                serverLight.clear(level);
            }
            tracedDirection = beam.direction();
            tracedBrightness = brightness;
            tracedTint = tint;
            tracedMoving = beam.moving();
        }
    }
}
