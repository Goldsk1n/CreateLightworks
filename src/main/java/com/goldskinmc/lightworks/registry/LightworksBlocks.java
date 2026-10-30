package com.goldskinmc.lightworks.registry;

import static com.goldskinmc.lightworks.Lightworks.REGISTRATE;

import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.content.directional.DirectionalLightMovement;
import com.goldskinmc.lightworks.content.beacon.RotatingBeaconBlock;
import com.goldskinmc.lightworks.content.beacon.RotatingBeaconMovement;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadBlock;
import com.goldskinmc.lightworks.content.signal.EdgeLightBlock;
import com.goldskinmc.lightworks.content.signal.NavigationLightBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlockEntity;
import com.goldskinmc.lightworks.content.signal.SignalLampMovement;
import com.goldskinmc.lightworks.light.BeamLightBlock;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.stress.BlockStressValues;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;

public final class LightworksBlocks {
    public static final BlockEntry<DirectionalLightBlock> SEARCHLIGHT = directional("searchlight",
            LightType.SEARCHLIGHT, () -> LightworksBlockEntities.SEARCHLIGHT.get(), Block.box(2, 0, 2, 14, 15.5, 14),
            "Searchlight", LightworksTags.LIGHT_FIXTURES);
    public static final BlockEntry<DirectionalLightBlock> FLOODLIGHT = directional("floodlight",
            LightType.FLOODLIGHT, () -> LightworksBlockEntities.FLOODLIGHT.get(), Block.box(0.5, 0, 0.5, 15.5, 12, 15.5),
            "Floodlight", LightworksTags.LIGHT_FIXTURES);
    public static final BlockEntry<DirectionalLightBlock> HEADLIGHT = directional("headlight",
            LightType.HEADLIGHT, () -> LightworksBlockEntities.HEADLIGHT.get(), Block.box(4, 0, 4, 12, 13, 12),
            "Headlight", LightworksTags.SABLE_LIGHT);

    public static final BlockEntry<PanTiltHeadBlock> PAN_TILT_HEAD = REGISTRATE
            .block("pan_tilt_head", PanTiltHeadBlock::new)
            .initialProperties(() -> Blocks.ANDESITE)
            .properties(p -> p.strength(2.0f).sound(SoundType.WOOD).noOcclusion())
            .blockstate((c, p) -> p.directionalBlock(c.get(), p.models().getExistingFile(p.modLoc("block/pan_tilt_head/base"))))
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 2.0))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_AXE)
            .lang("Pan-Tilt Head")
            .item().model((c, p) -> {
            }).build()
            .register();

    public static final BlockEntry<RotatingBeaconBlock> ROTATING_BEACON = REGISTRATE
            .block("rotating_beacon", RotatingBeaconBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f).sound(SoundType.COPPER).noOcclusion())
            .blockstate((c, p) -> p.directionalBlock(c.get(),
                    p.models().getExistingFile(p.modLoc("block/rotating_beacon/base"))))
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 1.0))
            .onRegister(MovementBehaviour.movementBehaviour(new RotatingBeaconMovement()))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, LightworksTags.LIGHT_FIXTURES)
            .lang("Rotating Beacon")
            .item().model((c, p) -> {
            }).build()
            .register();

    public static final BlockEntry<SignalLampBlock> WARNING_LIGHT = signal("warning_light", LightType.WARNING_LIGHT,
            SignalLampBlock::new, () -> LightworksBlockEntities.WARNING_LIGHT.get(), Block.box(4, 0, 4, 12, 10, 12),
            "Warning Light");
    public static final BlockEntry<SignalLampBlock> TAIL_LIGHT = signal("tail_light", LightType.TAIL_LIGHT,
            SignalLampBlock::new, () -> LightworksBlockEntities.TAIL_LIGHT.get(), Block.box(3, 0, 4, 13, 3, 12),
            "Tail and Brake Light");
    public static final BlockEntry<NavigationLightBlock> NAVIGATION_LIGHT = signal("navigation_light",
            LightType.NAVIGATION_LIGHT, NavigationLightBlock::new, () -> LightworksBlockEntities.NAVIGATION_LIGHT.get(),
            Block.box(5, 0, 5, 11, 6, 11), "Navigation Light");

    public static final BlockEntry<EdgeLightBlock> EDGE_LIGHT = REGISTRATE
            .block("edge_light", EdgeLightBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(1.0f).sound(SoundType.COPPER).noOcclusion().noCollission()
                    .lightLevel(EdgeLightBlock::lightLevel))
            .blockstate((c, p) -> {
                // One part per mount: colour and brightness only change the tint, not the model
                ModelFile model = p.models().getExistingFile(p.modLoc("block/edge_light/base"));
                MultiPartBlockStateBuilder parts = p.getMultipartBuilder(c.get());
                for (Direction facing : Direction.values()) {
                    int x = facing == Direction.DOWN ? 180 : facing.getAxis().isHorizontal() ? 90 : 0;
                    int y = facing.getAxis().isVertical() ? 0 : ((int) facing.toYRot() + 180) % 360;
                    parts.part().modelFile(model).rotationX(x).rotationY(y).addModel()
                            .condition(EdgeLightBlock.FACING, facing);
                }
            })
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, LightworksTags.SABLE_LIGHT)
            .lang("Edge Light")
            .item().model((c, p) -> {
            }).build()
            .register();

    /** Invisible light where a beam lands (server fallback path). No item, no drops. */
    public static final BlockEntry<BeamLightBlock> BEAM_LIGHT = REGISTRATE
            .block("beam_light", BeamLightBlock::new)
            .properties(p -> p.mapColor(MapColor.NONE).noCollission().noOcclusion().replaceable().noLootTable()
                    .strength(-1.0f, 3600000.8f)
                    .lightLevel(state -> state.getValue(BeamLightBlock.LEVEL))
                    .pushReaction(PushReaction.DESTROY))
            .blockstate((c, p) -> p.simpleBlock(c.get(),
                    p.models().getBuilder(c.getName()).texture("particle", p.mcLoc("block/glass"))))
            .loot((tables, block) -> {
            })
            .lang("Beam Light")
            .register();

    private LightworksBlocks() {
    }

    @FunctionalInterface
    private interface SignalLampFactory<T extends SignalLampBlock> {
        T create(BlockBehaviour.Properties properties, LightType type,
                Supplier<BlockEntityType<SignalLampBlockEntity>> blockEntity, VoxelShape upShape);
    }

    /** A lamp that glows (no beam), giving block light from its {@code LIGHT} state when placed. */
    private static <T extends SignalLampBlock> BlockEntry<T> signal(String name, LightType type,
            SignalLampFactory<T> factory, Supplier<BlockEntityType<SignalLampBlockEntity>> blockEntity,
            VoxelShape upShape, String lang) {
        return REGISTRATE.block(name, p -> factory.create(p, type, blockEntity, upShape))
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(p -> p.strength(1.5f).sound(SoundType.COPPER).noOcclusion()
                        .lightLevel(state -> state.getValue(SignalLampBlock.LIGHT)))
                .blockstate((c, p) -> p.directionalBlock(c.get(),
                        p.models().getExistingFile(p.modLoc("block/" + name + "/base"))))
                .onRegister(MovementBehaviour.movementBehaviour(new SignalLampMovement(type)))
                .tag(BlockTags.MINEABLE_WITH_PICKAXE, LightworksTags.SABLE_LIGHT)
                .lang(lang)
                .item().model((c, p) -> {
                }).build()
                .register();
    }

    /**
     * A light with an aimable beam. Models are hand-made (tools/art): the blockstate turns the base, the
     * renderer the head, and the item model already exists.
     *
     * @param upShape outline when mounted on the floor; turned for other mounts
     */
    private static BlockEntry<DirectionalLightBlock> directional(String name, LightType type,
            Supplier<BlockEntityType<DirectionalLightBlockEntity>> blockEntity, VoxelShape upShape, String lang,
            TagKey<Block> weight) {
        return REGISTRATE.block(name, p -> new DirectionalLightBlock(p, type, blockEntity, upShape))
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(p -> p.strength(2.0f).sound(SoundType.COPPER).noOcclusion())
                .blockstate((c, p) -> p.directionalBlock(c.get(),
                        p.models().getExistingFile(p.modLoc("block/" + name + "/base"))))
                .onRegister(MovementBehaviour.movementBehaviour(new DirectionalLightMovement(type)))
                .tag(BlockTags.MINEABLE_WITH_PICKAXE, weight)
                .lang(lang)
                .item().model((c, p) -> {
                }).build()
                .register();
    }

    /** Loads this class so its entries are registered. */
    public static void register() {
    }
}
