package com.goldskinmc.lightworks.compat.sdl;

import com.goldskinmc.lightworks.beam.BeamSpot;
import com.goldskinmc.lightworks.compat.DynamicSpot;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import toni.sodiumdynamiclights.DynamicLightSource;
import toni.sodiumdynamiclights.DynamicLightsMode;
import toni.sodiumdynamiclights.SodiumDynamicLights;

/**
 * Beam spots as Sodium Dynamic Lights sources, for the packs that ship it, as it and LambDynamicLights refuse each
 * other. It has no API for this: a source is what its entity mixin is, so this does what that does. A source is a
 * round light, which unlike {@code LdlSpots} also reaches a little way through the wall it lands on.
 */
public final class SdlSpots {
    /** Sodium Dynamic Lights never lights further than this from a source. */
    private static final double MAX_RADIUS = 7.75;
    private static final Set<Source> LIVE = ConcurrentHashMap.newKeySet();
    private static volatile boolean suppressed;

    private SdlSpots() {
    }

    /** False when the player has switched dynamic lights off in its options. */
    public static boolean enabled() {
        return SodiumDynamicLights.get() != null && SodiumDynamicLights.get().config.getDynamicLightsMode().isEnabled();
    }

    public static DynamicSpot create() {
        Source source = new Source();
        LIVE.add(source);
        return source;
    }

    public static void setSuppressed(boolean value) {
        suppressed = value;
        LIVE.forEach(source -> source.changed = true);
    }

    public static String status() {
        return String.format("Sodium Dynamic Lights spots %d, %s", LIVE.size(), enabled() ? "on" : "off in its options");
    }

    private static final class Source implements DynamicLightSource, DynamicSpot {
        @Nullable
        private volatile BeamSpot spot;
        private volatile boolean changed = true;
        private long lastUpdate;
        /** Chunk sections under the light, render thread only. */
        private LongOpenHashSet tracked = new LongOpenHashSet();

        @Override
        public void set(@Nullable BeamSpot next) {
            if (!Objects.equals(spot, next)) {
                spot = next;
                changed = true;
            }
            // Here and not on creation: it ignores sources while its dynamic lights are off, and forgets them with the world
            if (next != null && Minecraft.getInstance().level != null && !SodiumDynamicLights.get().containsLightSource(this)) {
                SodiumDynamicLights.get().addLightSource(this);
            }
        }

        @Override
        public void remove() {
            if (LIVE.remove(this)) {
                spot = null;
                SodiumDynamicLights.get().removeLightSource(this);
            }
        }

        @Override
        public double sdl$getDynamicLightX() {
            BeamSpot current = spot;
            return current == null ? 0 : current.spotX();
        }

        @Override
        public double sdl$getDynamicLightY() {
            BeamSpot current = spot;
            return current == null ? 0 : current.spotY();
        }

        @Override
        public double sdl$getDynamicLightZ() {
            BeamSpot current = spot;
            return current == null ? 0 : current.spotZ();
        }

        @Override
        public Level sdl$getDynamicLightLevel() {
            return Minecraft.getInstance().level;
        }

        @Override
        public void sdl$resetDynamicLight() {
            changed = true;
        }

        @Override
        public int sdl$getLuminance() {
            BeamSpot current = spot;
            return current == null || suppressed ? 0 : (int) Math.round(current.peak());
        }

        @Override
        public void sdl$dynamicLightTick() {
        }

        @Override
        public boolean sdl$shouldUpdateDynamicLight() {
            DynamicLightsMode mode = SodiumDynamicLights.get().config.getDynamicLightsMode();
            if (!mode.isEnabled()) {
                return false;
            }
            long now = System.currentTimeMillis();
            if (mode.hasDelay() && now < lastUpdate + mode.getDelay()) {
                return false;
            }
            lastUpdate = now;
            return true;
        }

        @Override
        public boolean sodiumdynamiclights$updateDynamicLight(@NotNull LevelRenderer renderer) {
            if (!changed || !sdl$shouldUpdateDynamicLight()) {
                return false;
            }
            changed = false;
            LongOpenHashSet sections = new LongOpenHashSet();
            BeamSpot current = spot;
            if (current != null && sdl$getLuminance() > 0) {
                int[] b = current.bounds(sdl$getLuminance() / MAX_RADIUS, MAX_RADIUS);
                // A block past the light too: a face's smooth light reads the blocks next to it, across a section's edge
                for (int x = SectionPos.blockToSectionCoord(b[0] - 1); x <= SectionPos.blockToSectionCoord(b[3] + 1); x++) {
                    for (int y = SectionPos.blockToSectionCoord(b[1] - 1); y <= SectionPos.blockToSectionCoord(b[4] + 1); y++) {
                        for (int z = SectionPos.blockToSectionCoord(b[2] - 1); z <= SectionPos.blockToSectionCoord(b[5] + 1); z++) {
                            sections.add(BlockPos.asLong(x, y, z));
                            SodiumDynamicLights.scheduleChunkRebuild(renderer, x, y, z);
                        }
                    }
                }
            }
            tracked.removeAll(sections);
            sodiumdynamiclights$scheduleTrackedChunksRebuild(renderer);
            tracked = sections;
            return true;
        }

        @Override
        public void sodiumdynamiclights$scheduleTrackedChunksRebuild(@NotNull LevelRenderer renderer) {
            for (LongIterator it = tracked.iterator(); it.hasNext(); ) {
                SodiumDynamicLights.scheduleChunkRebuild(renderer, it.nextLong());
            }
        }
    }
}
