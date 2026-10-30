package com.goldskinmc.lightworks.ponder;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.beam.Aim;
import com.goldskinmc.lightworks.content.LightState;
import com.goldskinmc.lightworks.content.signal.NavigationColour;
import com.goldskinmc.lightworks.content.signal.NavigationLightBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlock;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.google.common.hash.Hashing;
import com.simibubi.create.AllBlocks;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Writes the Ponder scene schematics ({@code assets/lightworks/ponder/*.nbt}) at datagen, in the
 * structure-template format Ponder loads, from the {@link Layouts}. Generating them keeps every scene
 * reproducible: change a layout, rerun {@code runData}, commit {@code src/generated/}.
 */
public final class PonderSchematics implements DataProvider {
    private final PackOutput.PathProvider paths;

    private PonderSchematics(PackOutput output) {
        paths = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "ponder");
    }

    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeClient(),
                new PonderSchematics(event.getGenerator().getPackOutput()));
    }

    static List<Schematic> all() {
        List<Schematic> all = new ArrayList<>();

        Schematic searchlight = new Schematic("searchlight", Layouts.Searchlight.SIZE, 5);
        searchlight.fill(Layouts.Searchlight.WALL_FROM, Layouts.Searchlight.WALL_TO, Blocks.STONE_BRICKS.defaultBlockState());
        searchlight.light(Layouts.Searchlight.LIGHT, LightworksBlocks.SEARCHLIGHT.getDefaultState(), Direction.UP,
                aimAt(Layouts.Searchlight.LIGHT, Direction.UP, face(Layouts.Searchlight.TARGET, Direction.WEST)));
        searchlight.set(Layouts.Searchlight.LEVER, floorLever());
        all.add(searchlight);

        Schematic floodlight = new Schematic("floodlight", Layouts.Floodlight.SIZE, 5);
        floodlight.fill(Layouts.Floodlight.PILLAR_FROM, Layouts.Floodlight.PILLAR_TO, Blocks.STONE_BRICKS.defaultBlockState());
        floodlight.light(Layouts.Floodlight.LIGHT, LightworksBlocks.FLOODLIGHT.getDefaultState(), Direction.EAST,
                aimAt(Layouts.Floodlight.LIGHT, Direction.EAST, face(Layouts.Floodlight.TARGET, Direction.UP)));
        all.add(floodlight);

        Schematic headlight = cart("headlight");
        headlight.light(Layouts.Cart.FRONT, LightworksBlocks.HEADLIGHT.getDefaultState(), Direction.EAST, Aim.STRAIGHT);
        headlight.light(Layouts.Cart.REAR, LightworksBlocks.HEADLIGHT.getDefaultState(), Direction.WEST, Aim.STRAIGHT);
        all.add(headlight);

        Schematic tail = cart("tail_light");
        tail.light(Layouts.Cart.FRONT, LightworksBlocks.HEADLIGHT.getDefaultState(), Direction.EAST, Aim.STRAIGHT);
        tail.lamp(Layouts.Cart.REAR, LightworksBlocks.TAIL_LIGHT.getDefaultState(), Direction.WEST);
        all.add(tail);

        Schematic panTilt = new Schematic("pan_tilt_head", Layouts.PanTilt.SIZE, 6);
        panTilt.set(Layouts.PanTilt.MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        panTilt.set(Layouts.PanTilt.HEAD, LightworksBlocks.PAN_TILT_HEAD.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        panTilt.light(Layouts.PanTilt.LIGHT, LightworksBlocks.SEARCHLIGHT.getDefaultState(), Direction.UP, Aim.STRAIGHT);
        panTilt.set(Layouts.PanTilt.LEVER_BASE, AllBlocks.BRASS_CASING.getDefaultState());
        panTilt.set(Layouts.PanTilt.LEVER, AllBlocks.ANALOG_LEVER.getDefaultState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        all.add(panTilt);

        Schematic beacon = new Schematic("rotating_beacon", Layouts.Beacon.SIZE, 5);
        beacon.set(Layouts.Beacon.MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        beacon.light(Layouts.Beacon.BEACON, LightworksBlocks.ROTATING_BEACON.getDefaultState(), Direction.UP, null);
        all.add(beacon);

        Schematic warning = new Schematic("warning_light", Layouts.Warning.SIZE, 4);
        warning.lamp(Layouts.Warning.LAMP, LightworksBlocks.WARNING_LIGHT.getDefaultState(), Direction.UP);
        all.add(warning);

        Schematic navigation = new Schematic("navigation_light", Layouts.Navigation.SIZE, 4);
        navigation.fill(Layouts.Navigation.DECK_FROM, Layouts.Navigation.DECK_TO, Blocks.SPRUCE_PLANKS.defaultBlockState());
        navigation.lamp(Layouts.Navigation.PORT, navigationLight(NavigationColour.PORT), Direction.UP);
        navigation.lamp(Layouts.Navigation.STARBOARD, navigationLight(NavigationColour.STARBOARD), Direction.UP);
        navigation.lamp(Layouts.Navigation.STERN, navigationLight(NavigationColour.STERN), Direction.UP);
        all.add(navigation);

        Schematic edge = new Schematic("edge_light", Layouts.Edge.SIZE, 3);
        edge.fill(Layouts.Edge.STRIP_FROM, Layouts.Edge.STRIP_TO, Blocks.GRAY_CARPET.defaultBlockState());
        for (BlockPos pos : Layouts.Edge.LEFT) {
            edge.set(pos, LightworksBlocks.EDGE_LIGHT.getDefaultState());
        }
        for (BlockPos pos : Layouts.Edge.RIGHT) {
            edge.set(pos, LightworksBlocks.EDGE_LIGHT.getDefaultState());
        }
        all.add(edge);
        return all;
    }

    private static Schematic cart(String name) {
        Schematic cart = new Schematic(name, Layouts.Cart.SIZE, 4);
        cart.fill(Layouts.Cart.BODY_FROM, Layouts.Cart.BODY_TO, AllBlocks.BRASS_CASING.getDefaultState());
        return cart;
    }

    private static BlockState navigationLight(NavigationColour colour) {
        return LightworksBlocks.NAVIGATION_LIGHT.getDefaultState().setValue(NavigationLightBlock.COLOUR, colour);
    }

    private static BlockState floorLever() {
        return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.SOUTH);
    }

    /** Centre of the given face of a block. */
    static Vec3 face(BlockPos pos, Direction side) {
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(side.getNormal()).scale(0.5));
    }

    /** The aim that points a light at {@code target} from its block centre. */
    static Aim aimAt(BlockPos light, Direction facing, Vec3 target) {
        Vec3 d = target.subtract(Vec3.atCenterOf(light));
        return Aim.fromDirection(facing.getStepX(), facing.getStepY(), facing.getStepZ(), d.x, d.y, d.z);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (Schematic schematic : all()) {
            byte[] bytes = schematic.toBytes();
            writes.add(CompletableFuture.runAsync(() -> {
                try {
                    output.writeIfNeeded(paths.file(Lightworks.id(schematic.name), "nbt"), bytes,
                            Hashing.sha1().hashBytes(bytes));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, Util.backgroundExecutor()));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Lightworks Ponder schematics";
    }

    /** One scene's blocks on a checkered base plate, saved like a structure block would. */
    static final class Schematic {
        final String name;
        private final int size;
        private final int height;
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private final Map<BlockPos, CompoundTag> blockEntities = new LinkedHashMap<>();

        Schematic(String name, int size, int height) {
            this.name = name;
            this.size = size;
            this.height = height;
            // Dark plate: beams are additive and would vanish against Ponder's usual white
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    set(new BlockPos(x, 0, z), ((x + z) % 2 == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES)
                            .defaultBlockState());
                }
            }
        }

        void set(BlockPos pos, BlockState state) {
            blocks.put(pos, state);
        }

        void fill(BlockPos from, BlockPos to, BlockState state) {
            BlockPos.betweenClosed(from, to).forEach(pos -> set(pos.immutable(), state));
        }

        /** A beam light, with its aim stored the way its block entity saves it (null: no aim, a beacon). */
        void light(BlockPos pos, BlockState state, Direction facing, @Nullable Aim aim) {
            set(pos, state.setValue(BlockStateProperties.FACING, facing));
            LightState light = new LightState();
            if (aim != null) {
                light.setAim(aim);
            }
            blockEntity(pos, state, light);
        }

        /** A signal lamp, lit as placed. */
        void lamp(BlockPos pos, BlockState state, Direction facing) {
            SignalLampBlock block = (SignalLampBlock) state.getBlock();
            set(pos, state.setValue(BlockStateProperties.FACING, facing)
                    .setValue(SignalLampBlock.LIGHT, block.lightType().placedLight()));
            blockEntity(pos, state, new LightState());
        }

        private void blockEntity(BlockPos pos, BlockState state, LightState light) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            light.write(tag);
            blockEntities.put(pos, tag);
        }

        byte[] toBytes() {
            List<BlockState> palette = new ArrayList<>();
            ListTag blockList = new ListTag();
            blocks.forEach((pos, state) -> {
                int index = palette.indexOf(state);
                if (index < 0) {
                    index = palette.size();
                    palette.add(state);
                }
                CompoundTag block = new CompoundTag();
                block.put("pos", ints(pos.getX(), pos.getY(), pos.getZ()));
                block.putInt("state", index);
                CompoundTag blockEntity = blockEntities.get(pos);
                if (blockEntity != null) {
                    block.put("nbt", blockEntity.copy());
                }
                blockList.add(block);
            });
            ListTag paletteList = new ListTag();
            palette.forEach(state -> paletteList.add(NbtUtils.writeBlockState(state)));

            CompoundTag root = new CompoundTag();
            root.put("size", ints(size, height, size));
            root.put("palette", paletteList);
            root.put("blocks", blockList);
            root.put("entities", new ListTag());
            NbtUtils.addCurrentDataVersion(root);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try {
                NbtIo.writeCompressed(root, bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return bytes.toByteArray();
        }

        private static ListTag ints(int... values) {
            ListTag list = new ListTag();
            for (int value : values) {
                list.add(IntTag.valueOf(value));
            }
            return list;
        }
    }
}
