package mogged.platform;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import mogged.net.SkinRemove;
import mogged.net.SkinState;
import mogged.net.TexChunk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The networking layer (1.20.1, NeoForge 47.1 with the Forge 47 API: SimpleChannel). One channel carries the three messages both ways;
 * a client or server without the mod is accepted, it just never gets them.
 */
public final class ModNetworking {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(Ids.mod("main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
            .simpleChannel();

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

    public static void registerPayloads() {
        CHANNEL.messageBuilder(TexChunk.class, 0)
                .encoder(TexChunk::write)
                .decoder(TexChunk::read)
                .consumerMainThread((message, context) -> {
                    ServerPlayer sender = context.get().getSender();
                    if (sender != null) {
                        serverChunk.accept(sender, message);
                    } else {
                        clientChunk.accept(message);
                    }
                })
                .add();
        CHANNEL.messageBuilder(SkinState.class, 1)
                .encoder(SkinState::write)
                .decoder(SkinState::read)
                .consumerMainThread((message, context) -> {
                    ServerPlayer sender = context.get().getSender();
                    if (sender != null) {
                        serverState.accept(sender, message);
                    } else {
                        clientState.accept(message);
                    }
                })
                .add();
        CHANNEL.messageBuilder(SkinRemove.class, 2)
                .encoder(SkinRemove::write)
                .decoder(SkinRemove::read)
                .consumerMainThread((message, context) -> {
                    if (context.get().getSender() == null) {
                        clientRemove.accept(message);
                    }
                })
                .add();
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

    /** True if the player's client has the mod. */
    public static boolean hasMod(ServerPlayer player) {
        if (player.connection == null) {
            return false;
        }
        // The singleplayer/LAN host plays over an in-memory connection: same game, so it has the mod
        return player.connection.connection.isMemoryConnection() || CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static void send(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

}
