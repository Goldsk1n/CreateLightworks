package com.goldskinmc.lightworks.content.pantilt;

import com.goldskinmc.lightworks.content.SideValueBox;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The Pan-Tilt Head's value box: how far it sweeps either way, in steps of {@value #DEGREES_PER_STEP}°. Zero is full circle. */
final class SweepBoard extends ScrollValueBehaviour {
    static final int DEGREES_PER_STEP = 5;
    private static final int STEPS = 180 / DEGREES_PER_STEP;
    private static final int STEPS_PER_MILESTONE = 30 / DEGREES_PER_STEP;

    SweepBoard(SmartBlockEntity head) {
        super(Component.translatable("lightworks.pan_tilt_head.sweep"), head, new Box());
        between(0, STEPS);
    }

    /** Text for a value, short enough for the board; also the goggles' line. */
    static MutableComponent named(int steps) {
        return steps == 0 ? Component.translatable("lightworks.pan_tilt_head.sweep_off")
                : Component.translatable("lightworks.pan_tilt_head.arc", steps * DEGREES_PER_STEP);
    }

    /** What the value box shows; left alone it would be the number of steps. */
    @Override
    public String formatValue() {
        return named(getValue()).getString();
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return new ValueSettingsBoard(label, max, STEPS_PER_MILESTONE,
                List.of(Component.translatable("lightworks.pan_tilt_head.arc_row")),
                new ValueSettingsFormatter(settings -> named(settings.value())));
    }

    /** On every side round the head, centred on its 13 pixel body. */
    private static final class Box extends SideValueBox {
        private static final double OUT = 7.5 / 16;
        private static final double ALONG = 6.5 / 16;

        Box() {
            super((state, side) -> side.getAxis() != state.getValue(PanTiltHeadBlock.FACING).getAxis());
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Direction mount = state.getValue(PanTiltHeadBlock.FACING);
            Vec3 side = Vec3.atLowerCornerOf(getSide().getNormal());
            Vec3 up = Vec3.atLowerCornerOf(mount.getNormal());
            return new Vec3(0.5, 0.5, 0.5).add(side.scale(OUT)).add(up.scale(ALONG - 0.5));
        }
    }
}
