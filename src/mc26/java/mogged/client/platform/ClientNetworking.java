package mogged.client.platform;

import java.util.function.Consumer;
import mogged.net.SkinRemove;
import mogged.net.SkinState;
import mogged.net.TexChunk;
import mogged.platform.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client side of the networking layer (26.x, NeoForge). */
public final class ClientNetworking {
    private ClientNetworking() {
    }

    public static void registerReceivers(Consumer<TexChunk> onChunk, Consumer<SkinState> onState, Consumer<SkinRemove> onRemove) {
        ModNetworking.setClientReceivers(onChunk, onState, onRemove);
    }

    /** The client's handlers for the payloads the server sends (on the mod bus). */
    public static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ModNetworking.ChunkPayload.TYPE, (payload, context) -> ModNetworking.receiveOnClient(payload));
        event.register(ModNetworking.StatePayload.TYPE, (payload, context) -> ModNetworking.receiveOnClient(payload));
        event.register(ModNetworking.RemovePayload.TYPE, (payload, context) -> ModNetworking.receiveOnClient(payload));
    }

    /** True if the server we are connected to runs Mogged. */
    public static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return false;
        }
        // Single player and the LAN host: the integrated server is this game, so it has the mod
        return connection.getConnection().isMemoryConnection() || connection.hasChannel(ModNetworking.ChunkPayload.TYPE);
    }

    public static void send(Object message) {
        ClientPacketDistributor.sendToServer(ModNetworking.payload(message));
    }
}
