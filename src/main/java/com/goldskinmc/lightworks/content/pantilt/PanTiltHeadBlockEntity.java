package com.goldskinmc.lightworks.content.pantilt;

import com.goldskinmc.lightworks.beam.Sweep;
import com.goldskinmc.lightworks.content.LightGoggles;
import com.goldskinmc.lightworks.content.SpinAngle;
import com.goldskinmc.lightworks.content.TickPhase;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A kinetic mount that turns the directional light on top of it. Shaft rotation pans the light around
 * the head's facing, exactly as fast as the shaft turns; analog redstone sets the tilt, 6 degrees per
 * signal level from along the mount (0) to straight out of it (15), which the head slews to at
 * {@value #TILT_PER_TICK} degrees a tick. The value box makes the pan {@link Sweep} back and forth instead of
 * going round. Both sides integrate the angles every tick, so client and server agree without packets except
 * when the speed, the signal or the box changes.
 */
public class PanTiltHeadBlockEntity extends KineticBlockEntity {
    public static final float TILT_PER_SIGNAL = 6f;
    public static final float TILT_PER_TICK = 3f;

    private final SpinAngle pan = new SpinAngle();
    private final Sweep sweep = new Sweep();
    private SweepBoard sweepBoard;
    private float tilt;
    private float prevTilt;
    private int signal;
    /** Game time of the last tick, so angles read mid-tick are the same whether this ticked first or not. */
    private long tickedAt = Long.MIN_VALUE;

    public PanTiltHeadBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(PanTiltHeadBlock.FACING);
    }

    /** Degrees the pan moves this tick; signed so the head turns the same way as the shaft driving it. */
    private float panStep() {
        float step = getSpeed() * SpinAngle.DEGREES_PER_RPM_TICK;
        return facing().getAxisDirection() == Direction.AxisDirection.POSITIVE ? step : -step;
    }

    /** How far the head sweeps either way; 0 goes full circle. */
    private float arc() {
        return sweepBoard == null ? 0 : sweepBoard.getValue() * SweepBoard.DEGREES_PER_STEP;
    }

    private float targetTilt() {
        return signal * TILT_PER_SIGNAL;
    }

    private static float slew(float from, float to) {
        return from + Mth.clamp(to - from, -TILT_PER_TICK, TILT_PER_TICK);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        sweepBoard = new SweepBoard(this);
        behaviours.add(sweepBoard);
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
        if (level != null) {
            pan.tick(sweep.step(panStep(), arc()), level.getGameTime());
        }
        prevTilt = tilt;
        tilt = slew(tilt, targetTilt());
        if (level != null) {
            tickedAt = level.getGameTime();
        }
    }

    /** Also true for copies that never tick (contraption render worlds, Ponder): they hold still. */
    private boolean tickedThisTick() {
        return TickPhase.ticked(level, tickedAt);
    }

    /** Pan angle (yaw in the mount frame) {@code partialTick} into the current tick. */
    public float yaw(float partialTick) {
        return pan.at(partialTick, sweep.peek(panStep(), arc()), level);
    }

    /** Tilt (elevation above the mount) {@code partialTick} into the current tick. */
    public float elevation(float partialTick) {
        if (!tickedThisTick()) {
            return Mth.lerp(partialTick, tilt, slew(tilt, targetTilt()));
        }
        return Mth.lerp(partialTick, prevTilt, tilt);
    }

    /** True while the light on top moves: the shaft turns or the tilt is still slewing. */
    public boolean turning() {
        return getSpeed() != 0 || tilt != targetTilt();
    }

    public int signal() {
        return signal;
    }

    /** Reads the redstone around the head (server). */
    public void updateSignal() {
        if (level == null || level.isClientSide) {
            return;
        }
        int next = level.getBestNeighborSignal(worldPosition);
        if (next != signal) {
            signal = next;
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
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        LightGoggles.title(tooltip, getBlockState().getBlock().getName());
        LightGoggles.line(tooltip, "pan_tilt", ChatFormatting.AQUA, Math.round(Mth.wrapDegrees(yaw(0)) + 360) % 360,
                Math.round(elevation(0)));
        LightGoggles.line(tooltip, "tilt_signal", ChatFormatting.RED, signal, Math.round(targetTilt()));
        LightGoggles.line(tooltip, "sweep", ChatFormatting.AQUA, SweepBoard.named(sweepBoard.getValue()));
        if (isPlayerSneaking) {
            LightGoggles.hint(tooltip, "pan_tilt");
            LightGoggles.hint(tooltip, "sweep");
            LightGoggles.hint(tooltip, "clipboard");
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Pan", pan.get());
        tag.putFloat("SweepOffset", sweep.offset());
        tag.putInt("SweepDirection", sweep.direction());
        tag.putFloat("Tilt", tilt);
        tag.putInt("Signal", signal);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        pan.set(tag.getFloat("Pan"));
        sweep.set(tag.getFloat("SweepOffset"), tag.getInt("SweepDirection"));
        tilt = tag.getFloat("Tilt");
        signal = tag.getInt("Signal");
        prevTilt = tilt;
    }
}
