package mogged.server;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import mogged.net.SkinKinds;
import mogged.net.SkinRemove;
import mogged.net.SkinState;
import mogged.net.TexChunk;
import mogged.platform.ModNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Relays custom textures and preset state between players that have the mod.
 * Works on dedicated servers and in singleplayer/LAN (integrated server).
 */
public final class SkinServer {
    private static final int MAX_TEXTURE_BYTES = 4_800_000;
    private static final int MAX_DURATION_MS = 10_000;
    private static final Map<UUID, PlayerData> DATA = new ConcurrentHashMap<>();
    private static int transferCounter = 1;
    private static MinecraftServer server;

    private SkinServer() {
    }

    public static void init() {
        ModNetworking.registerServerReceivers(SkinServer::onChunk, SkinServer::onState);
    }

    /** The server is starting (dedicated, or the integrated one in singleplayer/LAN). */
    public static void onServerStarting(MinecraftServer starting) {
        server = starting;
    }

    public static void onServerStopped() {
        DATA.clear();
        server = null;
    }

    /** Sends everything we know to a newly joined player. */
    public static void onJoin(ServerPlayer player) {
        if (!ModNetworking.hasMod(player)) {
            return;
        }
        for (Map.Entry<UUID, PlayerData> entry : DATA.entrySet()) {
            if (!entry.getKey().equals(player.getUUID())) {
                sendAll(player, entry.getKey(), entry.getValue());
            }
        }
    }

    public static void onLeave(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (DATA.remove(uuid) != null) {
            broadcast(uuid, new SkinRemove(uuid));
        }
    }

    private static void onChunk(ServerPlayer player, TexChunk payload) {
        byte kind = payload.kind();
        if (!SkinKinds.isValid(kind)) {
            return;
        }
        PlayerData data = DATA.computeIfAbsent(player.getUUID(), uuid -> new PlayerData());

        if (payload.isClear()) {
            data.textures[kind] = null;
            data.partial[kind] = null;
            broadcastTexture(player.getUUID(), kind, false, null);
            return;
        }
        if (!payload.isWellFormed()) {
            return;
        }

        // Chunks of one texture share a transfer id; a new id means a new upload, so drop the old pieces
        Partial partial = data.partial[kind];
        if (partial == null || partial.transferId != payload.transferId() || partial.chunks.length != payload.total()) {
            partial = new Partial(payload.transferId(), payload.total());
            data.partial[kind] = partial;
        }
        // Duplicates are ignored so a resent chunk cannot inflate the byte count
        if (partial.chunks[payload.index()] == null) {
            partial.chunks[payload.index()] = payload.data();
            partial.received++;
            partial.bytes += payload.data().length;
        }
        if (partial.bytes > MAX_TEXTURE_BYTES) {
            data.partial[kind] = null;
            return;
        }
        if (partial.received < partial.chunks.length) {
            return;
        }

        data.partial[kind] = null;
        byte[] png = join(partial.chunks, partial.bytes);
        if (!isPng(png)) {
            return;
        }
        data.textures[kind] = png;
        data.slim[kind] = payload.slim();
        broadcastTexture(player.getUUID(), kind, payload.slim(), png);
    }

    private static void onState(ServerPlayer player, SkinState payload) {
        PlayerData data = DATA.computeIfAbsent(player.getUUID(), uuid -> new PlayerData());
        int duration = Math.max(0, Math.min(MAX_DURATION_MS, payload.durationMs()));
        byte active = (byte) Math.max(SkinKinds.DEFAULT_PRESET, Math.min(SkinKinds.MAX_PRESET, payload.active()));

        SkinState relayed = new SkinState(player.getUUID(), active, payload.animate(), duration, payload.effects());
        // Late joiners must not replay the animation
        data.state = new SkinState(player.getUUID(), active, false, duration, payload.effects());
        broadcast(player.getUUID(), relayed);
    }

    private static byte[] join(byte[][] chunks, int size) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(size);
        for (byte[] chunk : chunks) {
            out.writeBytes(chunk);
        }
        return out.toByteArray();
    }

    public static boolean isPng(byte[] bytes) {
        return bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }

    private static void sendAll(ServerPlayer target, UUID owner, PlayerData data) {
        for (int kind = 0; kind < SkinKinds.COUNT; kind++) {
            byte[] png = data.textures[kind];
            if (png != null) {
                for (TexChunk chunk : split(owner, (byte) kind, data.slim[kind], png)) {
                    ModNetworking.send(target, chunk);
                }
            }
        }
        if (data.state != null) {
            ModNetworking.send(target, data.state);
        }
    }

    private static void broadcastTexture(UUID owner, byte kind, boolean slim, byte[] png) {
        List<TexChunk> chunks = png == null
                ? List.of(TexChunk.clear(owner, kind, nextTransfer()))
                : split(owner, kind, slim, png);
        for (TexChunk chunk : chunks) {
            broadcast(owner, chunk);
        }
    }

    /** Sends a message to every other player that has the mod. */
    private static void broadcast(UUID owner, Object message) {
        if (server == null) {
            return;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            if (!target.getUUID().equals(owner) && ModNetworking.hasMod(target)) {
                ModNetworking.send(target, message);
            }
        }
    }

    private static synchronized int nextTransfer() {
        return transferCounter++;
    }

    /** Splits a PNG into network-sized chunks sharing one transfer id. */
    public static List<TexChunk> split(UUID owner, byte kind, boolean slim, byte[] png) {
        int total = Math.max(1, (png.length + TexChunk.MAX_CHUNK - 1) / TexChunk.MAX_CHUNK);
        int transferId = nextTransfer();
        List<TexChunk> chunks = new ArrayList<>(total);

        for (int index = 0; index < total; index++) {
            int start = index * TexChunk.MAX_CHUNK;
            int end = Math.min(png.length, start + TexChunk.MAX_CHUNK);
            byte[] data = new byte[end - start];
            System.arraycopy(png, start, data, 0, data.length);
            chunks.add(new TexChunk(owner, kind, slim, transferId, index, total, data));
        }
        return chunks;
    }

    private static final class Partial {
        final int transferId;
        final byte[][] chunks;
        int received;
        int bytes;

        Partial(int transferId, int total) {
            this.transferId = transferId;
            this.chunks = new byte[total][];
        }
    }

    private static final class PlayerData {
        final byte[][] textures = new byte[SkinKinds.COUNT][];
        final boolean[] slim = new boolean[SkinKinds.COUNT];
        final Partial[] partial = new Partial[SkinKinds.COUNT];
        SkinState state;
    }
}
