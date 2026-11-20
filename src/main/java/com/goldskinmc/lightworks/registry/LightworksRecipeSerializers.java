package com.goldskinmc.lightworks.registry;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.content.carried.FlashlightDyeingRecipe;
import com.goldskinmc.lightworks.content.carried.HeadlampFittingRecipe;
import com.goldskinmc.lightworks.content.carried.HeadlampRemovingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Crafting recipes that are code, not data: dyeing a Flashlight keeps what is on it. */
public final class LightworksRecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Lightworks.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<FlashlightDyeingRecipe>>
            FLASHLIGHT_DYEING = SERIALIZERS.register("flashlight_dyeing",
                    () -> new SimpleCraftingRecipeSerializer<>(FlashlightDyeingRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<HeadlampFittingRecipe>>
            HEADLAMP_FITTING = SERIALIZERS.register("headlamp_fitting",
                    () -> new SimpleCraftingRecipeSerializer<>(HeadlampFittingRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<HeadlampRemovingRecipe>>
            HEADLAMP_REMOVING = SERIALIZERS.register("headlamp_removing",
                    () -> new SimpleCraftingRecipeSerializer<>(HeadlampRemovingRecipe::new));

    private LightworksRecipeSerializers() {
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
