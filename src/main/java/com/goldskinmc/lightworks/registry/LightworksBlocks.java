package com.goldskinmc.lightworks.registry;

import static com.goldskinmc.lightworks.Lightworks.REGISTRATE;

import com.goldskinmc.lightworks.content.searchlight.HeadlightBlock;
import com.goldskinmc.lightworks.content.searchlight.SearchlightBlock;
import com.goldskinmc.lightworks.content.searchlight.SearchlightBlockEntity;
import com.goldskinmc.lightworks.content.searchlight.SearchlightMovement;
import com.goldskinmc.lightworks.light.BeamLightBlock;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class LightworksBlocks {
    public static final BlockEntry<SearchlightBlock> SEARCHLIGHT = REGISTRATE
            .block("searchlight", SearchlightBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f).sound(SoundType.COPPER).noOcclusion())
            // directionalBlock rotates an up-facing model, so the lens goes on the top face
            .blockstate((c, p) -> p.directionalBlock(c.get(), p.models().orientableVertical(c.getName(),
                    p.modLoc("block/searchlight_side"),
                    p.modLoc("block/searchlight_front"))))
            .onRegister(MovementBehaviour.movementBehaviour(
                    new SearchlightMovement(SearchlightBlockEntity.BEAM, false)))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Searchlight")
            .simpleItem()
            .register();

    public static final BlockEntry<HeadlightBlock> HEADLIGHT = REGISTRATE
            .block("headlight", HeadlightBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(2.0f).sound(SoundType.COPPER).noOcclusion())
            .blockstate((c, p) -> p.directionalBlock(c.get(), p.models().orientableVertical(c.getName(),
                    p.modLoc("block/headlight_side"),
                    p.modLoc("block/headlight_front"))))
            .onRegister(MovementBehaviour.movementBehaviour(
                    new SearchlightMovement(SearchlightBlockEntity.HEADLIGHT_BEAM, true)))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Headlight")
            .simpleItem()
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

    /** Loads this class so its entries are registered. */
    public static void register() {
    }
}
