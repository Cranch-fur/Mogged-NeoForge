package mogged.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tiny CPU rasterizer for the player model, used by GUI previews and the skin editor.
 * Besides color it fills a pick buffer: for every pixel, which texture texel is visible there.
 */
public final class ModelRaster {
    public static final int TARGET_SKIN = 1;
    public static final int TARGET_CAPE = 2;
    public static final int TARGET_ELYTRA = 3;

    public static final int BACK_NONE = 0;
    public static final int BACK_CAPE = 1;
    public static final int BACK_ELYTRA = 2;

    public static final int HEAD = 0;
    public static final int BODY = 1;
    public static final int RIGHT_ARM = 2;
    public static final int LEFT_ARM = 3;
    public static final int RIGHT_LEG = 4;
    public static final int LEFT_LEG = 5;
    public static final int PARTS = 6;

    private static final int BASE = 0;
    private static final int OUTER = 1;
    // Pick value layout: target in the top 4 bits, texel index + 1 in the rest
    private static final int TARGET_SHIFT = 28;
    private static final int TEXEL_MASK = (1 << TARGET_SHIFT) - 1;
    private static final float EPSILON = 1.0E-6F;
    private static final float EDGE_TOLERANCE = 1.0E-4F;

    private int width;
    private int height;
    private int[] color;
    private float[] depth;
    private int[] pick;
    private float[] pickDepth;

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int[] color() {
        return color;
    }

    public int pickAt(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height ? pick[y * width + x] : 0;
    }

    public static int target(int pick) {
        return pick >>> TARGET_SHIFT;
    }

    public static int texel(int pick) {
        return (pick & TEXEL_MASK) - 1;
    }

    // --- 3x4 affine matrices (row-major) ---

    static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
    }

    static float[] mul(float[] a, float[] b) {
        float[] result = new float[12];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                result[row * 4 + col] = a[row * 4] * b[col] + a[row * 4 + 1] * b[4 + col] + a[row * 4 + 2] * b[8 + col];
            }
            result[row * 4 + 3] = a[row * 4] * b[3] + a[row * 4 + 1] * b[7] + a[row * 4 + 2] * b[11] + a[row * 4 + 3];
        }
        return result;
    }

    static float[] translate(float x, float y, float z) {
        return new float[]{1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z};
    }

    static float[] rotX(float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        return new float[]{1, 0, 0, 0, 0, cos, -sin, 0, 0, sin, cos, 0};
    }

    static float[] rotY(float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        return new float[]{cos, 0, sin, 0, 0, 1, 0, 0, -sin, 0, cos, 0};
    }

    static float[] rotZ(float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        return new float[]{cos, -sin, 0, 0, sin, cos, 0, 0, 0, 0, 1, 0};
    }

    static float[] pose(float x, float y, float z, float rx, float ry, float rz) {
        return mul(translate(x, y, z), mul(rotZ(rz), mul(rotY(ry), rotX(rx))));
    }

    /** Same cuboids and UVs as the vanilla player model, in model pixels. */
    private static List<Box> buildBoxes(Scene scene) {
        List<Box> boxes = new ArrayList<>();
        float[] root = identity();

        boxes.add(new Box(TARGET_SKIN, HEAD, BASE, -4, -8, -4, 8, 8, 8, 0.0F, false, 0, 0, root));
        boxes.add(new Box(TARGET_SKIN, HEAD, OUTER, -4, -8, -4, 8, 8, 8, 0.5F, false, 32, 0, root));
        boxes.add(new Box(TARGET_SKIN, BODY, BASE, -4, 0, -2, 8, 12, 4, 0.0F, false, 16, 16, root));
        boxes.add(new Box(TARGET_SKIN, BODY, OUTER, -4, 0, -2, 8, 12, 4, 0.25F, false, 16, 32, root));

        int armWidth = scene.slim ? 3 : 4;
        float armY = scene.slim ? 2.5F : 2.0F;
        float rightArmX = scene.slim ? -2.0F : -3.0F;
        float[] rightArm = pose(-5, armY, 0, 0, 0, 0.06F);
        float[] leftArm = pose(5, armY, 0, 0, 0, -0.06F);
        boxes.add(new Box(TARGET_SKIN, RIGHT_ARM, BASE, rightArmX, -2, -2, armWidth, 12, 4, 0.0F, false, 40, 16, rightArm));
        boxes.add(new Box(TARGET_SKIN, RIGHT_ARM, OUTER, rightArmX, -2, -2, armWidth, 12, 4, 0.25F, false, 40, 32, rightArm));
        boxes.add(new Box(TARGET_SKIN, LEFT_ARM, BASE, -1, -2, -2, armWidth, 12, 4, 0.0F, false, 32, 48, leftArm));
        boxes.add(new Box(TARGET_SKIN, LEFT_ARM, OUTER, -1, -2, -2, armWidth, 12, 4, 0.25F, false, 48, 48, leftArm));

        float[] rightLeg = pose(-1.9F, 12, 0, 0, 0, 0);
        float[] leftLeg = pose(1.9F, 12, 0, 0, 0, 0);
        boxes.add(new Box(TARGET_SKIN, RIGHT_LEG, BASE, -2, 0, -2, 4, 12, 4, 0.0F, false, 0, 16, rightLeg));
        boxes.add(new Box(TARGET_SKIN, RIGHT_LEG, OUTER, -2, 0, -2, 4, 12, 4, 0.25F, false, 0, 32, rightLeg));
        boxes.add(new Box(TARGET_SKIN, LEFT_LEG, BASE, -2, 0, -2, 4, 12, 4, 0.0F, false, 16, 48, leftLeg));
        boxes.add(new Box(TARGET_SKIN, LEFT_LEG, OUTER, -2, 0, -2, 4, 12, 4, 0.25F, false, 0, 48, leftLeg));

        if (scene.back == BACK_CAPE && scene.cape != null) {
            float[] cape = mul(translate(0, 0, 2.2F), mul(rotX(0.12F), rotY((float) Math.PI)));
            boxes.add(new Box(TARGET_CAPE, -1, BASE, -5, 0, -1, 10, 16, 1, 0.0F, false, 0, 0, cape));
        } else if (scene.back == BACK_ELYTRA && scene.elytra != null) {
            float wingAngle = (float) (Math.PI / 12);
            float[] leftWing = mul(translate(0, 0, 2.5F), pose(5, 0, 0, wingAngle, 0, -wingAngle));
            float[] rightWing = mul(translate(0, 0, 2.5F), pose(-5, 0, 0, wingAngle, 0, wingAngle));
            boxes.add(new Box(TARGET_ELYTRA, -1, BASE, -10, 0, 0, 10, 20, 2, 0.0F, false, 22, 0, leftWing));
            boxes.add(new Box(TARGET_ELYTRA, -1, BASE, 0, 0, 0, 10, 20, 2, 0.0F, true, 22, 0, rightWing));
        }
        return boxes;
    }

    public void render(Scene scene, int targetWidth, int targetHeight) {
        if (color == null || width != targetWidth || height != targetHeight) {
            width = Math.max(1, targetWidth);
            height = Math.max(1, targetHeight);
            color = new int[width * height];
            depth = new float[width * height];
            pick = new int[width * height];
            pickDepth = new float[width * height];
        }
        Arrays.fill(color, 0);
        Arrays.fill(depth, Float.POSITIVE_INFINITY);
        Arrays.fill(pick, 0);
        Arrays.fill(pickDepth, Float.POSITIVE_INFINITY);

        // Fit the model (36 px tall, wider with spread wings) into the target
        float modelWidth = scene.back == BACK_ELYTRA ? 26.0F : 22.0F;
        float scale = Math.min(height / 36.0F, width / modelWidth) * scene.zoom;
        float centerX = width / 2.0F;
        float centerY = height / 2.0F - 8.0F * scale;
        float[] view = mul(rotX((float) Math.toRadians(scene.pitch)), rotY((float) Math.toRadians(scene.yaw)));

        for (Box box : buildBoxes(scene)) {
            if (box.target == TARGET_SKIN && (scene.skin == null || !scene.visible[box.part][box.layer])) {
                continue;
            }
            Texture texture = switch (box.target) {
                case TARGET_SKIN -> scene.skin;
                case TARGET_CAPE -> scene.cape;
                default -> scene.elytra;
            };
            if (texture != null) {
                drawBox(scene, box, texture, mul(view, box.matrix), centerX, centerY, scale);
            }
        }
    }

    private void drawBox(Scene scene, Box box, Texture texture, float[] m, float centerX, float centerY, float scale) {
        float texScale = texture.width() / 64.0F;
        float[][] corners = {
                {box.x0, box.y0, box.z0}, {box.x1, box.y0, box.z0}, {box.x1, box.y1, box.z0}, {box.x0, box.y1, box.z0},
                {box.x0, box.y0, box.z1}, {box.x1, box.y0, box.z1}, {box.x1, box.y1, box.z1}, {box.x0, box.y1, box.z1}
        };
        float[][] projected = new float[8][3];
        for (int i = 0; i < 8; i++) {
            float x = corners[i][0];
            float y = corners[i][1];
            float z = corners[i][2];
            projected[i][0] = centerX + (m[0] * x + m[1] * y + m[2] * z + m[3]) * scale;
            projected[i][1] = centerY + (m[4] * x + m[5] * y + m[6] * z + m[7]) * scale;
            projected[i][2] = m[8] * x + m[9] * y + m[10] * z + m[11];
        }

        // Standard cuboid UV unwrap
        float u0 = box.u;
        float u1 = box.u + box.d;
        float u2 = box.u + box.d + box.w;
        float u3 = box.u + box.d + box.w + box.w;
        float u4 = box.u + box.d + box.w + box.d;
        float u5 = box.u + box.d + box.w + box.d + box.w;
        float v0 = box.v;
        float v1 = box.v + box.d;
        float v2 = box.v + box.d + box.h;

        face(scene, box, texture, texScale, projected, new int[]{5, 4, 0, 1}, u1, v0, u2, v1); // top
        face(scene, box, texture, texScale, projected, new int[]{2, 3, 7, 6}, u2, v1, u3, v0); // bottom
        face(scene, box, texture, texScale, projected, new int[]{0, 4, 7, 3}, u0, v1, u1, v2); // right
        face(scene, box, texture, texScale, projected, new int[]{1, 0, 3, 2}, u1, v1, u2, v2); // front
        face(scene, box, texture, texScale, projected, new int[]{5, 1, 2, 6}, u2, v1, u4, v2); // left
        face(scene, box, texture, texScale, projected, new int[]{4, 5, 6, 7}, u4, v1, u5, v2); // back
    }

    private void face(Scene scene, Box box, Texture texture, float texScale, float[][] points, int[] index,
                      float uA, float vA, float uB, float vB) {
        float[] us = {uB, uA, uA, uB};
        float[] vs = {vA, vA, vB, vB};
        float[] p0 = points[index[0]];
        float[] p1 = points[index[1]];
        float[] p2 = points[index[2]];

        // Simple directional light from the face normal; normals are flipped to face the viewer
        // because mirrored boxes (elytra) have reversed winding
        float ax = p1[0] - p0[0];
        float ay = p1[1] - p0[1];
        float az = p1[2] - p0[2];
        float bx = p2[0] - p0[0];
        float by = p2[1] - p0[1];
        float bz = p2[2] - p0[2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float shade = 1.0F;
        if (length > EPSILON) {
            nx /= length;
            ny /= length;
            nz /= length;
            if (nz > 0.0F) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            float light = -0.25F * nx - 0.55F * ny - 0.8F * nz;
            shade = 0.55F + 0.45F * Math.max(0.0F, light);
        }

        // Clamp UVs to this face so edges never bleed into neighbours
        float[] uvBounds = {
                Math.min(uA, uB) * texScale, Math.max(uA, uB) * texScale,
                Math.min(vA, vB) * texScale, Math.max(vA, vB) * texScale
        };
        triangle(scene, box, texture, texScale, points[index[0]], points[index[1]], points[index[2]],
                us[0], vs[0], us[1], vs[1], us[2], vs[2], shade, uvBounds);
        triangle(scene, box, texture, texScale, points[index[0]], points[index[2]], points[index[3]],
                us[0], vs[0], us[2], vs[2], us[3], vs[3], shade, uvBounds);
    }

    private void triangle(Scene scene, Box box, Texture texture, float texScale, float[] a, float[] b, float[] c,
                          float ua, float va, float ub, float vb, float uc, float vc, float shade, float[] uvBounds) {
        float area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        if (Math.abs(area) < EPSILON) {
            return;
        }

        int minX = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        boolean outer = box.layer == OUTER;
        int texWidth = texture.width();
        int texHeight = texture.height();
        int[] texels = texture.argb();

        for (int y = minY; y <= maxY; y++) {
            float py = y + 0.5F;
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5F;

                // Barycentric coordinates of the pixel center; all >= 0 means inside the triangle.
                // A small negative tolerance avoids hairline gaps between neighbouring triangles
                float wa = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area;
                float wb = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area;
                float wc = 1.0F - wa - wb;
                if (wa < -EDGE_TOLERANCE || wb < -EDGE_TOLERANCE || wc < -EDGE_TOLERANCE) {
                    continue;
                }

                float z = wa * a[2] + wb * b[2] + wc * c[2];
                float u = Math.max(uvBounds[0], Math.min(uvBounds[1] - 0.001F, (wa * ua + wb * ub + wc * uc) * texScale));
                float v = Math.max(uvBounds[2], Math.min(uvBounds[3] - 0.001F, (wa * va + wb * vb + wc * vc) * texScale));
                int texel = Math.min(texHeight - 1, Math.max(0, (int) v)) * texWidth + Math.min(texWidth - 1, Math.max(0, (int) u));
                int argb = texels[texel];
                int alpha = argb >>> 24;
                int pixel = y * width + x;

                // Empty outer-layer texels can still be picked so the editor can paint on them
                int pickValue = box.target << TARGET_SHIFT | texel + 1;
                boolean pickable = alpha > 0 || outer && scene.pickTransparentOverlay;
                if (pickable && z < pickDepth[pixel]) {
                    pickDepth[pixel] = z;
                    pick[pixel] = pickValue;
                }

                if (alpha == 0 || z >= depth[pixel]) {
                    continue;
                }
                int red = (int) ((argb >> 16 & 0xFF) * shade);
                int green = (int) ((argb >> 8 & 0xFF) * shade);
                int blue = (int) ((argb & 0xFF) * shade);
                if (scene.highlight != 0 && scene.highlight == pickValue) {
                    red = 255 - red / 2;
                    green = 255 - green / 2;
                    blue = 255 - blue / 2;
                }

                int rgb = red << 16 | green << 8 | blue;
                // Blend semi-transparent texels over what is already drawn
                if (alpha < 255 && color[pixel] >>> 24 != 0) {
                    int under = color[pixel];
                    float f = alpha / 255.0F;
                    rgb = (int) (red * f + (under >> 16 & 0xFF) * (1.0F - f)) << 16
                            | (int) (green * f + (under >> 8 & 0xFF) * (1.0F - f)) << 8
                            | (int) (blue * f + (under & 0xFF) * (1.0F - f));
                    alpha = 255;
                }
                color[pixel] = alpha << 24 | rgb;
                if (alpha == 255) {
                    depth[pixel] = z;
                }
            }
        }
    }

    private static final class Box {
        final int target;
        final int part;
        final int layer;
        final float x0;
        final float y0;
        final float z0;
        final float x1;
        final float y1;
        final float z1;
        final int u;
        final int v;
        final int w;
        final int h;
        final int d;
        final float[] matrix;

        Box(int target, int part, int layer, float x, float y, float z, int w, int h, int d, float inflate,
            boolean mirror, int u, int v, float[] matrix) {
            this.target = target;
            this.part = part;
            this.layer = layer;
            float left = x - inflate;
            float right = x + w + inflate;
            this.x0 = mirror ? right : left;
            this.x1 = mirror ? left : right;
            this.y0 = y - inflate;
            this.y1 = y + h + inflate;
            this.z0 = z - inflate;
            this.z1 = z + d + inflate;
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
            this.d = d;
            this.matrix = matrix;
        }
    }

    public static final class Scene {
        public Texture skin;
        public boolean slim;
        public Texture cape;
        public Texture elytra;
        public int back = BACK_CAPE;
        // [part][layer]
        public final boolean[][] visible = {{true, true}, {true, true}, {true, true}, {true, true}, {true, true}, {true, true}};
        public float yaw = 25.0F;
        public float pitch = -8.0F;
        public float zoom = 1.0F;
        public boolean pickTransparentOverlay;
        public int highlight;
    }

    public record Texture(int width, int height, int[] argb) {
    }
}
