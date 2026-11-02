package mogged.client.render;

/** UV boxes of the 64x64 skin layout, indexed as part * 2 + layer (0 = base, 1 = outer). */
public final class SkinLayout {
    // u, v, width, height, depth
    public static final int[][] BOXES = {
            {0, 0, 8, 8, 8},    // head
            {32, 0, 8, 8, 8},   // hat
            {16, 16, 8, 12, 4}, // body
            {16, 32, 8, 12, 4}, // jacket
            {40, 16, 4, 12, 4}, // right arm
            {40, 32, 4, 12, 4}, // right sleeve
            {32, 48, 4, 12, 4}, // left arm
            {48, 48, 4, 12, 4}, // left sleeve
            {0, 16, 4, 12, 4},  // right leg
            {0, 32, 4, 12, 4},  // right pants
            {16, 48, 4, 12, 4}, // left leg
            {0, 48, 4, 12, 4}   // left pants
    };

    private SkinLayout() {
    }

    /** Box index for a texel in 64 px coordinates, or -1 if the texel is unused. */
    public static int region(int x, int y) {
        for (int i = 0; i < BOXES.length; i++) {
            int[] box = BOXES[i];
            int u = box[0];
            int v = box[1];
            int width = box[2];
            int height = box[3];
            int depth = box[4];

            boolean inBounds = x >= u && x < u + 2 * depth + 2 * width && y >= v && y < v + depth + height;
            // The top row only has the top and bottom faces, its corners are unused
            boolean onFace = y >= v + depth || x >= u + depth && x < u + depth + 2 * width;
            if (inBounds && onFace) {
                return i;
            }
        }
        return -1;
    }

    /** x, y, width, height of a box's whole UV area. */
    public static int[] bounds(int index) {
        int[] box = BOXES[index];
        return new int[]{box[0], box[1], 2 * box[4] + 2 * box[2], box[4] + box[3]};
    }
}
