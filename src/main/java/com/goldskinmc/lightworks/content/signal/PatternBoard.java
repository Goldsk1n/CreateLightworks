package com.goldskinmc.lightworks.content.signal;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter.ScrollOptionSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Warning Light's value box: a board of its patterns in two rows, always and only at night, so one hold
 * and drag picks both. Create's icon boards draw no row labels, so the text at the cursor names the row.
 */
final class PatternBoard extends ScrollOptionBehaviour<WarningPattern> {
    private static final String ALWAYS = "lightworks.warning_light.always";
    private static final String NIGHT = "lightworks.warning_light.night";

    private final SignalLampBlockEntity lamp;

    PatternBoard(SignalLampBlockEntity lamp) {
        super(WarningPattern.class, Component.translatable("lightworks.warning_light.pattern"), lamp, new PatternBox());
        this.lamp = lamp;
    }

    /** Text for a pattern and row, also the goggles' line. */
    static MutableComponent named(Component pattern, boolean nightOnly) {
        return Component.translatable(nightOnly ? NIGHT : ALWAYS, pattern);
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return new ValueSettingsBoard(label, max, 1, List.of(Component.translatable(ALWAYS, ""), Component.translatable(NIGHT, "")),
                new ScrollOptionSettingsFormatter(WarningPattern.values()) {
                    @Override
                    public MutableComponent format(ValueSettings settings) {
                        return named(super.format(settings), settings.row() == 1);
                    }
                });
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(lamp.lightState().nightOnly() ? 1 : 0, value);
    }

    @Override
    public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
        if (settings.equals(getValueSettings())) {
            return;
        }
        lamp.lightState().setNightOnly(settings.row() == 1);
        value = settings.value();
        blockEntity.setChanged();
        blockEntity.sendData();
        playFeedbackSound(this);
    }
}
