package mogged.net;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One chunk of a PNG texture. A texture is split into up to {@link #MAX_CHUNKS} chunks;
 * a message with {@code total == 0} clears the slot.
 * Plain data shared by all Minecraft versions; the version-specific packet classes wrap it.
 */
public record TexChunk(UUID player, byte kind, boolean slim, int transferId, int index, int total, byte[] data) {
    public static final int MAX_CHUNK = 30000;
    public static final int MAX_CHUNKS = 160;

    public static TexChunk clear(UUID player, byte kind, int transferId) {
        return new TexChunk(player, kind, false, transferId, 0, 0, new byte[0]);
    }

    public boolean isClear() {
        return total <= 0;
    }

    public boolean isWellFormed() {
        return total <= MAX_CHUNKS && index >= 0 && index < total && data.length <= MAX_CHUNK;
    }

    public static TexChunk read(FriendlyByteBuf buf) {
        UUID player = buf.readUUID();
        byte kind = buf.readByte();
        boolean slim = buf.readBoolean();
        int transferId = buf.readVarInt();
        int index = buf.readVarInt();
        int total = buf.readVarInt();
        byte[] data = buf.readByteArray(MAX_CHUNK + 16);
        return new TexChunk(player, kind, slim, transferId, index, total, data);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        buf.writeByte(kind);
        buf.writeBoolean(slim);
        buf.writeVarInt(transferId);
        buf.writeVarInt(index);
        buf.writeVarInt(total);
        buf.writeByteArray(data);
    }
}
