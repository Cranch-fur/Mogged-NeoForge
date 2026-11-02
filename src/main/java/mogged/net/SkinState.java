package mogged.net;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Active preset of a player and how to animate the switch. */
public record SkinState(UUID player, byte active, boolean animate, int durationMs, boolean effects) {
    public static SkinState read(FriendlyByteBuf buf) {
        return new SkinState(buf.readUUID(), buf.readByte(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        buf.writeByte(active);
        buf.writeBoolean(animate);
        buf.writeVarInt(durationMs);
        buf.writeBoolean(effects);
    }
}
