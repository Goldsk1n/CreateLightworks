package com.goldskinmc.lightworks.content.directional;

import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.beam.Aim;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.beam.PanTilt;
import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.compat.DynamicLights;
import com.goldskinmc.lightworks.content.LightClipboard;
import com.goldskinmc.lightworks.content.LightGoggles;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.content.MovingBeam;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadBlockEntity;
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
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
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
 * client only. On a turning Pan-Tilt Head it is a moving light too, aimed by the head instead of by its
 * own aim.
 */
public class DirectionalLightBlockEntity extends SmartBlockEntity
        implements BeamLightOwner, BeamSource, TransformableBlockEntity, IHaveGoggleInformation, ClipboardCloneable {
    /** Head angles written for contraption copies, which cannot see the head. */
    static final String HEAD_YAW = "HeadYaw";
    static final String HEAD_ELEVATION = "HeadElevation";
    /** The values last set on the Pan and Tilt boards, kept because a wall light's aim rounds them. */
    private static final String BOARD_PAN = "BoardPan";
    private static final String BOARD_TILT = "BoardTilt";
    private static final String CLIPBOARD_PAN = "Pan";
    private static final String CLIPBOARD_TILT = "Tilt";
    /** How often the beam is re-traced to notice blocks placed or removed in its path. */
    private static final int RETRACE_INTERVAL_TICKS = 5;


    private final LightType type;
    private final ConeFalloff shape;
    private final LightState state = new LightState();
    private final ServerBeamLight serverLight = new ServerBeamLight();
    /** Client only: the dynamic light that follows this beam's spot. */
    private final ClientBeam clientBeam;
    @Nullable
    private Vec3 tracedDirection;
    private int tracedBrightness = -1;
    private int tracedTint = -1;
    private boolean tracedMoving;
    private boolean retraceDue = true;
    /**
     * The head's angles as of the last tick, or null when not on a head. Saving reads this rather than the
     * head: a chunk can be saved while its neighbours unload, and looking at them then waits forever.
     */
    @Nullable
    private float[] headAngles;
    /** Headlight on a Sable vehicle, client only: last seen moving the way it points. */
    private boolean leading = true;
    /** The Pan and Tilt boards' last values, or null; used while they still give the stored aim. */
    @Nullable
    private PanTilt board;

    public DirectionalLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.type = blockState.getBlock() instanceof DirectionalLightBlock block ? block.lightType() : LightType.SEARCHLIGHT;
        this.shape = Objects.requireNonNull(this.type.beam());
        this.clientBeam = new ClientBeam(shape, (float) this.type.lensHalf());
        setLazyTickRate(RETRACE_INTERVAL_TICKS);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new AimBoard(this, true));
        behaviours.add(new AimBoard(this, false));
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

    /** The aim as the Pan and Tilt boards show it. */
    public PanTilt panTilt() {
        Direction f = mountFacing();
        if (board != null && board.matches(f.getStepX(), f.getStepY(), f.getStepZ(), state.aim())) {
            return board;
        }
        return PanTilt.of(f.getStepX(), f.getStepY(), f.getStepZ(), state.aim());
    }

    /** Aims the light from board values, limited to what its mount allows. The server syncs it to clients. */
    public void setPanTilt(PanTilt value) {
        Direction f = mountFacing();
        PanTilt limited = value.clamp(f.getStepY());
        boolean boardChanged = !limited.equals(board);
        board = limited;
        if (!setAim(limited.toAim(f.getStepX(), f.getStepY(), f.getStepZ())) && boardChanged) {
            // Pan moved while the light points straight up or down: same beam, new board value
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    }

    /**
     * The aim a newly placed light starts with: level, and on a floor or ceiling turned the way the
     * player faces ({@code heading}, a horizontal direction); on a wall straight out.
     */
    public void aimLevel(Direction heading) {
        setPanTilt(mountFacing().getAxis().isVertical() ? new PanTilt((int) heading.toYRot() - 180, 0) : PanTilt.LEVEL);
    }

    /** The Pan-Tilt Head this light is mounted on, or null. */
    public @Nullable PanTiltHeadBlockEntity head() {
        if (level == null) {
            return null;
        }
        Direction facing = mountFacing();
        BlockPos below = worldPosition.relative(facing.getOpposite());
        if (!level.isLoaded(below)) {
            return null;
        }
        return level.getBlockEntity(below) instanceof PanTiltHeadBlockEntity head && head.facing() == facing ? head : null;
    }

    /** True while a Pan-Tilt Head turns this light: then it is a moving light. */
    public boolean turnedByHead() {
        PanTiltHeadBlockEntity head = head();
        return head != null && head.turning();
    }

    /**
     * {yaw, elevation} in degrees that the beam points at, {@code partialTick} into the current tick:
     * from the Pan-Tilt Head under the light, or the light's own aim.
     */
    public float[] aimAngles(float partialTick) {
        PanTiltHeadBlockEntity head = head();
        if (head != null) {
            return new float[] {head.yaw(partialTick), head.elevation(partialTick)};
        }
        return new float[] {state.aim().yaw(), state.aim().elevation()};
    }

    /** Unit vector the beam points along, in the same coordinates as {@link #beamOrigin()}. */
    public Vec3 beamDirection() {
        return beamDirection(0);
    }

    public Vec3 beamDirection(float partialTick) {
        float[] angles = aimAngles(partialTick);
        return direction(mountFacing(), angles[0], angles[1]);
    }

    /**
     * Centre of the lens, in this block entity's level coordinates. On a Sable vehicle these are plot
     * coordinates, not world coordinates.
     */
    public Vec3 beamOrigin() {
        return beamOrigin(0);
    }

    public Vec3 beamOrigin(float partialTick) {
        return Vec3.atCenterOf(worldPosition).add(beamDirection(partialTick).scale(lightType().lensDistance()));
    }

    static Vec3 direction(Direction facing, Aim aim) {
        return direction(facing, aim.yaw(), aim.elevation());
    }

    static Vec3 direction(Direction facing, double yaw, double elevation) {
        double[] d = Aim.direction(facing.getStepX(), facing.getStepY(), facing.getStepZ(), yaw, elevation);
        return new Vec3(d[0], d[1], d[2]);
    }

    /**
     * Brightness the light shows now, 0 (off) to 15. A Headlight without a redstone signal on a Sable
     * vehicle is dark while it trails (any signal overrides that, like on contraptions).
     */
    public int brightness() {
        return leading ? state.brightness() : 0;
    }

    /** Client: watches the vehicle's motion along the beam, like a Headlight on a contraption. */
    private void followMotion() {
        if (!type.followsMotion() || state.signal() != 0) {
            leading = true;
            return;
        }
        Vec3 velocity = SableHost.velocity(level, worldPosition);
        if (velocity == null) {
            leading = true;
            return;
        }
        double along = velocity.dot(tickBeam().direction());
        if (Math.abs(along) > MovingBeam.MIN_MOTION) {
            leading = along > 0;
        }
    }

    /** Light level the beam leaves where it lands, from the beam shape, the distance and the brightness. */
    public int lightLevelAt(BeamTrace.Hit hit) {
        if (!hit.hitBlock()) {
            return 0;
        }
        return lightLevel(shape.onAxis(hit.distance()));
    }

    private int lightLevel(double lightAtFullBrightness) {
        return Mth.clamp((int) Math.round(lightAtFullBrightness * brightness() / 15.0), 0, 15);
    }

    /** The beam in world coordinates, following the vehicle when this light is on one. */
    @Override
    public BeamPose tickBeam() {
        return movingWithHead(SableHost.toWorld(level, worldPosition, beamOrigin(), beamDirection()));
    }

    @Override
    public BeamPose renderBeam(float partialTick) {
        return movingWithHead(SableHost.toWorldForRender(worldPosition, beamOrigin(partialTick),
                beamDirection(partialTick), partialTick));
    }

    private BeamPose movingWithHead(BeamPose pose) {
        return pose.moving() || !turnedByHead() ? pose : new BeamPose(pose.origin(), pose.direction(), true);
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
            updateMount();
        }
    }

    /** Placed by a contraption, a schematic or a command, nothing told the block what it stands on. */
    private void updateMount() {
        BlockState block = getBlockState();
        Direction facing = mountFacing();
        BlockPos below = worldPosition.relative(facing.getOpposite());
        if (level.isLoaded(below)) {
            boolean onHead = DirectionalLightBlock.isHeadFor(facing, level.getBlockState(below));
            if (block.getValue(DirectionalLightBlock.ON_HEAD) != onHead) {
                level.setBlock(worldPosition, block.setValue(DirectionalLightBlock.ON_HEAD, onHead), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        long start = Timer.start();
        headAngles = head() == null ? null : aimAngles(0);
        if (level.isClientSide) {
            followMotion();
        }
        updateBeam();
        (level.isClientSide ? Timer.CLIENT_BLOCK : Timer.SERVER_BLOCK).stop(start);
    }

    private void updateBeam() {
        BeamPose beam = tickBeam();
        int brightness = brightness();
        int tint = lightState().colour() == null ? -1 : lightState().rgb(lightType());
        boolean changed = !beam.direction().equals(tracedDirection) || brightness != tracedBrightness
                || beam.moving() != tracedMoving || tint != tracedTint;
        // On a vehicle the beam moves every tick; a fixed one is re-traced when it changes or periodically
        if (!beam.moving() && !retraceDue && !changed) {
            return;
        }
        if (level.isClientSide) {
            clientBeam.update(level, beam, beam.moving() ? renderBeam(0.5f) : beam, brightness, tint);
        } else if (LightworksConfig.serverLightBlocks() && !beam.moving()) {
            serverLight.update(level, worldPosition, BeamTrace.landings(level, beam.origin(), beam.direction(), shape,
                    type.lensHalf(), BeamTrace.range(shape), type.rays()), brightness);
        } else {
            // Light blocks cannot keep up with a moving vehicle: vehicles rely on the client dynamic light
            serverLight.clear(level);
        }
        tracedDirection = beam.direction();
        tracedBrightness = brightness;
        tracedTint = tint;
        tracedMoving = beam.moving();
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
        return serverLight.owns(pos);
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

    /** Engineer's Goggles: state, aim and colour, and a warning when the beam can light nothing. Client side. */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LightGoggles.title(tooltip, getBlockState().getBlock().getName());
        LightGoggles.brightness(tooltip, state);
        if (type.followsMotion()) {
            LightGoggles.line(tooltip, state.signal() == 0 ? "headlight_auto" : "headlight_manual", ChatFormatting.AQUA);
        }
        PanTiltHeadBlockEntity head = head();
        if (head != null) {
            float[] angles = aimAngles(0);
            Direction f = mountFacing();
            PanTilt byHead = PanTilt.of(f.getStepX(), f.getStepY(), f.getStepZ(),
                    new Aim(Math.round(angles[0]), Math.round(angles[1])));
            LightGoggles.line(tooltip, "aim_head", ChatFormatting.AQUA, pan(byHead), tilt(byHead));
        } else {
            LightGoggles.line(tooltip, "aim", ChatFormatting.AQUA, pan(panTilt()), tilt(panTilt()));
        }
        if (state.on() && brightness() > 0 && level != null) {
            if (!DynamicLights.present() && (tickBeam().moving() || !LightworksConfig.serverLightBlocks())) {
                LightGoggles.line(tooltip, tickBeam().moving() ? "moving_beam_only" : "no_light_path",
                        ChatFormatting.YELLOW);
            }
        }
        LightGoggles.colour(tooltip, state.colour());
        if (isPlayerSneaking) {
            LightGoggles.hint(tooltip, head != null ? "on_head" : "aim");
            LightGoggles.hint(tooltip, "dye");
            LightGoggles.hint(tooltip, "clipboard");
            LightGoggles.hint(tooltip, LightworksConfig.invertRedstone() ? "redstone_inverted" : "redstone");
        }
        return true;
    }

    @Override
    public String getClipboardKey() {
        return LightClipboard.KEY;
    }

    /** Colour and aim, as the Pan and Tilt boards show it: relative to the mount, so a row of lights ends up parallel. */
    @Override
    public boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side) {
        LightClipboard.writeColour(tag, state);
        PanTilt aim = panTilt();
        tag.putInt(CLIPBOARD_PAN, aim.pan());
        tag.putInt(CLIPBOARD_TILT, aim.tilt());
        return true;
    }

    @Override
    public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player, Direction side,
            boolean simulate) {
        if (!LightClipboard.hasColour(tag)) {
            return false;
        }
        if (simulate) {
            return true;
        }
        // Without the dye the rest still pastes: a row of lights should not stay crooked for want of it
        LightClipboard.readColour(tag, state, player);
        if (tag.contains(CLIPBOARD_TILT)) {
            setPanTilt(new PanTilt(tag.getInt(CLIPBOARD_PAN), tag.getInt(CLIPBOARD_TILT)));
        }
        notifyUpdate();
        return true;
    }

    private static String pan(PanTilt aim) {
        return (aim.pan() < 0 ? "⟲ " : "⟳ ") + Math.abs(aim.pan()) + "°";
    }

    private static String tilt(PanTilt aim) {
        return (aim.tilt() < 0 ? "↓ " : "↑ ") + Math.abs(aim.tilt()) + "°";
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        state.write(tag);
        if (board != null) {
            tag.putInt(BOARD_PAN, board.pan());
            tag.putInt(BOARD_TILT, board.tilt());
        }
        // In client packets too: Create syncs a contraption to clients with this block entity's update tag
        if (headAngles != null) {
            tag.putFloat(HEAD_YAW, headAngles[0]);
            tag.putFloat(HEAD_ELEVATION, headAngles[1]);
        }
        if (!clientPacket) {
            serverLight.save(tag);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        state.read(tag);
        board = tag.contains(BOARD_TILT) ? new PanTilt(tag.getInt(BOARD_PAN), tag.getInt(BOARD_TILT)) : null;
        if (!clientPacket) {
            serverLight.load(tag);
        }
    }
}
