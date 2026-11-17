package com.goldskinmc.lightworks.registry;

import com.goldskinmc.lightworks.Lightworks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * A recipe condition, true when Create is at least {@code at_least}. Create changed how processing recipes name their
 * results in 6.0.3 (an {@code id}, where 6.0.0 took a bare item name and 6.0.3 refuses one), so a Create recipe of ours
 * comes in two files, each loaded only where its format is read.
 */
public record CreateVersionCondition(String atLeast) implements ICondition {
    public static final MapCodec<CreateVersionCondition> CODEC = Codec.STRING.fieldOf("at_least")
            .xmap(CreateVersionCondition::new, CreateVersionCondition::atLeast);
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Lightworks.MOD_ID);

    static {
        CONDITIONS.register("create_version", () -> CODEC);
    }

    public static void register(IEventBus modBus) {
        CONDITIONS.register(modBus);
    }

    @Override
    public boolean test(IContext context) {
        return ModList.get().getModContainerById("create")
                .map(create -> create.getModInfo().getVersion().compareTo(new DefaultArtifactVersion(atLeast)) >= 0)
                .orElse(false);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
