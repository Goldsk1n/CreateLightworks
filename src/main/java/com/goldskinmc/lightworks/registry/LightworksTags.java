package com.goldskinmc.lightworks.registry;

import com.goldskinmc.lightworks.Lightworks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tags. Sable reads block weights from {@code physics_block_properties} data: lamps the size of a lantern
 * join Sable's own {@code #sable:super_light} (mass 0.25, as a lantern), the Headlight {@code #sable:light} (0.5),
 * the bigger fixtures get 0.75 from {@code data/lightworks/physics_block_properties/light_fixtures.json}, and the
 * Pan-Tilt Head keeps Sable's default of 1. Without Sable these are unused tags.
 */
public final class LightworksTags {
    public static final TagKey<Block> SABLE_LIGHT = BlockTags.create(ResourceLocation.fromNamespaceAndPath("sable", "light"));
    public static final TagKey<Block> SABLE_SUPER_LIGHT = BlockTags.create(ResourceLocation.fromNamespaceAndPath("sable", "super_light"));
    public static final TagKey<Block> LIGHT_FIXTURES = BlockTags.create(Lightworks.id("light_fixtures"));

    private LightworksTags() {
    }
}
