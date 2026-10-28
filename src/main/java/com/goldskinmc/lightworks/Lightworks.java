package com.goldskinmc.lightworks;

import com.goldskinmc.lightworks.registry.LightworksBlockEntities;
import com.goldskinmc.lightworks.registry.LightworksBlocks;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(Lightworks.MOD_ID)
public final class Lightworks {
    public static final String MOD_ID = "lightworks";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID);

    public Lightworks(IEventBus modBus, ModContainer container) {
        LOGGER.info("Lightworks loading");
        REGISTRATE.registerEventListeners(modBus);
        LightworksBlocks.register();
        LightworksBlockEntities.register();
        modBus.addListener(Lightworks::addToCreativeTabs);
        container.registerConfig(ModConfig.Type.SERVER, LightworksConfig.SPEC);
        REGISTRATE.addRawLang("lightworks.configuration.serverLightBlocks", "Server light blocks");
        REGISTRATE.addRawLang("lightworks.goggles.brightness.label", "Brightness:");
        REGISTRATE.addRawLang("lightworks.goggles.brightness", "%s of 15");
        REGISTRATE.addRawLang("lightworks.goggles.off_powered.label", "Off:");
        REGISTRATE.addRawLang("lightworks.goggles.off_powered", "full redstone signal");
        REGISTRATE.addRawLang("lightworks.goggles.off_unpowered.label", "Off:");
        REGISTRATE.addRawLang("lightworks.goggles.off_unpowered", "no redstone signal (inverted redstone)");
        REGISTRATE.addRawLang("lightworks.goggles.signal.label", "Redstone signal:");
        REGISTRATE.addRawLang("lightworks.goggles.signal", "%s");
        REGISTRATE.addRawLang("lightworks.goggles.aim.label", "Aim:");
        REGISTRATE.addRawLang("lightworks.goggles.aim", "%s° up, %s° round");
        REGISTRATE.addRawLang("lightworks.goggles.aim_head.label", "Aim:");
        REGISTRATE.addRawLang("lightworks.goggles.aim_head", "%s° up, %s° round, by the Pan-Tilt Head");
        REGISTRATE.addRawLang("lightworks.goggles.beam_hit.label", "Beam:");
        REGISTRATE.addRawLang("lightworks.goggles.beam_hit", "lands on %s %s blocks away, light %s");
        REGISTRATE.addRawLang("lightworks.goggles.beam_miss.label", "Beam:");
        REGISTRATE.addRawLang("lightworks.goggles.beam_miss", "reaches %s blocks without landing");
        REGISTRATE.addRawLang("lightworks.goggles.moving_dynamic.label", "Moving:");
        REGISTRATE.addRawLang("lightworks.goggles.moving_dynamic", "lit by LambDynamicLights");
        REGISTRATE.addRawLang("lightworks.goggles.moving_beam_only.label", "Moving:");
        REGISTRATE.addRawLang("lightworks.goggles.moving_beam_only", "beam only; LambDynamicLights lights the ground");
        REGISTRATE.addRawLang("lightworks.goggles.no_light_path.label", "Light:");
        REGISTRATE.addRawLang("lightworks.goggles.no_light_path", "none: server light blocks are off");
        REGISTRATE.addRawLang("lightworks.goggles.colour.label", "Colour:");
        REGISTRATE.addRawLang("lightworks.goggles.colour", "%s");
        REGISTRATE.addRawLang("lightworks.goggles.colour.default", "default");
        REGISTRATE.addRawLang("lightworks.goggles.headlight_auto.label", "Mode:");
        REGISTRATE.addRawLang("lightworks.goggles.headlight_auto", "automatic, lit while leading");
        REGISTRATE.addRawLang("lightworks.goggles.headlight_manual.label", "Mode:");
        REGISTRATE.addRawLang("lightworks.goggles.headlight_manual", "redstone override, lit both ways");
        REGISTRATE.addRawLang("lightworks.goggles.pan_tilt.label", "Pan and tilt:");
        REGISTRATE.addRawLang("lightworks.goggles.pan_tilt", "%s° round, %s° up");
        REGISTRATE.addRawLang("lightworks.goggles.tilt_signal.label", "Redstone signal:");
        REGISTRATE.addRawLang("lightworks.goggles.tilt_signal", "%s, tilts to %s°");
        REGISTRATE.addRawLang("lightworks.goggles.beacon_turning.label", "Rotor:");
        REGISTRATE.addRawLang("lightworks.goggles.beacon_turning", "turning, beams sweep round");
        REGISTRATE.addRawLang("lightworks.goggles.beacon_stopped.label", "Rotor:");
        REGISTRATE.addRawLang("lightworks.goggles.beacon_stopped", "stopped, lights where the beams land");
        REGISTRATE.addRawLang("lightworks.goggles.block_light.label", "Block light:");
        REGISTRATE.addRawLang("lightworks.goggles.block_light", "%s");
        REGISTRATE.addRawLang("lightworks.goggles.pattern.label", "Pattern:");
        REGISTRATE.addRawLang("lightworks.goggles.pattern", "%s");
        REGISTRATE.addRawLang("lightworks.goggles.brake.label", "Brake light:");
        REGISTRATE.addRawLang("lightworks.goggles.brake", "brightens for a second when the vehicle slows");
        REGISTRATE.addRawLang("lightworks.goggles.brake_now.label", "Brake light:");
        REGISTRATE.addRawLang("lightworks.goggles.brake_now", "on, the vehicle is slowing");
        REGISTRATE.addRawLang("lightworks.goggles.side.label", "Side:");
        REGISTRATE.addRawLang("lightworks.goggles.side", "%s");
        REGISTRATE.addRawLang("lightworks.navigation.port", "port (red)");
        REGISTRATE.addRawLang("lightworks.navigation.starboard", "starboard (green)");
        REGISTRATE.addRawLang("lightworks.navigation.stern", "stern (white)");
        REGISTRATE.addRawLang("lightworks.goggles.hint.aim", "Wrench: aim where you look");
        REGISTRATE.addRawLang("lightworks.goggles.hint.on_head", "Aimed by the Pan-Tilt Head below");
        REGISTRATE.addRawLang("lightworks.goggles.hint.dye", "Dye: tint the lens and beam");
        REGISTRATE.addRawLang("lightworks.goggles.hint.redstone", "Redstone dims it; 15 turns it off");
        REGISTRATE.addRawLang("lightworks.goggles.hint.redstone_inverted", "Redstone lights it; brighter the stronger");
        REGISTRATE.addRawLang("lightworks.goggles.hint.pan_tilt", "Shaft pans the light on top; redstone tilts it 6° a level");
        REGISTRATE.addRawLang("lightworks.goggles.hint.beacon", "Shaft speed sets the sweep");
        REGISTRATE.addRawLang("lightworks.goggles.hint.pattern", "Scroll the value box to change the pattern");
        REGISTRATE.addRawLang("lightworks.goggles.hint.navigation", "Wrench: change the side colour");
        REGISTRATE.addRawLang("lightworks.warning_light.pattern", "Pattern");
        REGISTRATE.addRawLang("create.lightworks.warning_pattern.steady", "Steady");
        REGISTRATE.addRawLang("create.lightworks.warning_pattern.blink", "Blink");
        REGISTRATE.addRawLang("create.lightworks.warning_pattern.pulse", "Pulse");
        REGISTRATE.addRawLang("create.lightworks.warning_pattern.flash", "Flash");
        REGISTRATE.addRawLang("lightworks.configuration.invertRedstone", "Invert redstone");
        REGISTRATE.addRawLang("lightworks.configuration.maxBeamRange", "Longest beam");
        REGISTRATE.addRawLang("lightworks.configuration.beamVisual", "Visible beam");
        REGISTRATE.addRawLang("lightworks.configuration.beamVisualWithShaders", "Visible beam with shaders");
        REGISTRATE.addRawLang("lightworks.configuration.debugOverlay", "Debug overlay (F3)");
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(LightworksBlocks.SEARCHLIGHT);
            event.accept(LightworksBlocks.FLOODLIGHT);
            event.accept(LightworksBlocks.PAN_TILT_HEAD);
            event.accept(LightworksBlocks.ROTATING_BEACON);
            event.accept(LightworksBlocks.WARNING_LIGHT);
            event.accept(LightworksBlocks.HEADLIGHT);
            event.accept(LightworksBlocks.TAIL_LIGHT);
            event.accept(LightworksBlocks.NAVIGATION_LIGHT);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
