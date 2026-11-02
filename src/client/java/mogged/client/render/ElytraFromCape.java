package mogged.client.render;

/**
 * Builds an elytra texture from a cape texture, the way some capes ship without elytra art.
 * Returns null when the cape already has its own elytra area painted.
 */
public final class ElytraFromCape {
    // Share of painted pixels in the elytra area above which we keep the original
    private static final double PAINTED_THRESHOLD = 0.3;
    private static final int ALPHA_THRESHOLD = 16;

    private ElytraFromCape() {
    }

    public static ModelRaster.Texture derive(ModelRaster.Texture cape) {
        int width = cape.width();
        int height = cape.height();
        if (width < 64 || height * 2 != width) {
            return null;
        }
        int scale = width / 64;
        int[] src = cape.argb();

        int painted = 0;
        int total = 0;
        for (int y = 0; y < 22 * scale; y++) {
            for (int x = 22 * scale; x < 46 * scale; x++) {
                total++;
                if (src[y * width + x] >>> 24 > ALPHA_THRESHOLD) {
                    painted++;
                }
            }
        }
        if (painted > total * PAINTED_THRESHOLD) {
            return null;
        }

        int[] dst = src.clone();
        // Cape back -> both wing faces, cape sides -> wing edges, cape top/bottom -> wing tops
        copy(src, dst, width, scale, 1, 1, 10, 16, 24, 2, 10, 20);
        copy(src, dst, width, scale, 1, 1, 10, 16, 36, 2, 10, 20);
        copy(src, dst, width, scale, 1, 1, 1, 16, 22, 2, 2, 20);
        copy(src, dst, width, scale, 10, 1, 1, 16, 34, 2, 2, 20);
        copy(src, dst, width, scale, 1, 1, 10, 1, 24, 0, 10, 2);
        copy(src, dst, width, scale, 1, 16, 10, 1, 34, 0, 10, 2);
        return new ModelRaster.Texture(width, height, dst);
    }

    /** Scaled copy of a source rectangle into a destination rectangle (64 px units). */
    private static void copy(int[] src, int[] dst, int width, int scale,
                             int srcX, int srcY, int srcW, int srcH, int dstX, int dstY, int dstW, int dstH) {
        for (int y = 0; y < dstH * scale; y++) {
            int sy = srcY * scale + Math.min(srcH * scale - 1, y * srcH / dstH);
            for (int x = 0; x < dstW * scale; x++) {
                int sx = srcX * scale + Math.min(srcW * scale - 1, x * srcW / dstW);
                dst[(dstY * scale + y) * width + dstX * scale + x] = src[sy * width + sx];
            }
        }
    }
}
