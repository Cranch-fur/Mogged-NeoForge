package mogged.client.platform;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Supplier;
import mogged.client.SkinView;
import mogged.client.TexKey;
import mogged.client.render.ModelRaster;
import mogged.platform.Ids;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Everything on the client that differs between Minecraft versions (26.x). */
public final class ClientPlatform {
    private static final Identifier DEFAULT_ELYTRA = Identifier.withDefaultNamespace("textures/entity/equipment/wings/elytra.png");
    private static Supplier<PlayerSkin> ownLookup;

    private ClientPlatform() {
    }

    /** The switch key's category ("key.category.mogged.main"); NeoForge registers it (registerCategory). */
    public static final KeyMapping.Category KEY_CATEGORY = new KeyMapping.Category(Ids.mod("main"));

    /** The switch key; NeoForge registers it through RegisterKeyMappingsEvent. */
    public static KeyMapping createToggleKey() {
        return new KeyMapping("key.mogged.toggle", InputConstants.KEY_K, KEY_CATEGORY);
    }

    public static void showActionBar(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(message);
        }
    }


    // --- Skins ---

    public static TexKey key(ClientAsset.Texture texture) {
        return texture == null ? null : new TexKey(texture.texturePath().toString(), texture);
    }

    public static SkinView view(PlayerSkin skin) {
        return new SkinView(key(skin.body()), key(skin.cape()), key(skin.elytra()), skin.model() == PlayerModelType.SLIM);
    }

    /** The vanilla skin with our parts; keeps vanilla's "secure" flag. */
    public static PlayerSkin build(PlayerSkin vanilla, SkinView view) {
        return new PlayerSkin(texture(view.body()), texture(view.cape()), texture(view.elytra()),
                view.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE, vanilla.secure());
    }

    private static ClientAsset.Texture texture(TexKey key) {
        return key == null ? null : (ClientAsset.Texture) key.handle();
    }

    public static SkinView ownSkin() {
        return view(ownPlayerSkin());
    }

    static PlayerSkin ownPlayerSkin() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (ownLookup == null) {
                ownLookup = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
            }
            return ownLookup.get();
        } catch (RuntimeException e) {
            return DefaultPlayerSkin.get(mc.getGameProfile());
        }
    }

    public static TexKey defaultElytra() {
        return key(new ClientAsset.ResourceTexture(DEFAULT_ELYTRA, DEFAULT_ELYTRA));
    }

    // --- Pixels ---

    /** Reads a texture's pixels: downloaded skins from the texture manager, built-in ones from resources. */
    public static ModelRaster.Texture readPixels(TexKey key) {
        Identifier id = Identifier.tryParse(key.path());
        if (id == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            AbstractTexture texture = mc.getTextureManager().getTexture(id);
            if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null && !dynamic.getPixels().isClosed()) {
                return pixels(dynamic.getPixels());
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

    private static ModelRaster.Texture pixels(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                argb[y * width + x] = image.getPixel(x, y);
            }
        }
        return new ModelRaster.Texture(width, height, argb);
    }
}
