package mogged.client.platform;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import mogged.Mogged;
import mogged.client.SkinView;
import mogged.client.TexKey;
import mogged.client.render.ModelRaster;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;

/** Everything on the client that differs between Minecraft versions (1.20.1). */
public final class ClientPlatform {
    private static final ResourceLocation DEFAULT_ELYTRA = new ResourceLocation("textures/entity/elytra.png");
    // Set while we read the vanilla skin through PlayerInfo, so our own mixin stays out of the way
    private static final ThreadLocal<Boolean> READING_VANILLA = ThreadLocal.withInitial(() -> false);
    // The account's textures outside a world; fetched once in the background (needs a web request)
    private static volatile Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> ownTextures;
    private static boolean ownTexturesRequested;
    private static SkinView ownMenuSkin;

    private ClientPlatform() {
    }

    /** The switch key; NeoForge registers it through RegisterKeyMappingsEvent. */
    public static KeyMapping createToggleKey() {
        return new KeyMapping("key.mogged.toggle", InputConstants.KEY_K, "key.category.mogged.main");
    }

    public static void showActionBar(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, true);
        }
    }


    // --- Skins ---

    public static TexKey key(ResourceLocation id) {
        return id == null ? null : new TexKey(id.toString(), id);
    }

    public static ResourceLocation id(TexKey key) {
        return key == null ? null : (ResourceLocation) key.handle();
    }

    /** True while {@link #view(PlayerInfo)} reads the unmodified values. */
    public static boolean readingVanilla() {
        return READING_VANILLA.get();
    }

    /** The vanilla skin of a player info. 1.20.1 has no skin record, so it is read part by part. */
    public static SkinView view(PlayerInfo info) {
        READING_VANILLA.set(true);
        try {
            return new SkinView(key(info.getSkinLocation()), key(info.getCapeLocation()), key(info.getElytraLocation()),
                    "slim".equals(info.getModelName()));
        } finally {
            READING_VANILLA.set(false);
        }
    }

    public static SkinView ownSkin() {
        Minecraft mc = Minecraft.getInstance();
        GameProfile profile = mc.getUser().getGameProfile();
        ClientPacketListener connection = mc.getConnection();
        PlayerInfo info = connection != null ? connection.getPlayerInfo(profile.getId()) : null;
        if (info != null) {
            return view(info);
        }
        return ownMenuSkin(mc, profile);
    }

    private static SkinView ownMenuSkin(Minecraft mc, GameProfile profile) {
        if (ownMenuSkin != null) {
            return ownMenuSkin;
        }
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = ownTextures;
        if (textures == null) {
            requestOwnTextures(mc, profile);
            return defaultSkin(profile.getId());
        }
        MinecraftProfileTexture skin = textures.get(MinecraftProfileTexture.Type.SKIN);
        if (skin == null) {
            ownMenuSkin = defaultSkin(profile.getId());
            return ownMenuSkin;
        }
        ownMenuSkin = new SkinView(
                key(mc.getSkinManager().registerTexture(skin, MinecraftProfileTexture.Type.SKIN)),
                register(mc, textures, MinecraftProfileTexture.Type.CAPE),
                register(mc, textures, MinecraftProfileTexture.Type.ELYTRA),
                "slim".equals(skin.getMetadata("model")));
        return ownMenuSkin;
    }

    private static TexKey register(Minecraft mc, Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures,
                                   MinecraftProfileTexture.Type type) {
        MinecraftProfileTexture texture = textures.get(type);
        return texture == null ? null : key(mc.getSkinManager().registerTexture(texture, type));
    }

    private static void requestOwnTextures(Minecraft mc, GameProfile profile) {
        if (ownTexturesRequested) {
            return;
        }
        ownTexturesRequested = true;
        CompletableFuture.runAsync(() -> {
            try {
                GameProfile filled = new GameProfile(profile.getId(), profile.getName());
                filled.getProperties().putAll(mc.getProfileProperties());
                ownTextures = mc.getSkinManager().getInsecureSkinInformation(filled);
            } catch (RuntimeException e) {
                Mogged.LOGGER.debug("Could not fetch own skin", e);
                ownTextures = Map.of();
            }
        });
    }

    private static SkinView defaultSkin(UUID uuid) {
        return new SkinView(key(DefaultPlayerSkin.getDefaultSkin(uuid)), null, null,
                "slim".equals(DefaultPlayerSkin.getSkinModelName(uuid)));
    }

    public static TexKey defaultElytra() {
        return key(DEFAULT_ELYTRA);
    }

    // --- Pixels ---

    /** ARGB <-> ABGR: NativeImage in these versions keeps red and blue swapped. */
    static int swapRedBlue(int color) {
        return color & 0xFF00FF00 | (color & 0xFF) << 16 | color >> 16 & 0xFF;
    }

    /** Reads a texture's pixels: downloaded skins from the texture manager, built-in ones from resources. */
    public static ModelRaster.Texture readPixels(TexKey key) {
        ResourceLocation id = ResourceLocation.tryParse(key.path());
        if (id == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            AbstractTexture texture = mc.getTextureManager().getTexture(id, null);
            if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
                return pixels(dynamic.getPixels());
            }
            // Downloaded skins (HttpTexture) keep no pixels on the CPU: read them back from the GPU
            ModelRaster.Texture fromGpu = readBack(texture);
            if (fromGpu != null) {
                return fromGpu;
            }
        } catch (RuntimeException ignored) {
            // Texture not loaded yet
        }
        Optional<Resource> resource = mc.getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            return pixels(image);
        } catch (Exception e) {
            return null;
        }
    }

    /** Copies a GPU texture into memory; must run on the render thread. */
    private static ModelRaster.Texture readBack(AbstractTexture texture) {
        if (texture == null || !RenderSystem.isOnRenderThread()) {
            return null;
        }
        texture.bind();
        int width = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0) {
            return null;
        }
        try (NativeImage image = new NativeImage(width, height, false)) {
            image.downloadTexture(0, false);
            return pixels(image);
        }
    }

    private static ModelRaster.Texture pixels(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                argb[y * width + x] = swapRedBlue(image.getPixelRGBA(x, y));
            }
        }
        return new ModelRaster.Texture(width, height, argb);
    }
}
