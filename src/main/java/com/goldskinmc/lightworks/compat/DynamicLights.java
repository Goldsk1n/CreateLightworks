package com.goldskinmc.lightworks.compat;

import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.compat.ldl.LdlSpots;
import com.goldskinmc.lightworks.compat.sdl.SdlSpots;
import com.goldskinmc.lightworks.light.ClientLightSpot;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

/**
 * Guard for the client dynamic-light path. Only touches {@code compat.ldl} when LambDynamicLights is
 * loaded and {@code compat.sdl} when Sodium Dynamic Lights is, so their classes never load without them.
 * The two refuse each other, so one at most is here.
 */
public final class DynamicLights {
    /** Present in both the release jar (nested in "lambdynlights") and the dev runtime. */
    private static final String LDL_RUNTIME_MOD_ID = "lambdynlights_runtime";
    private static final String SDL_MOD_ID = "sodiumdynamiclights";

    private DynamicLights() {
    }

    /**
     * True when a dynamic-light mod can take spots, or, with neither, our own client light blocks
     * ({@link ClientLightSpot}) do.
     */
    public static boolean present() {
        return ldl() || (sdl() && SdlSpots.enabled()) || (!sdl() && clientLightBlocks());
    }

    /**
     * Whether our own client light blocks stand in for a dynamic-light mod: only on a client ({@link ClientLightSpot} uses
     * client classes, so a dedicated server must never load it), when the client config allows it.
     */
    private static boolean clientLightBlocks() {
        return FMLEnvironment.dist.isClient() && LightworksClientConfig.clientLightBlocks();
    }

    private static boolean ldl() {
        return ModList.get().isLoaded(LDL_RUNTIME_MOD_ID);
    }

    private static boolean sdl() {
        return ModList.get().isLoaded(SDL_MOD_ID);
    }

    /** A new dynamic spot, or {@link DynamicSpot#NONE} when no dynamic-light mod can take it. */
    public static DynamicSpot createSpot() {
        if (ldl()) {
            return LdlSpots.create();
        }
        if (sdl()) {
            return SdlSpots.create();
        }
        return clientLightBlocks() ? new ClientLightSpot() : DynamicSpot.NONE;
    }

    /** Switches every dynamic spot dark (or back on) without removing it: for comparisons in testing. */
    public static void setSuppressed(boolean suppressed) {
        if (ldl()) {
            LdlSpots.setSuppressed(suppressed);
        } else if (sdl()) {
            SdlSpots.setSuppressed(suppressed);
        }
    }

    /** Where our client light blocks stand now, for testing; none on a server. */
    public static List<BlockPos> clientLightBlockPositions() {
        return FMLEnvironment.dist.isClient() ? ClientLightSpot.placedPositions() : List.of();
    }

    /** The client world's block at {@code pos}, for testing; null on a server. */
    public static @Nullable BlockState clientBlockAt(BlockPos pos) {
        return FMLEnvironment.dist.isClient() ? ClientLightSpot.clientStateAt(pos) : null;
    }

    /** One-line status for the debug overlay and log. */
    public static String status() {
        if (ldl()) {
            return LdlSpots.status();
        }
        if (sdl()) {
            return SdlSpots.status();
        }
        return clientLightBlocks() ? ClientLightSpot.status() : "No dynamic light mod installed";
    }
}
