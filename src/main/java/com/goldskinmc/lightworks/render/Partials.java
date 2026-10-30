package com.goldskinmc.lightworks.render;

import com.goldskinmc.lightworks.Lightworks;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Models drawn by our renderers rather than as block models: moving parts (heads, yokes, rotors) and
 * lenses, which glow, each with a {@code lens_core} drawn over it while lit. Each is a JSON model under {@code models/block/<block>/<part>.json}. Registered
 * from {@code LightworksClient}.
 */
public final class Partials {
    private static final String[] PATHS = {
            "searchlight/yoke", "searchlight/head", "searchlight/lens", "searchlight/lens_core",
            "floodlight/yoke", "floodlight/head", "floodlight/lens", "floodlight/lens_core",
            "headlight/head", "headlight/lens", "headlight/lens_core",
            "pan_tilt_head/turntable",
            "rotating_beacon/rotor", "rotating_beacon/lens", "rotating_beacon/lens_core",
            "warning_light/lens", "warning_light/lens_core", "tail_light/lens", "tail_light/lens_core",
            "navigation_light/lens", "navigation_light/lens_core", "edge_light/lens",
    };
    private static final Map<String, ModelResourceLocation> MODELS = new LinkedHashMap<>();

    static {
        for (String path : PATHS) {
            MODELS.put(path, ModelResourceLocation.standalone(Lightworks.id("block/" + path)));
        }
    }

    private Partials() {
    }

    public static void register(ModelEvent.RegisterAdditional event) {
        MODELS.values().forEach(event::register);
    }

    /** The part {@code <block>/<part>}; it must be listed in {@link #PATHS}. */
    public static BakedModel get(String path) {
        ModelResourceLocation location = MODELS.get(path);
        if (location == null) {
            throw new IllegalArgumentException("Unknown partial model " + path);
        }
        return Minecraft.getInstance().getModelManager().getModel(location);
    }

    public static boolean has(String path) {
        return MODELS.containsKey(path);
    }
}
