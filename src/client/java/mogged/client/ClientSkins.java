package mogged.client;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import mogged.Mogged;
import mogged.client.compat.FlashbackCompat;
import mogged.client.render.ModelRaster;
import mogged.client.platform.ClientNetworking;
import mogged.client.platform.ClientPlatform;
import mogged.net.SkinKinds;
import mogged.net.SkinRemove;
import mogged.net.SkinState;
import mogged.net.TexChunk;
import mogged.server.SkinServer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Client-side state: own presets, other players' textures and network sync. */
public final class ClientSkins {
    public static final PlayerSkins SELF = new PlayerSkins("self");

    private static final Map<UUID, PlayerSkins> REMOTE = new ConcurrentHashMap<>();
    private static final Map<UUID, Partial[]> PARTIAL = new ConcurrentHashMap<>();
    private static final int[] LOAD_GENERATION = new int[SkinKinds.COUNT];
    private static final Component[] STATUS = new Component[SkinKinds.COUNT];
    private static final Random RANDOM = new Random();
    private static final int UPLOAD_RETRY_TICKS = 200;
    private static final int MAX_DURATION_MS = 10_000;

    private static int retryUploadTicks;

    private ClientSkins() {
    }

    private static boolean isSelf(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && uuid.equals(mc.player.getUUID()) && !FlashbackCompat.isInReplay();
    }

    private static boolean canActAsSelf() {
        return Minecraft.getInstance().player != null && !FlashbackCompat.isInReplay();
    }

    public static PlayerSkins skinsFor(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        if (isSelf(uuid)) {
            return SELF.hasAnything() ? SELF : null;
        }
        return REMOTE.get(uuid);
    }

    /** Called from mixins: replaces the vanilla skin with the active preset or transition frame. */
    public static SkinView modify(UUID uuid, SkinView vanilla) {
        if (vanilla == null) {
            return null;
        }
        PlayerSkins skins = skinsFor(uuid);
        if (skins == null) {
            return vanilla;
        }
        try {
            return skins.apply(vanilla);
        } catch (RuntimeException e) {
            Mogged.LOGGER.error("Failed to apply skin", e);
            return vanilla;
        }
    }

    public static Component status(int kind) {
        return STATUS[kind];
    }

    // --- Own textures ---

    public static void reloadAllFromConfig() {
        for (int kind = 0; kind < SkinKinds.COUNT; kind++) {
            loadSelf(kind);
        }
        SkinConfig config = SkinConfig.get();
        SELF.active = config.activePreset;
        SELF.durationMs = config.durationMs;
        SELF.effects = config.effects;
        SELF.invalidate();
    }

    public static void loadSelf(int kind) {
        SkinConfig config = SkinConfig.get();
        String source = SkinImages.clean(config.source(kind));
        boolean slim = config.slim(kind);
        // Loading is async; the generation lets a slow old download be ignored if the user changed the source since
        int generation = ++LOAD_GENERATION[kind];

        if (source.isEmpty()) {
            STATUS[kind] = null;
            SELF.setTexture(kind, null);
            upload(kind);
            return;
        }

        STATUS[kind] = Component.translatable("mogged.status.loading").withStyle(ChatFormatting.YELLOW);
        SkinImages.readBytes(source).whenCompleteAsync((png, error) -> {
            // A newer load for this slot has started meanwhile
            if (generation != LOAD_GENERATION[kind]) {
                return;
            }
            if (error != null) {
                STATUS[kind] = errorText(error);
                return;
            }
            try {
                SkinTexture texture = SkinTexture.create("self", kind, png, slim);
                SELF.setTexture(kind, texture);
                STATUS[kind] = Component.translatable("mogged.status.loaded", texture.width + "x" + texture.height)
                        .withStyle(ChatFormatting.GREEN);
                upload(kind);
            } catch (RuntimeException e) {
                STATUS[kind] = errorText(e);
            }
        }, Minecraft.getInstance());
    }

    public static void updateSlim(int kind, boolean slim) {
        SkinTexture current = SELF.textures[kind];
        if (current == null || current.slim == slim) {
            return;
        }
        try {
            SELF.setTexture(kind, SkinTexture.create("self", kind, current.png, slim));
            upload(kind);
        } catch (RuntimeException e) {
            STATUS[kind] = errorText(e);
        }
    }

    public static Component errorText(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && !(cause instanceof SkinImages.LoadException)) {
            cause = cause.getCause();
        }
        if (cause instanceof SkinImages.LoadException load) {
            return Component.translatable(load.key, load.args).withStyle(ChatFormatting.RED);
        }
        return Component.translatable("mogged.error.read").withStyle(ChatFormatting.RED);
    }

    /** The player's own account skin, without any preset applied. */
    public static SkinView ownVanillaSkin() {
        return ClientPlatform.ownSkin();
    }

    /** Fills a preview scene with the textures of the given preset. */
    public static void fillScene(int preset, ModelRaster.Scene scene, int back) {
        SkinView skin = SELF.resolve(preset, ownVanillaSkin());
        scene.skin = SELF.pixelsOf(skin.body());
        scene.slim = skin.slim();
        scene.cape = SELF.pixelsOf(skin.cape());
        scene.elytra = skin.elytra() != null
                ? SELF.pixelsOf(skin.elytra())
                : SELF.pixelsOf(ClientPlatform.defaultElytra());
        scene.back = back;
    }

    public static Component presetName(int preset) {
        return preset == SkinKinds.DEFAULT_PRESET
                ? Component.translatable("mogged.preset.default")
                : Component.translatable("mogged.preset.n", preset);
    }

    // --- Switching ---

    /** Switch key: go to the next preset that is in the cycle and has content. */
    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (!canActAsSelf()) {
            return;
        }
        SkinConfig config = SkinConfig.get();
        int count = SkinKinds.MAX_PRESET + 1;
        int next = -1;

        // Walk forward from the current preset and wrap around, skipping presets that are unticked or empty
        for (int step = 1; step < count; step++) {
            int candidate = (SELF.active + step) % count;
            if (config.cycle[candidate] && SELF.presetHasContent(candidate)) {
                next = candidate;
                break;
            }
        }

        if (next < 0) {
            ClientPlatform.showActionBar(Component.translatable("mogged.message.nothing"));
        } else {
            select(next);
        }
    }

    public static void select(int preset) {
        Minecraft mc = Minecraft.getInstance();
        SkinConfig config = SkinConfig.get();
        preset = Mth.clamp(preset, SkinKinds.DEFAULT_PRESET, SkinKinds.MAX_PRESET);
        config.activePreset = preset;
        config.save();

        if (SELF.active == preset) {
            return;
        }
        SELF.durationMs = config.durationMs;
        SELF.effects = config.effects;
        SELF.setActive(preset, true);

        if (canActAsSelf()) {
            ClientPlatform.showActionBar(Component.translatable("mogged.message.switched", presetName(preset)));
            if (SELF.effects) {
                playStartEffects(mc.player);
            }
            sendState(true);
        }
    }

    // --- Networking ---

    private static boolean serverSupported() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || FlashbackCompat.isInReplay()) {
            return false;
        }
        try {
            return ClientNetworking.serverHasMod();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static UUID selfUuid() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getUUID() : new UUID(0L, 0L);
    }

    private static List<TexChunk> selfChunks(int kind) {
        SkinTexture texture = SELF.textures[kind];
        UUID uuid = selfUuid();
        return texture == null
                ? List.of(TexChunk.clear(uuid, (byte) kind, 0))
                : SkinServer.split(uuid, (byte) kind, texture.slim, texture.png);
    }

    private static SkinState selfState(boolean animate) {
        SkinConfig config = SkinConfig.get();
        return new SkinState(selfUuid(), (byte) SELF.active, animate, config.durationMs, config.effects);
    }

    public static void upload(int kind) {
        if (!canActAsSelf()) {
            return;
        }
        boolean supported = serverSupported();
        for (TexChunk chunk : selfChunks(kind)) {
            if (supported) {
                ClientNetworking.send(chunk);
            }
            FlashbackCompat.record(chunk);
        }
    }

    public static void sendState(boolean animate) {
        if (!canActAsSelf()) {
            return;
        }
        SkinState state = selfState(animate);
        if (serverSupported()) {
            ClientNetworking.send(state);
        }
        FlashbackCompat.record(state);
    }

    public static void uploadEverything() {
        for (int kind = 0; kind < SkinKinds.COUNT; kind++) {
            upload(kind);
        }
        sendState(false);
    }

    public static void onJoin() {
        SELF.setActive(SkinConfig.get().activePreset, false);
        uploadEverything();
        // Channels may be announced a bit later than JOIN, keep retrying for a while
        retryUploadTicks = serverSupported() ? 0 : UPLOAD_RETRY_TICKS;
    }

    public static void onDisconnect() {
        for (PlayerSkins skins : REMOTE.values()) {
            skins.releaseAll();
        }
        REMOTE.clear();
        PARTIAL.clear();
        VanillaPixels.clear();
    }

    /** Everything needed to restore current skins at the start of a Flashback recording. */
    public static List<Object> snapshotPayloads() {
        List<Object> payloads = new ArrayList<>();

        if (canActAsSelf() && SELF.hasAnything()) {
            for (int kind = 0; kind < SkinKinds.COUNT; kind++) {
                if (SELF.textures[kind] != null) {
                    payloads.addAll(selfChunks(kind));
                }
            }
            payloads.add(selfState(false));
        }

        for (Map.Entry<UUID, PlayerSkins> entry : REMOTE.entrySet()) {
            PlayerSkins skins = entry.getValue();
            for (int kind = 0; kind < SkinKinds.COUNT; kind++) {
                SkinTexture texture = skins.textures[kind];
                if (texture != null) {
                    payloads.addAll(SkinServer.split(entry.getKey(), (byte) kind, texture.slim, texture.png));
                }
            }
            payloads.add(new SkinState(entry.getKey(), (byte) skins.active, false, skins.durationMs, skins.effects));
        }
        return payloads;
    }

    public static void onChunk(TexChunk payload) {
        UUID uuid = payload.player();
        byte kind = payload.kind();
        if (!SkinKinds.isValid(kind)) {
            return;
        }

        if (payload.isClear()) {
            PlayerSkins skins = REMOTE.get(uuid);
            if (skins != null) {
                skins.setTexture(kind, null);
            }
            return;
        }
        if (!payload.isWellFormed()) {
            return;
        }

        Partial[] partials = PARTIAL.computeIfAbsent(uuid, key -> new Partial[SkinKinds.COUNT]);
        Partial partial = partials[kind];
        if (partial == null || partial.transferId != payload.transferId() || partial.chunks.length != payload.total()) {
            partial = new Partial(payload.transferId(), payload.total());
            partials[kind] = partial;
        }
        if (partial.chunks[payload.index()] == null) {
            partial.chunks[payload.index()] = payload.data();
            partial.received++;
        }
        if (partial.received < partial.chunks.length) {
            return;
        }

        partials[kind] = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] chunk : partial.chunks) {
            out.writeBytes(chunk);
        }

        try {
            String owner = textureOwner(uuid);
            SkinTexture texture = SkinTexture.create(owner, kind, out.toByteArray(), payload.slim());
            REMOTE.computeIfAbsent(uuid, key -> new PlayerSkins(owner)).setTexture(kind, texture);
        } catch (RuntimeException e) {
            Mogged.LOGGER.debug("Ignoring invalid skin texture from {}", uuid, e);
        }
    }

    public static void onState(SkinState payload) {
        UUID uuid = payload.player();
        PlayerSkins skins = REMOTE.computeIfAbsent(uuid, key -> new PlayerSkins(textureOwner(key)));
        skins.durationMs = Mth.clamp(payload.durationMs(), 0, MAX_DURATION_MS);
        skins.effects = payload.effects();

        boolean changed = skins.active != payload.active();
        skins.setActive(payload.active(), payload.animate());

        if (changed && payload.animate() && skins.effects) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getPlayerByUUID(uuid) instanceof AbstractClientPlayer player) {
                playStartEffects(player);
            }
        }
    }

    public static void onRemove(SkinRemove payload) {
        PlayerSkins skins = REMOTE.remove(payload.player());
        PARTIAL.remove(payload.player());
        if (skins != null) {
            skins.releaseAll();
        }
    }

    private static String textureOwner(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    // --- Effects ---

    private static void playStartEffects(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        mc.level.playLocalSound(x, y + 1.0, z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.7F, 1.35F, false);
        mc.level.playLocalSound(x, y + 1.0, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F, false);

        // Ring of particles above the head
        for (int i = 0; i < 16; i++) {
            double angle = i / 16.0 * Math.PI * 2.0;
            mc.level.addParticle(ParticleTypes.REVERSE_PORTAL,
                    x + Math.cos(angle) * 0.6, y + player.getBbHeight() + 0.1, z + Math.sin(angle) * 0.6,
                    0.0, -0.02, 0.0);
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();

        if (retryUploadTicks > 0) {
            retryUploadTicks--;
            if (mc.player != null && serverSupported()) {
                retryUploadTicks = 0;
                uploadEverything();
            }
        }

        if (mc.level == null || mc.isPaused()) {
            return;
        }

        for (AbstractClientPlayer player : mc.level.players()) {
            PlayerSkins skins = skinsFor(player.getUUID());
            if (skins == null || !skins.isTransitioning() || !skins.effects || player.isInvisible()) {
                continue;
            }
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
                continue;
            }

            // Sparkles follow the transition line from head to feet
            float progress = skins.progress();
            double lineY = player.getY() + player.getBbHeight() * (1.0 - progress);
            for (int i = 0; i < 5; i++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2.0;
                double radius = 0.42 + RANDOM.nextDouble() * 0.12;
                mc.level.addParticle(i < 3 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                        player.getX() + Math.cos(angle) * radius, lineY, player.getZ() + Math.sin(angle) * radius,
                        Math.cos(angle) * 0.02, (RANDOM.nextDouble() - 0.5) * 0.02, Math.sin(angle) * 0.02);
            }
        }
    }

    private static final class Partial {
        final int transferId;
        final byte[][] chunks;
        int received;

        Partial(int transferId, int total) {
            this.transferId = transferId;
            this.chunks = new byte[total][];
        }
    }
}
