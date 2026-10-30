package com.goldskinmc.lightworks.registry;

import static com.goldskinmc.lightworks.Lightworks.REGISTRATE;

import com.goldskinmc.lightworks.content.directional.DirectionalLightBlock;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.content.directional.DirectionalLightRenderer;
import com.goldskinmc.lightworks.content.beacon.RotatingBeaconBlockEntity;
import com.goldskinmc.lightworks.content.beacon.RotatingBeaconRenderer;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadBlockEntity;
import com.goldskinmc.lightworks.content.signal.EdgeLightBlockEntity;
import com.goldskinmc.lightworks.content.signal.SignalLampBlock;
import com.goldskinmc.lightworks.content.signal.SignalLampBlockEntity;
import com.goldskinmc.lightworks.content.signal.EdgeLightRenderer;
import com.goldskinmc.lightworks.content.signal.SignalLampRenderer;
import com.goldskinmc.lightworks.content.pantilt.PanTiltHeadRenderer;
import com.goldskinmc.lightworks.light.BeamLightBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;

public final class LightworksBlockEntities {
    public static final BlockEntityEntry<DirectionalLightBlockEntity> SEARCHLIGHT = directional("searchlight",
            LightworksBlocks.SEARCHLIGHT);
    public static final BlockEntityEntry<DirectionalLightBlockEntity> FLOODLIGHT = directional("floodlight",
            LightworksBlocks.FLOODLIGHT);
    public static final BlockEntityEntry<DirectionalLightBlockEntity> HEADLIGHT = directional("headlight",
            LightworksBlocks.HEADLIGHT);

    public static final BlockEntityEntry<PanTiltHeadBlockEntity> PAN_TILT_HEAD = REGISTRATE
            .blockEntity("pan_tilt_head", PanTiltHeadBlockEntity::new)
            .validBlocks(LightworksBlocks.PAN_TILT_HEAD)
            .renderer(() -> PanTiltHeadRenderer::new)
            .register();

    public static final BlockEntityEntry<RotatingBeaconBlockEntity> ROTATING_BEACON = REGISTRATE
            .blockEntity("rotating_beacon", RotatingBeaconBlockEntity::new)
            .validBlocks(LightworksBlocks.ROTATING_BEACON)
            .renderer(() -> RotatingBeaconRenderer::new)
            .register();

    public static final BlockEntityEntry<SignalLampBlockEntity> WARNING_LIGHT = signal("warning_light",
            LightworksBlocks.WARNING_LIGHT);
    public static final BlockEntityEntry<SignalLampBlockEntity> TAIL_LIGHT = signal("tail_light",
            LightworksBlocks.TAIL_LIGHT);
    public static final BlockEntityEntry<SignalLampBlockEntity> NAVIGATION_LIGHT = signal("navigation_light",
            LightworksBlocks.NAVIGATION_LIGHT);

    public static final BlockEntityEntry<EdgeLightBlockEntity> EDGE_LIGHT = REGISTRATE
            .blockEntity("edge_light", EdgeLightBlockEntity::new)
            .renderer(() -> EdgeLightRenderer::new)
            .validBlocks(LightworksBlocks.EDGE_LIGHT)
            .register();

    public static final BlockEntityEntry<BeamLightBlockEntity> BEAM_LIGHT = REGISTRATE
            .<BeamLightBlockEntity>blockEntity("beam_light", (type, pos, state) -> new BeamLightBlockEntity(pos, state))
            .validBlocks(LightworksBlocks.BEAM_LIGHT)
            .register();

    private LightworksBlockEntities() {
    }

    private static BlockEntityEntry<SignalLampBlockEntity> signal(String name,
            BlockEntry<? extends SignalLampBlock> block) {
        return REGISTRATE.blockEntity(name, SignalLampBlockEntity::new)
                .validBlocks(block)
                .renderer(() -> SignalLampRenderer::new)
                .register();
    }

    /** One block entity type per directional light block, all with the same class and renderer. */
    private static BlockEntityEntry<DirectionalLightBlockEntity> directional(String name,
            BlockEntry<? extends DirectionalLightBlock> block) {
        return REGISTRATE.blockEntity(name, DirectionalLightBlockEntity::new)
                .validBlocks(block)
                .renderer(() -> DirectionalLightRenderer::new)
                .register();
    }

    /** Loads this class so its entries are registered. */
    public static void register() {
    }
}
