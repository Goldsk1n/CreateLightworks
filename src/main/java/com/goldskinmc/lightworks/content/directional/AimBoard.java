package com.goldskinmc.lightworks.content.directional;

import com.goldskinmc.lightworks.beam.PanTilt;
import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.content.SideValueBox;
import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One of a directional light's two value boards, Pan or Tilt, set like Create's Valve Handle: hold
 * right-click on its value box and pick a row (which way) and a value (how far). Pan has rows ⟲ and ⟳,
 * tilt ↑ and ↓ (only the one that points away from a floor or ceiling). Two boxes sit on each side face
 * ({@link Slot}). The values live in the light (its {@link PanTilt}), not in this behaviour, and the boards
 * are hidden on a Pan-Tilt Head, which aims the light instead.
 */
public class AimBoard extends ScrollValueBehaviour {
    public static final BehaviourType<AimBoard> PAN = new BehaviourType<>("lightworks_pan");
    public static final BehaviourType<AimBoard> TILT = new BehaviourType<>("lightworks_tilt");
    private static final Component LEFT = Component.literal("⟲").withStyle(ChatFormatting.BOLD);
    private static final Component RIGHT = Component.literal("⟳").withStyle(ChatFormatting.BOLD);
    private static final Component UP = Component.literal("↑").withStyle(ChatFormatting.BOLD);
    private static final Component DOWN = Component.literal("↓").withStyle(ChatFormatting.BOLD);

    private final DirectionalLightBlockEntity light;
    private final boolean pan;

    AimBoard(DirectionalLightBlockEntity light, boolean pan) {
        super(Component.translatable(pan ? "lightworks.aim.pan" : "lightworks.aim.tilt"), light,
                new Slot(pan, (DirectionalLightBlock) light.getBlockState().getBlock()));
        this.light = light;
        this.pan = pan;
        onlyActiveWhen(() -> light.head() == null);
    }

    @Override
    public BehaviourType<?> getType() {
        return pan ? PAN : TILT;
    }

    /** How Create's packet tells the boards apart: it gives a value to the first behaviour with the sent id. */
    @Override
    public int netId() {
        return pan ? 1 : 2;
    }

    /** The light copies its aim on the Clipboard: Pan and Tilt would overwrite each other under one key. */
    @Override
    public boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side) {
        return false;
    }

    @Override
    public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player, Direction side,
            boolean simulate) {
        return false;
    }

    /** A dye click on a box goes to the light, which takes dye anywhere on the block. */
    @Override
    public boolean bypassesInput(ItemStack mainhandItem) {
        return mainhandItem.getItem() instanceof DyeItem;
    }

    /** The light saves its aim; this behaviour keeps nothing of its own. */
    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
    }

    private int fy() {
        return light.mountFacing().getStepY();
    }

    private List<Component> rows() {
        if (pan) {
            return ImmutableList.of(LEFT, RIGHT);
        }
        return fy() > 0 ? ImmutableList.of(UP) : fy() < 0 ? ImmutableList.of(DOWN) : ImmutableList.of(UP, DOWN);
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        int max = pan ? PanTilt.maxPan(fy()) : 90;
        return new ValueSettingsBoard(label, max, pan && max == 180 ? 45 : 15, rows(),
                new ValueSettingsFormatter(this::formatSettings));
    }

    /** The signed angle as a board row and a size: the first row is left or up (down on a ceiling). */
    @Override
    public ValueSettings getValueSettings() {
        PanTilt aim = light.panTilt();
        if (pan) {
            return new ValueSettings(aim.pan() < 0 ? 0 : 1, Math.abs(aim.pan()));
        }
        return new ValueSettings(aim.tilt() < 0 && fy() == 0 ? 1 : 0, Math.abs(aim.tilt()));
    }

    @Override
    public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
        if (settings.equals(getValueSettings())) {
            return;
        }
        PanTilt aim = light.panTilt();
        boolean negative = pan ? settings.row() == 0 : fy() < 0 || settings.row() == 1;
        int value = negative ? -settings.value() : settings.value();
        light.setPanTilt(pan ? new PanTilt(value, aim.tilt()) : new PanTilt(aim.pan(), value));
        playFeedbackSound(this);
    }

    /** What the value box shows: the arrow of its row and the angle. */
    @Override
    public String formatValue() {
        return formatSettings(getValueSettings()).getString();
    }

    private MutableComponent formatSettings(ValueSettings settings) {
        List<Component> rows = rows();
        Component arrow = rows.get(Math.min(settings.row(), rows.size() - 1));
        return Component.empty().append(arrow).append(" " + settings.value() + "°");
    }

    /**
     * Value box positions: the same on every light, two boxes stacked on every side face from the mount, pan
     * first. They are drawn clear of the head however it is aimed, which can be off the outline or the block.
     * On the Floodlight and Headlight the top of the tilt box stands above the outline.
     */
    static class Slot extends SideValueBox {
        /** Box size, in blocks: two fill the Searchlight's 15.5 pixel height. */
        private static final float SIZE = 7.75f / 16;
        /** From the block centre to where a box is drawn, past the moving parts' furthest corner, in blocks. */
        private static final double CLEAR_OF_HEAD = 9.25 / 16;
        /** The same for the Headlight, whose head is small: 7.23 pixels, so its boxes stay on the block. */
        private static final double CLEAR_OF_SMALL_HEAD = 7.5 / 16;

        private final double out;
        private final double along;

        Slot(boolean pan, DirectionalLightBlock block) {
            super((state, side) -> side.getAxis() != state.getValue(DirectionalLightBlock.FACING).getAxis());
            out = block.lightType() == LightType.HEADLIGHT ? CLEAR_OF_SMALL_HEAD : CLEAR_OF_HEAD;
            along = pan ? SIZE / 2 : SIZE * 1.5;
            scale = SIZE;
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Direction mount = state.getValue(DirectionalLightBlock.FACING);
            Vec3 side = Vec3.atLowerCornerOf(getSide().getNormal());
            Vec3 up = Vec3.atLowerCornerOf(mount.getNormal());
            return new Vec3(0.5, 0.5, 0.5).add(side.scale(out)).add(up.scale(along - 0.5));
        }

        @Override
        public float getScale() {
            return SIZE;
        }
    }
}
