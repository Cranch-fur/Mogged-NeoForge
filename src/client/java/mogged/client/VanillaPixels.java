package mogged.client;

import java.util.LinkedHashMap;
import java.util.Map;
import mogged.client.platform.ClientPlatform;
import mogged.client.render.ModelRaster;

/** Pixels of textures we did not create (account skins, vanilla elytra), with a small LRU cache. */
final class VanillaPixels {
    private static final int CACHE_SIZE = 64;
    private static final Map<TexKey, ModelRaster.Texture> CACHE = new LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TexKey, ModelRaster.Texture> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private VanillaPixels() {
    }

    static ModelRaster.Texture get(TexKey key) {
        if (key == null) {
            return null;
        }
        ModelRaster.Texture cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        // Not cached when missing: downloaded skins may simply not be loaded yet
        ModelRaster.Texture pixels = ClientPlatform.readPixels(key);
        if (pixels != null) {
            CACHE.put(key, pixels);
        }
        return pixels;
    }

    static void clear() {
        CACHE.clear();
    }
}
