package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.beam.BeamFrame;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.beam.LightMix;
import com.goldskinmc.lightworks.debug.Timer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Coloured light painted onto the world. Minecraft's block light and LambDynamicLights are colourless, so
 * a dyed beam, or a signal lamp, tints the block faces it lights. Every frame the colours of all the lights
 * reaching a face are added up ({@link LightMix}) and the one result is multiplied over what is already
 * drawn, after the world (like the visible beam, it needs no client mod and works with Sodium). Shader packs
 * replace the blending, so it is off while one is on (client config {@code colouredLight}).
 * <p>
 * Block light cannot draw a beam's square either, so every beam, dyed or not, also has a {@linkplain #spot spot}:
 * the same faces brightened in proportion to what they show, after the colours.
 * <p>
 * The lit faces are found in world space when the light changes (a moving light: when its spot has
 * moved), only on faces that see the light, so a tint never shows through a wall. Client thread only.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class ProjectedTint {
    private static final Set<ProjectedTint> ACTIVE = new LinkedHashSet<>();
    /** Squares per face side: the falloff is sampled at their corners, so the tint fades smoothly. */
    private static final int GRID = 4;
    private static final int CORNERS_PER_FACE = (GRID + 1) * (GRID + 1);
    private static final int FLOATS_PER_FACE = CORNERS_PER_FACE * 4;
    /** Largest tinted patch, in blocks from its centre; wider cones are cut off here. */
    private static final double MAX_RADIUS = 6;
    private static final int MAX_FACES = 400;
    /** A moving beam finds its faces all the time; this covers a Floodlight on flat ground at its full range. */
    private static final int MAX_FACES_MOVING = 1000;
    /** A fixed light finds its faces only when it changes, so it may tint more. */
    private static final int MAX_FACES_FIXED = 1600;
    /** A beam nearing its limit fades over this last share of it, so it never vanishes at once. */
    private static final float LIMIT_FADE_SHARE = 0.2f;
    private static final int NOT_SHARED = -1;
    private static final List<AABB> FULL_BLOCK = List.of(new AABB(0, 0, 0, 1, 1, 1));
    /** Boxes painted per block: a stair has up to three. */
    private static final int MAX_PARTS = 4;
    /** What a beam's light keeps on a face it only grazes. */
    private static final double SLANT_FLOOR = 0.55;
    /**
     * How far the colour can go: at 1 a lit white block takes the light's colour exactly. This layer also takes
     * away light the beam did not bring, so at much more than this a beam darkens the ground it lights.
     */
    private static final float STRENGTH = 0.5f;
    /** Lifted off the face so it never fights the block for depth. */
    private static final float LIFT = 0.002f;
    private static final float DAY_FADE = 0.8f;
    /** The glow round a beam's spot is softer than the spot itself. */
    private static final float GLOW_WEIGHT = 0.6f;
    private static final long EXPIRY_TICKS = 5;
    /** How much a spot brightens what it falls on, at full strength: 1 doubles it. */
    private static final float SPOT_STRENGTH = 0.45f;
    /**
     * Share of that on walls. Minecraft shades a block's faces by the way they face, north and south 0.8, east and west
     * 0.6, as if lit by the sun. A lamp is not, and brightening keeps the difference: these bring both to the same light, measured on andesite by wall-sides.
     */
    private static final float NORTH_SOUTH_SPOT = 0.25f / SPOT_STRENGTH;
    private static final float EAST_WEST_SPOT = 0.58f / SPOT_STRENGTH;
    /**
     * How much of a beam goes straight onto what it falls on. Brightening alone leaves what is dark dark: green grass
     * has little red to brighten, and a block no ray landed on, with no block light, has nothing to brighten at all.
     */
    private static final float DIRECT_STRENGTH = 0.12f;
    /**
     * Lights whose spot stands in for a dynamic light when no mod gives them one: on dark ground one go shows little, and
     * going over it again adds up the way light would, keeping the texture. Its glow adds many faces a light, redrawn
     * every frame, so a vehicle's few lights get this, and a scene of fifty keeps its frame rate with plain spots.
     */
    private static final int MAX_STAND_INS = 8;

    private static final RenderType TINT = RenderType.create("lightworks_tint", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard("lightworks_multiply", () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
                    }, () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    }))
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** Adds to what is drawn in proportion to it, like light falling on a surface. */
    private static final RenderType SPOT = RenderType.create("lightworks_spot", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard("lightworks_brighten", () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE);
                    }, () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    }))
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** Adds to what is drawn, whatever it is. */
    private static final RenderType DIRECT = RenderType.create("lightworks_direct", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard("lightworks_direct_add", () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                    }, () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    }))
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /**
     * Whether a beam's lens sees a block, per side, while one beam finds its faces: the colour and the spot ask about
     * the same faces, and a ray to each is most of the work. A moving beam asks once per cell of two blocks.
     */
    private static final Long2BooleanOpenHashMap[] SEEN_BY_BEAM = new Long2BooleanOpenHashMap[Direction.values().length];

    /** Which faces are in the mix, per part and side: block position to place in the two arrays below. */
    private static final Long2IntOpenHashMap[] MIXED_FACES = new Long2IntOpenHashMap[MAX_PARTS * Direction.values().length];
    /** Per mixed face: its corners' x, y, z and sums of red, green, blue and strength. */
    private static float[] mixedPlaces = new float[0];
    private static float[] mixedLight = new float[0];

    static {
        for (int side = 0; side < SEEN_BY_BEAM.length; side++) {
            SEEN_BY_BEAM[side] = new Long2BooleanOpenHashMap();
        }
        for (int face = 0; face < MIXED_FACES.length; face++) {
            MIXED_FACES[face] = new Long2IntOpenHashMap();
            MIXED_FACES[face].defaultReturnValue(-1);
        }
    }

    /** Per face: (GRID + 1)^2 corners of x, y, z and light 0 to 1. */
    private float[] corners = new float[0];
    /** Per face: which block it is on, and which part of the block and side, as one number. */
    private long[] facePositions = new long[0];
    private byte[] faceSides = new byte[0];
    /** Per face: the same light at all its corners, so a spot draws it as one quad. */
    private boolean[] faceEven = new boolean[0];
    private int faces;
    private float red;
    private float green;
    private float blue;
    private float strength;
    private float skyExposure;
    /** A {@linkplain #spot spot}: brightens its faces rather than joining the mix of colours. */
    private boolean brightens;
    /** A spot with no dynamic light under it, which has to stand in for one. */
    private boolean standsIn;
    /** The colour's brightest channel, 0 to 1: a dark dye gives a faint spot. */
    private float dyeLevel = 1;
    /** Client game time after which this tint stops drawing unless refreshed; MAX for owned tints. */
    private long expires = Long.MAX_VALUE;

    /**
     * Tints the faces a beam lights: a square spot (matching the visible beam) round every place one of
     * its rays landed, and the softer glow its white light leaves round the spot.
     *
     * @param origin     lens position
     * @param axis       unit vector the beam points along
     * @param lensHalf   half the lens's width, in blocks
     * @param ends       where the beam's centre, corner and edge rays stopped on blocks
     * @param glowCentre where the beam's white light sits, just in front of the spot
     * @param glowRadius how far that light reaches, in blocks (0 for none)
     * @param brightness 0 to 1
     * @param moving     a moving light, which finds its faces all the time and keeps to the smaller limits
     */
    public void beam(Level level, Vec3 origin, Vec3 axis, ConeFalloff shape, double lensHalf, List<Vec3> ends,
            Vec3 glowCentre, double glowRadius, int rgb, float brightness, boolean moving) {
        int maxFaces = moving ? MAX_FACES_MOVING : MAX_FACES_FIXED;
        List<Region> regions = new ArrayList<>(1);
        List<Source> sources = new ArrayList<>(2);
        Source beam = beamSource(origin, axis, shape, lensHalf, moving);
        sources.add(beam);
        if (glowRadius > 0.5) {
            double radius = Math.min(MAX_RADIUS, glowRadius);
            regions.add(new Region(glowCentre, radius));
            sources.add(new Source(glowCentre, glowFalloff(glowCentre, radius, GLOW_WEIGHT), NOT_SHARED));
        }
        if (!project(level, litBlocks(level, ends, beam, maxFaces), regions, sources, maxFaces)) {
            faces = 0;
        }
        show(level, ends.get(0), rgb, brightness * limitFade(maxFaces));
    }

    /** Call before finding one beam's faces: what its lens saw last time no longer holds. */
    static void newBeam() {
        for (Long2BooleanOpenHashMap seen : SEEN_BY_BEAM) {
            seen.clear();
        }
    }

    /** 1 well inside the limit, down to 0 at it. */
    private float limitFade(int maxFaces) {
        return Math.min(1, (maxFaces - faces) / (LIMIT_FADE_SHARE * maxFaces));
    }

    /**
     * Brightens the faces a beam lights, in the same square as {@link #beam}: a beam's white light on them is
     * block light, which cannot show the beam's shape. A spot too big for the limit fades out and is left out whole:
     * a cut one would show its jagged edge, and the block light is still there.
     *
     * @param rgb        the light's colour, 0xRRGGBB
     * @param moving     a moving light, which finds its faces all the time and keeps to the smaller limits
     * @param standIn    no dynamic light lights the world under this spot: brighter, and with the glow one would leave
     * @param glowCentre where that light would sit, just in front of the spot
     * @param glowRadius how far it would reach, in blocks
     */
    public void spot(Level level, Vec3 origin, Vec3 axis, ConeFalloff shape, double lensHalf, List<Vec3> ends, int rgb,
            float brightness, boolean moving, boolean standIn, Vec3 glowCentre, double glowRadius) {
        brightens = true;
        standsIn = standIn && (standsIn && ACTIVE.contains(this) || standIns() < MAX_STAND_INS);
        int maxFaces = moving ? MAX_FACES_MOVING : MAX_FACES_FIXED;
        Source beam = beamSource(origin, axis, shape, lensHalf, moving);
        List<Region> regions = new ArrayList<>(1);
        List<Source> sources = new ArrayList<>(2);
        sources.add(beam);
        if (standsIn && glowRadius > 0.5) {
            double radius = Math.min(MAX_RADIUS, glowRadius);
            regions.add(new Region(glowCentre, radius));
            sources.add(new Source(glowCentre, glowFalloff(glowCentre, radius, GLOW_WEIGHT), NOT_SHARED));
        }
        if (!project(level, litBlocks(level, ends, beam, maxFaces), regions, sources, maxFaces)) {
            faces = 0;
        }
        show(level, ends.get(0), rgb, brightness * limitFade(maxFaces));
    }

    /**
     * The blocks with a face in a beam's light: spreads from where its rays landed, across blocks that have one,
     * so it covers the whole lit shape however slanted the surface. Stops soon after {@code maxFaces} blocks.
     */
    private static LongOpenHashSet litBlocks(Level level, List<Vec3> ends, Source beam, int maxFaces) {
        LongOpenHashSet lit = new LongOpenHashSet();
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (Vec3 end : ends) {
            enqueueAround(BlockPos.containing(end), visited, queue);
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && lit.size() <= maxFaces) {
            pos.set(queue.dequeueLong());
            if (level.isLoaded(pos) && hasLitFace(level, pos, neighbour, beam)) {
                lit.add(pos.asLong());
                enqueueAround(pos, visited, queue);
            }
        }
        return lit;
    }

    private static void enqueueAround(BlockPos centre, LongOpenHashSet visited, LongArrayFIFOQueue queue) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    long next = BlockPos.asLong(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
                    if (visited.add(next)) {
                        queue.enqueue(next);
                    }
                }
            }
        }
    }

    private static boolean hasLitFace(Level level, BlockPos pos, BlockPos.MutableBlockPos neighbour, Source source) {
        for (AABB box : boxes(level, pos)) {
            for (Direction side : Direction.values()) {
                Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
                if (source.position().subtract(faceCentre(pos, box, side)).dot(normal) <= 0
                        || coveredByNeighbour(level, pos, box, side, neighbour)) {
                    continue;
                }
                for (int a = 0; a <= GRID; a++) {
                    for (int b = 0; b <= GRID; b++) {
                        double[] p = facePoint(pos, box, side, a, b);
                        if (source.falloff().at(p[0], p[1], p[2]) > 0.01f) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * The boxes of a block that can be painted, relative to it. Only blocks drawn just as their shape says, or a flower
     * would get a box of colour round it; none for the rest.
     */
    private static List<AABB> boxes(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isSolidRender(level, pos)) {
            return FULL_BLOCK;
        }
        Block block = state.getBlock();
        if (block instanceof SlabBlock || block instanceof StairBlock || block instanceof CarpetBlock
                || block instanceof SnowLayerBlock || block instanceof FarmBlock || block instanceof DirtPathBlock) {
            return state.getShape(level, pos).toAabbs();
        }
        return List.of();
    }

    /** A face on the block's edge with a full block against it. What other blocks hide, the depth test hides. */
    private static boolean coveredByNeighbour(Level level, BlockPos pos, AABB box, Direction side, BlockPos.MutableBlockPos neighbour) {
        boolean onEdge = side.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? box.max(side.getAxis()) >= 1 : box.min(side.getAxis()) <= 0;
        return onEdge && level.getBlockState(neighbour.setWithOffset(pos, side)).isSolidRender(level, neighbour);
    }

    private static Vec3 faceCentre(BlockPos pos, AABB box, Direction side) {
        Direction.Axis axis = side.getAxis();
        double face = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(axis) : box.min(axis);
        return box.getCenter().with(axis, face).add(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Corner (a, b) of the {@link #GRID} squares on a face, lifted off the block. */
    private static double[] facePoint(BlockPos pos, AABB box, Direction side, int a, int b) {
        Direction.Axis axis = side.getAxis();
        Direction.Axis u = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis v = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        double[] p = new double[3];
        p[axis.ordinal()] = pos.get(axis) + (side.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? box.max(axis) + LIFT : box.min(axis) - LIFT);
        p[u.ordinal()] = pos.get(u) + box.min(u) + (box.max(u) - box.min(u)) * a / GRID;
        p[v.ordinal()] = pos.get(v) + box.min(v) + (box.max(v) - box.min(v)) * b / GRID;
        return p;
    }

    private static Source beamSource(Vec3 origin, Vec3 axis, ConeFalloff shape, double lensHalf, boolean moving) {
        double[] u = BeamFrame.side(axis.x, axis.y, axis.z);
        double[] v = BeamFrame.up(axis.x, axis.y, axis.z, u);
        return new Source(origin, (x, y, z) -> (float) (shape.lightInBeam(x - origin.x, y - origin.y,
                z - origin.z, axis.x, axis.y, axis.z, u[0], u[1], u[2], v[0], v[1], v[2], lensHalf) / 15),
                moving ? 1 : 0);
    }

    /** Tints the faces round a signal lamp, fading to nothing {@code radius} blocks away. */
    public void glow(Level level, Vec3 centre, double radius, int rgb, float brightness) {
        project(level, new LongOpenHashSet(), List.of(new Region(centre, radius)),
                List.of(new Source(centre, glowFalloff(centre, radius, 1), NOT_SHARED)), MAX_FACES);
        show(level, centre, rgb, brightness);
    }

    /** Light round a point, falling off with the square of the distance's fraction of {@code radius}. */
    private static Falloff glowFalloff(Vec3 centre, double radius, float weight) {
        return (x, y, z) -> {
            double d = Math.sqrt((x - centre.x) * (x - centre.x) + (y - centre.y) * (y - centre.y)
                    + (z - centre.z) * (z - centre.z));
            double t = Math.max(0, 1 - d / radius);
            return (float) (weight * t * t);
        };
    }

    /** Changes how strong the tint is without finding the faces again (a blinking pattern). */
    public void setBrightness(float brightness) {
        strength = brightness;
    }

    /**
     * Keeps this tint drawing for a few more ticks. For lamps on contraptions, which can be discarded or
     * unloaded without a word: their tint fades out by itself when nothing refreshes it.
     */
    public void keepAlive(Level level) {
        expires = level.getGameTime() + EXPIRY_TICKS;
    }

    /** Stops drawing until the next {@link #beam} or {@link #glow}. */
    public void clear() {
        ACTIVE.remove(this);
        faces = 0;
    }

    public int faces() {
        return faces;
    }

    private static int standIns() {
        int count = 0;
        for (ProjectedTint tint : ACTIVE) {
            if (tint.standsIn) {
                count++;
            }
        }
        return count;
    }

    /** Every tinted face being drawn: the self-test logs it. */
    public static int activeFaces() {
        int total = 0;
        for (ProjectedTint tint : ACTIVE) {
            total += tint.faces;
        }
        return total;
    }

    @FunctionalInterface
    private interface Falloff {
        float at(double x, double y, double z);
    }

    /** Blocks within {@code radius} of {@code centre} are looked at. */
    private record Region(Vec3 centre, double radius) {
    }

    /**
     * A light: a face it can see is tinted by its falloff.
     *
     * @param cellBits blocks in a cell that share one answer to "can it see this?", as a power of 2 ({@link #SEEN_BY_BEAM});
     *                 {@link #NOT_SHARED} for a source that is not a beam's lens
     */
    private record Source(Vec3 position, Falloff falloff, int cellBits) {
    }

    private void show(Level level, Vec3 at, int rgb, float brightness) {
        // Brightest channel to 1, so the tint changes the colour without darkening the light
        float r = (rgb >> 16 & 0xFF) / 255f;
        float g = (rgb >> 8 & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(0.01f, Math.max(r, Math.max(g, b)));
        dyeLevel = max;
        red = r / max;
        green = g / max;
        blue = b / max;
        strength = brightness;
        skyExposure = level.getBrightness(LightLayer.SKY, BlockPos.containing(at)) / 15f;
        if (faces > 0) {
            ACTIVE.add(this);
        } else {
            ACTIVE.remove(this);
        }
    }

    /**
     * Finds the exposed faces of paintable blocks among {@code blocks} and in the regions; each face is tinted by the
     * strongest of the sources that face it and see it. False if it stopped at {@code maxFaces}.
     */
    private boolean project(Level level, LongOpenHashSet blocks, List<Region> regions, List<Source> sources, int maxFaces) {
        faces = 0;
        LongOpenHashSet seen = new LongOpenHashSet();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        List<Falloff> lighting = new ArrayList<>(sources.size());
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        for (long at : blocks) {
            if (faces >= maxFaces) {
                return false;
            }
            seen.add(at);
            projectBlock(level, block.set(at), sources, neighbour, lighting);
        }
        for (Region region : regions) {
            int reach = (int) Math.ceil(region.radius());
            BlockPos middle = BlockPos.containing(region.centre());
            double limit = (region.radius() + 1) * (region.radius() + 1);
            for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-reach, -reach, -reach),
                    middle.offset(reach, reach, reach))) {
                if (faces >= maxFaces) {
                    return false;
                }
                if (Vec3.atCenterOf(pos).distanceToSqr(region.centre()) > limit || !seen.add(pos.asLong())
                        || boxes(level, pos).isEmpty()) {
                    continue;
                }
                projectBlock(level, pos, sources, neighbour, lighting);
            }
        }
        return true;
    }

    private void projectBlock(Level level, BlockPos pos, List<Source> sources, BlockPos.MutableBlockPos neighbour,
            List<Falloff> lighting) {
        List<AABB> boxes = boxes(level, pos);
        for (int part = 0; part < Math.min(boxes.size(), MAX_PARTS); part++) {
            AABB box = boxes.get(part);
            for (Direction side : Direction.values()) {
                if (coveredByNeighbour(level, pos, box, side, neighbour)) {
                    continue;
                }
                Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
                Vec3 faceCentre = faceCentre(pos, box, side);
                lighting.clear();
                for (Source source : sources) {
                    if (source.position().subtract(faceCentre).dot(normal) > 0
                            && sees(level, source, side, faceCentre, normal, pos)) {
                        lighting.add(source.falloff());
                    }
                }
                if (lighting.size() == 1) {
                    addFace(pos, box, part, side, facing(sources, lighting.get(0), faceCentre, normal));
                } else if (lighting.size() > 1) {
                    List<Falloff> both = List.copyOf(lighting);
                    for (int i = 0; i < both.size(); i++) {
                        lighting.set(i, facing(sources, both.get(i), faceCentre, normal));
                    }
                    List<Falloff> turned = List.copyOf(lighting);
                    addFace(pos, box, part, side, (x, y, z) -> {
                        float most = 0;
                        for (Falloff falloff : turned) {
                            most = Math.max(most, falloff.at(x, y, z));
                        }
                        return most;
                    });
                }
            }
        }
    }

    /**
     * A beam's light on a face it meets at a slant: the same light over more surface looks dimmer. Not all the way down
     * to nothing as the cosine would, so a level beam's ground stays lit, which is what block light does there too.
     */
    private static Falloff facing(List<Source> sources, Falloff falloff, Vec3 faceCentre, Vec3 normal) {
        Source beam = sources.get(0);
        if (beam.cellBits() == NOT_SHARED || beam.falloff() != falloff) {
            return falloff;
        }
        double cos = Math.max(0, beam.position().subtract(faceCentre).normalize().dot(normal));
        float share = (float) (SLANT_FLOOR + (1 - SLANT_FLOOR) * cos);
        return (x, y, z) -> falloff.at(x, y, z) * share;
    }

    private static boolean sees(Level level, Source source, Direction side, Vec3 faceCentre, Vec3 normal, BlockPos pos) {
        int bits = source.cellBits();
        if (bits == NOT_SHARED) {
            return sees(level, source.position(), faceCentre, normal, pos);
        }
        long cell = BlockPos.asLong(pos.getX() >> bits, pos.getY() >> bits, pos.getZ() >> bits);
        Long2BooleanOpenHashMap seen = SEEN_BY_BEAM[side.ordinal()];
        if (seen.containsKey(cell)) {
            return seen.get(cell);
        }
        boolean answer = sees(level, source.position(), faceCentre, normal, pos);
        seen.put(cell, answer);
        return answer;
    }

    /** True when nothing stands between the light and the face. */
    private static boolean sees(Level level, Vec3 source, Vec3 faceCentre, Vec3 normal, BlockPos pos) {
        Vec3 target = faceCentre.add(normal.scale(0.05));
        BlockHitResult hit = level.clip(new ClipContext(source, target, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private void addFace(BlockPos pos, AABB box, int part, Direction side, Falloff falloff) {
        int base = faces * FLOATS_PER_FACE;
        if (corners.length < base + FLOATS_PER_FACE) {
            corners = Arrays.copyOf(corners, Math.max(FLOATS_PER_FACE * 8, corners.length * 2));
        }
        float lit = 0;
        float least = Float.MAX_VALUE;
        int i = base;
        for (int a = 0; a <= GRID; a++) {
            for (int b = 0; b <= GRID; b++) {
                double[] p = facePoint(pos, box, side, a, b);
                float light = falloff.at(p[0], p[1], p[2]);
                corners[i++] = (float) p[0];
                corners[i++] = (float) p[1];
                corners[i++] = (float) p[2];
                corners[i++] = light;
                lit = Math.max(lit, light);
                least = Math.min(least, light);
            }
        }
        if (lit > 0.01f) {
            if (facePositions.length <= faces) {
                facePositions = Arrays.copyOf(facePositions, Math.max(8, faces * 2));
                faceSides = Arrays.copyOf(faceSides, facePositions.length);
                faceEven = Arrays.copyOf(faceEven, facePositions.length);
            }
            facePositions[faces] = pos.asLong();
            faceSides[faces] = (byte) (part * Direction.values().length + side.ordinal());
            faceEven[faces] = least == lit;
            faces++;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Shader packs replace the blending, which would turn the tint into glaring patches
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()
                || !LightworksClientConfig.colouredLight() || !ClientSafety.works(ClientSafety.LAYERS)) {
            return;
        }
        try {
            draw(event);
        } catch (RuntimeException e) {
            ClientSafety.failed(ClientSafety.LAYERS, e);
        }
    }

    private static void draw(RenderLevelStageEvent event) {
        long start = Timer.start();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(TINT);
        float day = BeamVisual.daylight();
        poseStack.pushPose();
        try {
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            Matrix4f pose = poseStack.last().pose();
            long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
            ACTIVE.removeIf(tint -> now > tint.expires);
            int mixed = 0;
            for (Long2IntOpenHashMap sideFaces : MIXED_FACES) {
                sideFaces.clear();
            }
            for (ProjectedTint tint : ACTIVE) {
                float amount = STRENGTH * tint.strength * (1 - DAY_FADE * day * tint.skyExposure);
                if (amount > 0.01f && !tint.brightens) {
                    mixed = tint.addToMix(amount, mixed);
                }
            }
            drawMix(out, pose, mixed);
            buffers.endBatch(TINT);
            VertexConsumer spots = buffers.getBuffer(SPOT);
            for (ProjectedTint tint : ACTIVE) {
                float amount = SPOT_STRENGTH * tint.dyeLevel * tint.strength * (1 - DAY_FADE * day * tint.skyExposure);
                if (amount > 0.01f && tint.brightens) {
                    tint.drawSpot(spots, pose, amount, true);
                    // Not by day: there is no darkness for it to light
                    float standIn = SPOT_STRENGTH * tint.dyeLevel * tint.strength * (1 - day * tint.skyExposure);
                    if (tint.standsIn && standIn > 0.01f) {
                        tint.drawSpot(spots, pose, standIn, true);
                    }
                }
            }
            buffers.endBatch(SPOT);
            VertexConsumer direct = buffers.getBuffer(DIRECT);
            for (ProjectedTint tint : ACTIVE) {
                float amount = DIRECT_STRENGTH * tint.dyeLevel * tint.strength * (1 - DAY_FADE * day * tint.skyExposure);
                if (amount > 0.002f && tint.brightens) {
                    tint.drawSpot(direct, pose, amount, false);
                }
            }
            buffers.endBatch(DIRECT);
        } finally {
            poseStack.popPose();
        }
        Timer.TINT.stop(start);
    }

    /** Adds this light to the faces it reaches; gives how many faces are in the mix now. */
    private int addToMix(float amount, int mixed) {
        for (int f = 0; f < faces; f++) {
            Long2IntOpenHashMap sideFaces = MIXED_FACES[faceSides[f]];
            int place = sideFaces.get(facePositions[f]);
            int from = f * FLOATS_PER_FACE;
            if (place < 0) {
                place = mixed++;
                sideFaces.put(facePositions[f], place);
                if (mixedLight.length < mixed * FLOATS_PER_FACE) {
                    int size = Math.max(FLOATS_PER_FACE * 64, mixedLight.length * 2);
                    mixedLight = Arrays.copyOf(mixedLight, size);
                    mixedPlaces = Arrays.copyOf(mixedPlaces, size);
                }
                int to = place * FLOATS_PER_FACE;
                Arrays.fill(mixedLight, to, to + FLOATS_PER_FACE, 0);
                for (int c = 0; c < CORNERS_PER_FACE; c++) {
                    System.arraycopy(corners, from + c * 4, mixedPlaces, to + c * 4, 3);
                }
            }
            int to = place * FLOATS_PER_FACE;
            for (int c = 0; c < CORNERS_PER_FACE; c++) {
                float k = amount * corners[from + c * 4 + 3];
                mixedLight[to + c * 4] += k * red;
                mixedLight[to + c * 4 + 1] += k * green;
                mixedLight[to + c * 4 + 2] += k * blue;
                mixedLight[to + c * 4 + 3] += k;
            }
        }
        return mixed;
    }

    /** @param evenWalls makes up for the shading of walls ({@link #NORTH_SOUTH_SPOT}), for what brightens in proportion */
    private void drawSpot(VertexConsumer out, Matrix4f pose, float amount, boolean evenWalls) {
        int row = GRID + 1;
        for (int f = 0; f < faces; f++) {
            int base = f * FLOATS_PER_FACE;
            float light = evenWalls ? amount * wallShare(faceSides[f] % Direction.values().length) : amount;
            if (faceEven[f]) {
                spotCorner(out, pose, base, light);
                spotCorner(out, pose, base + GRID * row * 4, light);
                spotCorner(out, pose, base + (GRID * row + GRID) * 4, light);
                spotCorner(out, pose, base + GRID * 4, light);
                continue;
            }
            for (int a = 0; a < GRID; a++) {
                for (int b = 0; b < GRID; b++) {
                    spotCorner(out, pose, base + (a * row + b) * 4, light);
                    spotCorner(out, pose, base + ((a + 1) * row + b) * 4, light);
                    spotCorner(out, pose, base + ((a + 1) * row + b + 1) * 4, light);
                    spotCorner(out, pose, base + (a * row + b + 1) * 4, light);
                }
            }
        }
    }

    private static float wallShare(int side) {
        return switch (Direction.values()[side].getAxis()) {
            case Z -> NORTH_SOUTH_SPOT;
            case X -> EAST_WEST_SPOT;
            case Y -> 1;
        };
    }

    private void spotCorner(VertexConsumer out, Matrix4f pose, int i, float amount) {
        float k = amount * corners[i + 3];
        out.addVertex(pose, corners[i], corners[i + 1], corners[i + 2]).setColor(k * red, k * green, k * blue, 1f);
    }

    private static void drawMix(VertexConsumer out, Matrix4f pose, int mixed) {
        int row = GRID + 1;
        for (int f = 0; f < mixed; f++) {
            int base = f * FLOATS_PER_FACE;
            if (mixIsEven(base)) {
                corner(out, pose, base);
                corner(out, pose, base + GRID * row * 4);
                corner(out, pose, base + (GRID * row + GRID) * 4);
                corner(out, pose, base + GRID * 4);
                continue;
            }
            for (int a = 0; a < GRID; a++) {
                for (int b = 0; b < GRID; b++) {
                    corner(out, pose, base + (a * row + b) * 4);
                    corner(out, pose, base + ((a + 1) * row + b) * 4);
                    corner(out, pose, base + ((a + 1) * row + b + 1) * 4);
                    corner(out, pose, base + (a * row + b + 1) * 4);
                }
            }
        }
    }

    /** True when every corner of a mixed face has the same light: most of a big beam, drawn as one quad. */
    private static boolean mixIsEven(int base) {
        for (int i = 4; i < FLOATS_PER_FACE; i++) {
            if (mixedLight[base + i] != mixedLight[base + i % 4]) {
                return false;
            }
        }
        return true;
    }

    /** One corner: white (no change) where unlit, the mix of the lights reaching it where lit. */
    private static void corner(VertexConsumer out, Matrix4f pose, int i) {
        float r = mixedLight[i];
        float g = mixedLight[i + 1];
        float b = mixedLight[i + 2];
        float k = mixedLight[i + 3];
        out.addVertex(pose, mixedPlaces[i], mixedPlaces[i + 1], mixedPlaces[i + 2])
                .setColor(LightMix.channel(r, r, g, b, k), LightMix.channel(g, r, g, b, k),
                        LightMix.channel(b, r, g, b, k), 1f);
    }

    @SubscribeEvent
    public static void onLeaveWorld(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
    }
}
