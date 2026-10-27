package com.goldskinmc.lightworks.compat.ldl;

import com.goldskinmc.lightworks.Lightworks;
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import org.jetbrains.annotations.Nullable;

/**
 * LambDynamicLights entrypoint, declared in neoforge.mods.toml under {@code yumi:entrypoints}.
 * LambDynamicLights only calls it on the client, and only when it is installed.
 */
public final class LightworksDynamicLights implements DynamicLightsInitializer {
    private static @Nullable DynamicLightBehaviorManager behaviors;

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        behaviors = context.dynamicLightBehaviorManager();
        Lightworks.LOGGER.info("LambDynamicLights detected, dynamic light path enabled");
    }

    // Older entrypoint form, still abstract in the 1.21.1 API; the context overload above is the one used
    @Override
    @SuppressWarnings("removal")
    public void onInitializeDynamicLights(ItemLightSourceManager itemLightSourceManager) {
    }

    /** The behaviour manager, or null when LambDynamicLights is absent. */
    public static @Nullable DynamicLightBehaviorManager behaviors() {
        return behaviors;
    }
}
