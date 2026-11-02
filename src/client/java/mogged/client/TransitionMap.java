package mogged.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-pixel switch moments for {@link TextureBlend}.
 * For skins the value follows the height on the model, so the change runs from head to feet.
 */
final class TransitionMap {
    private static final Map<Long, float[]> SKIN_CACHE = new HashMap<>();
    private static final Map<String, float[]> GENERIC_CACHE = new HashMap<>();

    // Skin UV boxes (64 px layout): u, v, width, height, depth, model top (px from head), model height
    private static final int[][] BOXES = {
            {0, 0, 8, 8, 8, 0, 8},     // head
            {32, 0, 8, 8, 8, 0, 8},    // hat
            {16, 16, 8, 12, 4, 8, 12}, // body
            {16, 32, 8, 12, 4, 8, 12}, // jacket
            {40, 16, 4, 12, 4, 8, 12}, // right arm
            {40, 32, 4, 12, 4, 8, 12}, // right sleeve
            {32, 48, 4, 12, 4, 8, 12}, // left arm
            {48, 48, 4, 12, 4, 8, 12}, // left sleeve
            {0, 16, 4, 12, 4, 20, 12}, // right leg
            {0, 32, 4, 12, 4, 20, 12}, // right pants
            {16, 48, 4, 12, 4, 20, 12}, // left leg
            {0, 48, 4, 12, 4, 20, 12}  // left pants
    };
    private static final float MODEL_HEIGHT = 32.0F;

    private TransitionMap() {
    }

    static synchronized float[] get(int size, int seed) {
        long key = (long) size << 32 ^ seed & 0xFFFFFFFFL;
        return SKIN_CACHE.computeIfAbsent(key, k -> build(size, seed));
    }

    /** Top-to-bottom sweep with some noise, for capes and elytra. */
    static synchronized float[] generic(int width, int height, int seed) {
        return GENERIC_CACHE.computeIfAbsent(width + "x" + height + ":" + seed, key -> {
            float[] map = new float[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    float value = 0.8F * y / Math.max(1, height - 1) + 0.2F * noise(x, y, seed);
                    map[y * width + x] = clamp01(value);
                }
            }
            return map;
        });
    }

    private static float[] build(int size, int seed) {
        float[] map = new float[size * size];
        float scale = size / 64.0F;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float height = heightAt(x / scale, y / scale);
                float noise = noise(x, y, seed) * 0.6F + noise(x / 2, y / 2, seed * 31 + 7) * 0.4F;
                map[y * size + x] = clamp01(0.82F * height + 0.18F * noise);
            }
        }
        return map;
    }

    /** Relative height on the model (0 = top of head, 1 = feet) for a UV coordinate. */
    private static float heightAt(float u, float v) {
        for (int[] box : BOXES) {
            int boxU = box[0];
            int boxV = box[1];
            int width = box[2];
            int height = box[3];
            int depth = box[4];

            boolean inside = u >= boxU && u < boxU + 2 * depth + 2 * width && v >= boxV && v < boxV + depth + height;
            if (!inside) {
                continue;
            }

            float local;
            if (v >= boxV + depth) {
                // Side faces: height grows downwards
                local = (v - (boxV + depth)) / height;
            } else if (u >= boxU + depth && u < boxU + depth + 2 * width) {
                // Top face first, bottom face second
                local = u < boxU + depth + width ? 0.0F : 1.0F;
            } else {
                continue;
            }
            return (box[5] + local * box[6]) / MODEL_HEIGHT;
        }
        return 0.5F;
    }

    private static float noise(int x, int y, int seed) {
        int hash = x * 374761393 + y * 668265263 + seed * 1442695041;
        hash = (hash ^ hash >>> 13) * 1274126177;
        hash ^= hash >>> 16;
        return (hash & 0xFFFF) / 65535.0F;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
