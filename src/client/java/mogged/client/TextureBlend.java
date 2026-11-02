package mogged.client;

import mogged.client.platform.GpuTexture;
import mogged.client.render.ModelRaster;

/** Animated texture that sweeps from one image to another with a glowing edge. */
final class TextureBlend {
    static final float HALF_BAND = 0.06F;
    static final float BAND = 2 * HALF_BAND;
    private static final int MAX_SIZE = 4096;
    private final GpuTexture texture;
    private int width;
    private int height;
    private int[] from;
    private int[] to;
    private int[] frame;
    // Per-pixel moment (0..1) when the pixel switches
    private float[] threshold;

    TextureBlend(String owner, String name) {
        this.texture = new GpuTexture("dynamic/" + owner + "/" + name);
    }

    /** Texture showing the current frame (valid after a successful setup). */
    TexKey key() {
        return texture.key();
    }

    /** Prepares a blend; returns false if it cannot be done (missing or huge textures). */
    boolean setup(ModelRaster.Texture fromPixels, ModelRaster.Texture toPixels, boolean skinLayout, int seed) {
        if (fromPixels == null || toPixels == null) {
            return false;
        }
        int w = Math.max(fromPixels.width(), toPixels.width());
        int h = Math.max(fromPixels.height(), toPixels.height());
        if (w <= 0 || h <= 0 || w > MAX_SIZE || h > MAX_SIZE) {
            return false;
        }

        width = w;
        height = h;
        from = scale(fromPixels, w, h);
        to = scale(toPixels, w, h);
        frame = from.clone();
        // Upload once so the key exists before the first frame is drawn
        texture.upload(frame, w, h);
        threshold = skinLayout && w == h ? TransitionMap.get(w, seed) : TransitionMap.generic(w, h, seed);
        return true;
    }

    /** Nearest-neighbour resize. */
    private static int[] scale(ModelRaster.Texture pixels, int w, int h) {
        if (pixels.width() == w && pixels.height() == h) {
            return pixels.argb();
        }
        int[] result = new int[w * h];
        for (int y = 0; y < h; y++) {
            int srcY = Math.min(pixels.height() - 1, y * pixels.height() / h);
            for (int x = 0; x < w; x++) {
                int srcX = Math.min(pixels.width() - 1, x * pixels.width() / w);
                result[y * w + x] = pixels.argb()[srcY * pixels.width() + srcX];
            }
        }
        return result;
    }

    /**
     * Redraws the texture for a sweep position. Each pixel has a threshold from {@link TransitionMap}:
     * pixels well below the line show the new image, well above it the old one,
     * and pixels within {@link #HALF_BAND} of the line glow.
     */
    void update(float line) {
        if (from == null) {
            return;
        }
        int size = width * height;

        for (int i = 0; i < size; i++) {
            float delta = line - threshold[i];
            int color;
            if (delta < -HALF_BAND) {
                color = from[i];
            } else if (delta > HALF_BAND) {
                color = to[i];
            } else {
                float t = (delta + HALF_BAND) / BAND;
                float strength = 1.0F - Math.abs(t - 0.5F) * 2.0F;
                color = glow(t < 0.5F ? from[i] : to[i], from[i], to[i], strength);
            }
            frame[i] = color;
        }
        texture.upload(frame, width, height);
    }

    /** Tints a pixel towards cyan-white; transparent pixels inside the band light up too. */
    private static int glow(int base, int a, int b, float strength) {
        int alpha = Math.max(base >>> 24, (int) (Math.max(a >>> 24, b >>> 24) * strength));
        float mix = 0.25F + 0.75F * strength;
        int red = base >> 16 & 0xFF;
        int green = base >> 8 & 0xFF;
        int blue = base & 0xFF;
        red = (int) (red + (170 - red) * mix * 0.9F + 60.0F * strength);
        green = (int) (green + (245 - green) * mix);
        blue = (int) (blue + (255 - blue) * mix);
        return Math.min(255, alpha) << 24 | Math.min(255, red) << 16 | Math.min(255, green) << 8 | Math.min(255, blue);
    }

    void clearData() {
        from = null;
        to = null;
        frame = null;
    }

    void release() {
        texture.release();
    }
}
