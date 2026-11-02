package mogged.client.platform;

import java.util.function.Consumer;
import mogged.net.SkinRemove;
import mogged.net.SkinState;
import mogged.net.TexChunk;
import mogged.platform.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Client side of the networking layer (1.20.1, NeoForge 47.1, the Forge 47 API). */
public final class ClientNetworking {
    private ClientNetworking() {
    }

    public static void registerReceivers(Consumer<TexChunk> onChunk, Consumer<SkinState> onState, Consumer<SkinRemove> onRemove) {
        ModNetworking.setClientReceivers(onChunk, onState, onRemove);
    }

    /** True if the server we are connected to runs Mogged. */
    public static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && (connection.getConnection().isMemoryConnection()
                || ModNetworking.CHANNEL.isRemotePresent(connection.getConnection()));
    }

    public static void send(Object message) {
        ModNetworking.CHANNEL.sendToServer(message);
    }
}
