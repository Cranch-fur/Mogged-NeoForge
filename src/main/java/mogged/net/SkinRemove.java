package mogged.net;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Server -> client: forget all custom textures of a player. */
public record SkinRemove(UUID player) {
    public static SkinRemove read(FriendlyByteBuf buf) {
        return new SkinRemove(buf.readUUID());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(player);
    }
}
