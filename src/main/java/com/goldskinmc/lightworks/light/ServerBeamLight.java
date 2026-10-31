package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.beam.BeamRays;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps a beam's {@link BeamLightBlock}s in the air just in front of what it lights: where its rays land and across
 * the surfaces between them ({@link BeamArea}), moving or removing them as the beam changes and leaving alone those
 * that stay, so a re-trace updates no light.
 * Owned by the light's block entity; server side only.
 */
public final class ServerBeamLight {
    /** Landings closer than this share one light, the strongest: block light spreads that far anyway. */
    private static final int MIN_SPACING_SQUARED = 3 * 3;
    /** The area is searched when the rays land elsewhere, and this often besides, for blocks placed off the rays. */
    private static final int AREA_REFRESH_TICKS = 40;

    private final String tagKey;
    private List<BlockPos> lights = List.of();
    private List<BeamTrace.Landing> area = List.of();
    private long areaKey;
    private long areaDue;

    public ServerBeamLight() {
        this("BeamLight");
    }

    /** @param tagKey where {@link #save} keeps the positions, so an owner with two beams can keep both */
    public ServerBeamLight(String tagKey) {
        this.tagKey = tagKey;
    }

    public boolean owns(BlockPos pos) {
        return lights.contains(pos);
    }

    /**
     * Lights what a beam lights: where its rays land and the surfaces between them.
     *
     * @param owner      position of the block entity that owns these lights
     * @param axis       unit vector the beam points along
     * @param brightness the light's brightness now, 0 to 15; 0 removes the lights
     */
    public void update(Level level, BlockPos owner, Vec3 origin, Vec3 axis, ConeFalloff shape, double lensHalf, double range,
            BeamRays rays, int brightness) {
        List<BeamTrace.Landing> landed = BeamTrace.landings(level, origin, axis, shape, lensHalf, range, rays);
        long now = level.getGameTime();
        long key = axis.hashCode();
        for (BeamTrace.Landing landing : landed) {
            key = key * 31 + landing.hit().end().hashCode();
        }
        if (key != areaKey || now >= areaDue) {
            area = BeamArea.landings(level, origin, axis, shape, lensHalf, range, landed);
            areaKey = key;
            areaDue = now + AREA_REFRESH_TICKS;
        }
        List<BeamTrace.Landing> all = new ArrayList<>(landed.size() + area.size());
        all.addAll(landed);
        all.addAll(area);
        update(level, owner, all, brightness);
    }

    /**
     * @param owner      position of the block entity that owns these lights
     * @param landings   where the beam lands, with its light at full brightness ({@link BeamTrace#landings})
     * @param brightness the light's brightness now, 0 to 15; 0 removes the lights
     */
    private void update(Level level, BlockPos owner, List<BeamTrace.Landing> landings, int brightness) {
        Map<BlockPos, Integer> wanted = new LinkedHashMap<>();
        List<BeamTrace.Landing> strongestFirst = new ArrayList<>(landings);
        strongestFirst.sort(Comparator.comparingDouble(landing -> -landing.light()));
        for (BeamTrace.Landing landing : strongestFirst) {
            int lightLevel = Mth.clamp((int) Math.round(landing.light() * brightness / 15.0), 0, 15);
            BlockPos inFront = BlockPos.containing(landing.hit().end().subtract(landing.direction().normalize().scale(0.5)));
            if (lightLevel > 0 && !inFront.equals(owner) && canUse(level, inFront, owner)
                    && wanted.keySet().stream().noneMatch(pos -> pos.distSqr(inFront) < MIN_SPACING_SQUARED)) {
                wanted.put(inFront, lightLevel);
            }
        }

        for (BlockPos old : lights) {
            if (!wanted.containsKey(old)) {
                remove(level, old);
            }
        }
        for (Map.Entry<BlockPos, Integer> light : wanted.entrySet()) {
            place(level, owner, light.getKey(), light.getValue());
        }
        lights = List.copyOf(wanted.keySet());
    }

    private static void place(Level level, BlockPos owner, BlockPos pos, int lightLevel) {
        BlockState state = level.getBlockState(pos);
        if (state.is(LightworksBlocks.BEAM_LIGHT.get())) {
            // Same spot: only the brightness can change, and changing the state keeps the block entity
            if (state.getValue(BeamLightBlock.LEVEL) != lightLevel) {
                level.setBlock(pos, state.setValue(BeamLightBlock.LEVEL, lightLevel), BeamLightBlock.QUIET_FLAGS);
            }
            return;
        }
        level.setBlock(pos, LightworksBlocks.BEAM_LIGHT.getDefaultState().setValue(BeamLightBlock.LEVEL, lightLevel),
                BeamLightBlock.QUIET_FLAGS);
        if (level.getBlockEntity(pos) instanceof BeamLightBlockEntity light) {
            light.setOwner(owner);
        }
    }

    /** Removes the lights that are still ours and whose chunks are loaded. */
    public void clear(Level level) {
        for (BlockPos pos : lights) {
            remove(level, pos);
        }
        lights = List.of();
    }

    private static void remove(Level level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getBlockState(pos).is(LightworksBlocks.BEAM_LIGHT.get())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), BeamLightBlock.QUIET_FLAGS);
        }
    }

    /** Only empty air, or a beam light this owner placed, may hold a light. */
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
        if (!lights.isEmpty()) {
            tag.putLongArray(tagKey, lights.stream().mapToLong(BlockPos::asLong).toArray());
        }
    }

    public void load(CompoundTag tag) {
        List<BlockPos> loaded = new ArrayList<>();
        if (tag.contains(tagKey, Tag.TAG_LONG)) {
            // Saved before a beam could have several lights
            loaded.add(BlockPos.of(tag.getLong(tagKey)));
        } else {
            for (long pos : tag.getLongArray(tagKey)) {
                loaded.add(BlockPos.of(pos));
            }
        }
        lights = List.copyOf(loaded);
    }
}
