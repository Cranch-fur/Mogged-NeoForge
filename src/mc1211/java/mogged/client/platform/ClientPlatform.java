package mogged.client.platform;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.util.Optional;
import mogged.client.SkinView;
import mogged.client.TexKey;
import mogged.client.render.ModelRaster;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;

/** Everything on the client that differs between Minecraft versions (1.21.1). */
public final class ClientPlatform {
    private static final ResourceLocation DEFAULT_ELYTRA = ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");

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

    public static SkinView view(PlayerSkin skin) {
        return new SkinView(key(skin.texture()), key(skin.capeTexture()), key(skin.elytraTexture()), skin.model() == PlayerSkin.Model.SLIM);
    }

    /** The vanilla skin with our parts; keeps vanilla's texture URL and "secure" flag. */
    public static PlayerSkin build(PlayerSkin vanilla, SkinView view) {
        return new PlayerSkin(id(view.body()), vanilla.textureUrl(), id(view.cape()), id(view.elytra()),
                view.slim() ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, vanilla.secure());
    }

    private static ResourceLocation id(TexKey key) {
        return key == null ? null : (ResourceLocation) key.handle();
    }

    public static SkinView ownSkin() {
        return view(ownPlayerSkin());
    }

    static PlayerSkin ownPlayerSkin() {
        Minecraft mc = Minecraft.getInstance();
        try {
            return mc.getSkinManager().getInsecureSkin(mc.getGameProfile());
        } catch (RuntimeException e) {
            return DefaultPlayerSkin.get(mc.getGameProfile());
        }
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
            AbstractTexture texture = mc.getTextureManager().getTexture(id);
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
