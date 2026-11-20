package com.goldskinmc.lightworks.content.carried;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.registry.LightworksComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The headlamp key, client to server, and the mod's first packet: switches the headlamp on the sender's head on or off.
 * It carries nothing; the server looks at what the player wears, so a client can only ever switch its own lamp. The
 * changed item reaches every client that sees the player as worn items always do, so no packet goes back.
 */
public record ToggleHeadlampPayload() implements CustomPacketPayload {
    public static final Type<ToggleHeadlampPayload> TYPE = new Type<>(Lightworks.id("toggle_headlamp"));
    public static final StreamCodec<ByteBuf, ToggleHeadlampPayload> STREAM_CODEC = StreamCodec.unit(new ToggleHeadlampPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1")
                .playToServer(TYPE, STREAM_CODEC, (payload, context) -> toggle(context)));
    }

    private static void toggle(IPayloadContext context) {
        toggle(context.player());
    }

    /** Switches the worn headlamp, if there is one; does nothing otherwise. True when it switched. */
    public static boolean toggle(Player player) {
        ItemStack head = Headlamps.worn(player);
        if (head.isEmpty()) {
            return false;
        }
        boolean on = !head.getOrDefault(LightworksComponents.LAMP_ON.get(), false);
        head.set(LightworksComponents.LAMP_ON.get(), on);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LEVER_CLICK,
                SoundSource.PLAYERS, 0.3f, on ? 1.4f : 1.1f);
        return true;
    }
}
