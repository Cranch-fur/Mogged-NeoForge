package mogged.platform;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import mogged.net.SkinRemove;
import mogged.net.SkinState;
import mogged.net.TexChunk;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The networking layer (26.x, NeoForge: custom payloads). The three messages are optional payloads, handled
 * on the main thread: a client or server without the mod is accepted, it just never gets them. The client's
 * handlers are registered on the client (ClientNetworking, RegisterClientPayloadHandlersEvent).
 */
public final class ModNetworking {
    private static final String PROTOCOL = "1";

    public record ChunkPayload(TexChunk message) implements CustomPacketPayload {
        public static final Type<ChunkPayload> TYPE = new Type<>(Ids.mod("chunk"));
        public static final StreamCodec<FriendlyByteBuf, ChunkPayload> CODEC = StreamCodec.of(
                (buf, payload) -> payload.message().write(buf), buf -> new ChunkPayload(TexChunk.read(buf)));

        @Override
        public Type<ChunkPayload> type() {
            return TYPE;
        }
    }

    public record StatePayload(SkinState message) implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(Ids.mod("state"));
        public static final StreamCodec<FriendlyByteBuf, StatePayload> CODEC = StreamCodec.of(
                (buf, payload) -> payload.message().write(buf), buf -> new StatePayload(SkinState.read(buf)));

        @Override
        public Type<StatePayload> type() {
            return TYPE;
        }
    }

    public record RemovePayload(SkinRemove message) implements CustomPacketPayload {
        public static final Type<RemovePayload> TYPE = new Type<>(Ids.mod("remove"));
        public static final StreamCodec<FriendlyByteBuf, RemovePayload> CODEC = StreamCodec.of(
                (buf, payload) -> payload.message().write(buf), buf -> new RemovePayload(SkinRemove.read(buf)));

        @Override
        public Type<RemovePayload> type() {
            return TYPE;
        }
    }

    private static BiConsumer<ServerPlayer, TexChunk> serverChunk = (player, message) -> {
    };
    private static BiConsumer<ServerPlayer, SkinState> serverState = (player, message) -> {
    };
    // Set by the client (ClientNetworking); a dedicated server never sets them
    private static Consumer<TexChunk> clientChunk = message -> {
    };
    private static Consumer<SkinState> clientState = message -> {
    };
    private static Consumer<SkinRemove> clientRemove = message -> {
    };

    private ModNetworking() {
    }

    public static void registerPayloads(IEventBus modBus) {
        modBus.addListener(ModNetworking::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL).optional().executesOn(HandlerThread.MAIN);
        // Server-side handlers here; the client's come from RegisterClientPayloadHandlersEvent
        registrar.playBidirectional(ChunkPayload.TYPE, ChunkPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer sender) {
                serverChunk.accept(sender, payload.message());
            }
        });
        registrar.playBidirectional(StatePayload.TYPE, StatePayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer sender) {
                serverState.accept(sender, payload.message());
            }
        });
        registrar.playToClient(RemovePayload.TYPE, RemovePayload.CODEC);
    }

    public static void registerServerReceivers(BiConsumer<ServerPlayer, TexChunk> onChunk, BiConsumer<ServerPlayer, SkinState> onState) {
        serverChunk = onChunk;
        serverState = onState;
    }

    /** Where messages from the server go on the client. */
    public static void setClientReceivers(Consumer<TexChunk> onChunk, Consumer<SkinState> onState, Consumer<SkinRemove> onRemove) {
        clientChunk = onChunk;
        clientState = onState;
        clientRemove = onRemove;
    }

    /** A payload from the server, on the client. */
    public static void receiveOnClient(CustomPacketPayload payload) {
        if (payload instanceof ChunkPayload chunk) {
            clientChunk.accept(chunk.message());
        } else if (payload instanceof StatePayload state) {
            clientState.accept(state.message());
        } else if (payload instanceof RemovePayload remove) {
            clientRemove.accept(remove.message());
        }
    }

    /** One of our messages as the payload that carries it. */
    public static CustomPacketPayload payload(Object message) {
        if (message instanceof TexChunk chunk) {
            return new ChunkPayload(chunk);
        }
        if (message instanceof SkinState state) {
            return new StatePayload(state);
        }
        if (message instanceof SkinRemove remove) {
            return new RemovePayload(remove);
        }
        throw new IllegalArgumentException("Not a Mogged message: " + message);
    }

    /** True if the player's client has the mod. */
    public static boolean hasMod(ServerPlayer player) {
        if (player.connection == null) {
            return false;
        }
        // The singleplayer/LAN host plays over an in-memory connection: same game, so it has the mod
        Connection connection = player.connection.getConnection();
        return (connection != null && connection.isMemoryConnection()) || player.connection.hasChannel(ChunkPayload.TYPE);
    }

    public static void send(ServerPlayer player, Object message) {
        PacketDistributor.sendToPlayer(player, payload(message));
    }
}
