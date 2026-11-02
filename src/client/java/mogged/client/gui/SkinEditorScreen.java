package mogged.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import mogged.Mogged;
import mogged.client.ClientSkins;
import mogged.client.SkinConfig;
import mogged.client.SkinImages;
import mogged.client.SkinTexture;
import mogged.client.SkinView;
import mogged.client.platform.BaseScreen;
import mogged.client.platform.GpuTexture;
import mogged.client.platform.KeyPress;
import mogged.client.render.ElytraFromCape;
import mogged.client.render.ModelRaster;
import mogged.client.render.SkinLayout;
import mogged.net.SkinKinds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

/** Pixel editor for a preset's skin and cape: 2D canvas on the left, paintable 3D view on the right. */
public class SkinEditorScreen extends BaseScreen {
    private static final int MODE_SKIN = 0;
    private static final int MODE_CAPE = 1;

    private static final int PENCIL = 0;
    private static final int ERASER = 1;
    private static final int PICKER = 2;
    private static final int FILL = 3;
    private static final int TOOLS = 4;
    private static final String[] TOOL_KEYS = {
            "mogged.editor.tool.pencil", "mogged.editor.tool.eraser", "mogged.editor.tool.picker", "mogged.editor.tool.fill"
    };
    private static final String[] PART_KEYS = {
            "mogged.editor.part.head", "mogged.editor.part.body",
            "mogged.editor.part.right_arm", "mogged.editor.part.left_arm",
            "mogged.editor.part.right_leg", "mogged.editor.part.left_leg"
    };
    // Minecraft dye colors, greys and a few skin tones
    private static final int[] PALETTE = {
            0xFF000000, 0xFF3F3F3F, 0xFF7F7F7F, 0xFFBFBFBF, 0xFFFFFFFF, 0xFFB02E26, 0xFFF9801D, 0xFFFED83D,
            0xFF80C71F, 0xFF5E7C16, 0xFF169C9C, 0xFF3AB3DA, 0xFF3C44AA, 0xFF8932B8, 0xFFC74EBD, 0xFF835432,
            0xFFF3CBA0, 0xFFD9A066, 0xFFA0673C, 0xFF6B3E26, 0xFFF38BAA, 0xFF2D1B0F, 0xFFE6C17B, 0xFF4E2F1F
    };
    private static final int PALETTE_COLUMNS = 8;
    private static final int SWATCH = 11;
    private static final int MAX_UNDO = 60;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MESSAGE_MILLIS = 4000;
    private static final float MIN_ZOOM = 0.5F;
    private static final float MAX_ZOOM = 48.0F;
    private static final float GRID_ZOOM = 8.0F;
    private static final int CHECKER = 6;

    // What the mouse is currently dragging
    private static final int DRAG_NONE = 0;
    private static final int DRAG_PAINT_2D = 1;
    private static final int DRAG_PAN_2D = 2;
    private static final int DRAG_PAINT_3D = 3;
    private static final int DRAG_ROTATE_3D = 4;
    private static final int DRAG_SV = 5;
    private static final int DRAG_HUE = 6;

    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int FRAME_COLOR = 0xFF000000;

    private final Screen parent;
    private final int preset;
    private final Canvas skin;
    private final Canvas cape;
    private boolean slim;
    private int mode = MODE_SKIN;
    private int tool = PENCIL;
    private int color = 0xFF3AB3DA;
    private float hue;
    private float saturation;
    private float value;
    // [part][layer] visibility; hidden parts are also locked for painting
    private final boolean[][] visible = {{true, true}, {true, true}, {true, true}, {true, true}, {true, true}, {true, true}};
    private boolean paintOuter = true;

    // Layout
    private int areaY;
    private int areaHeight;
    private int canvasX;
    private int canvasWidth;
    private int viewX;
    private int viewWidth;
    private int svX;
    private int svY;
    private int svWidth;
    private int svHeight;
    private int hueY;
    private int swatchY;
    private int paletteY;

    // 2D canvas view; zoom <= 0 means "fit on next frame"
    private float zoom = -1.0F;
    private float panX;
    private float panY;
    private final GpuTexture canvasTexture = new GpuTexture("gui/editor_canvas");
    private boolean canvasDirty = true;

    // 3D view
    private final ModelRaster raster = new ModelRaster();
    private final ModelRaster.Scene scene = new ModelRaster.Scene();
    private final GpuTexture viewTexture = new GpuTexture("gui/editor_view");
    private boolean viewDirty = true;
    private float yaw = 25.0F;
    private float pitch = -8.0F;
    private float viewZoom = 1.0F;
    private int renderScale = 1;
    private int hoverPick;
    private ModelRaster.Texture derivedElytra;
    private boolean elytraDerived;

    private int drag = DRAG_NONE;
    private int lastTexel = -1;

    private EditBox hexBox;
    private boolean updatingHex;
    private final Button[] toolButtons = new Button[TOOLS];
    private final Button[] partButtons = new Button[ModelRaster.PARTS * 2];
    private Button tabSkin;
    private Button tabCape;
    private Button hdButton;
    private Button modelButton;
    private Button layerButton;
    private Button showButton;
    private Component message;
    private long messageUntil;

    public SkinEditorScreen(Screen parent, int preset) {
        super(Component.translatable("mogged.editor.title", ClientSkins.presetName(preset)));
        this.parent = parent;
        this.preset = preset;
        SkinConfig.Preset config = SkinConfig.get().preset(preset);

        // Start from the preset's skin, or the account skin if it has none
        SkinTexture presetSkin = ClientSkins.SELF.textures[SkinKinds.skin(preset)];
        ModelRaster.Texture skinPixels;
        if (presetSkin != null) {
            skinPixels = presetSkin.pixels();
            slim = presetSkin.slim;
        } else {
            SkinView own = ClientSkins.ownVanillaSkin();
            skinPixels = ClientSkins.SELF.pixelsOf(own.body());
            slim = own.slim();
            if (config.skin != null && !config.skin.isBlank()) {
                slim = config.slim;
            }
        }
        if (skinPixels == null || skinPixels.width() != skinPixels.height()) {
            skinPixels = new ModelRaster.Texture(64, 64, new int[64 * 64]);
        }
        skin = new Canvas(skinPixels.width(), skinPixels.height(), skinPixels.argb().clone());

        SkinTexture presetCape = ClientSkins.SELF.textures[SkinKinds.cape(preset)];
        ModelRaster.Texture capePixels = presetCape != null
                ? presetCape.pixels()
                : ClientSkins.SELF.pixelsOf(ClientSkins.ownVanillaSkin().cape());
        if (capePixels == null || capePixels.width() != capePixels.height() * 2) {
            capePixels = blankCape(64);
        }
        cape = new Canvas(capePixels.width(), capePixels.height(), capePixels.argb().clone());

        setColor(color);
    }

    /** Grey cape template with front and back marked. */
    private static ModelRaster.Texture blankCape(int width) {
        int scale = width / 64;
        int[] argb = new int[width * width / 2];
        for (int y = 0; y < 17 * scale; y++) {
            for (int x = 0; x < 22 * scale; x++) {
                int c = 0xFF4A4A4A;
                if (y >= scale && x >= scale && x < 11 * scale) {
                    c = 0xFF5E5E5E;
                }
                if (y >= scale && x >= 12 * scale && x < 22 * scale) {
                    c = 0xFF3A3A3A;
                }
                argb[y * width + x] = c;
            }
        }
        return new ModelRaster.Texture(width, width / 2, argb);
    }

    private Canvas canvas() {
        return mode == MODE_SKIN ? skin : cape;
    }

    @Override
    protected void init() {
        int left = 6;
        int sideWidth = 92;
        int top = 6;

        // Top bar
        int x = left;
        tabSkin = addRenderableWidget(Button.builder(Component.translatable("mogged.editor.tab.skin"), b -> setMode(MODE_SKIN))
                .bounds(x, top, 46, BUTTON_HEIGHT).build());
        x += 48;
        tabCape = addRenderableWidget(Button.builder(Component.translatable("mogged.editor.tab.cape"), b -> setMode(MODE_CAPE))
                .bounds(x, top, 46, BUTTON_HEIGHT).build());
        x += 52;
        hdButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleHd())
                .bounds(x, top, 60, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("mogged.editor.tooltip.hd")))
                .build());
        x += 62;
        modelButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            slim = !slim;
            viewDirty = true;
            updateLabels();
        }).bounds(x, top, 96, BUTTON_HEIGHT).build());
        layerButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    paintOuter = !paintOuter;
                    viewDirty = true;
                    updateLabels();
                })
                .bounds(x + 98, top, 96, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("mogged.editor.tooltip.layer")))
                .build());
        // Cape mode only, shares the slot of the model button
        showButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            PreviewBack.toggle(preset);
            viewDirty = true;
            updateLabels();
        }).bounds(x, top, 96, BUTTON_HEIGHT).build());
        x += 196;
        addRenderableWidget(Button.builder(Component.translatable("mogged.editor.undo"), b -> undo())
                .bounds(x, top, 44, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("mogged.editor.redo"), b -> redo())
                .bounds(x + 46, top, 44, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("?"), b -> {
                })
                .bounds(width - 26, top, 20, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("mogged.editor.help")))
                .build());

        // Left side: tools and color picker
        int y = top + 26;
        for (int i = 0; i < TOOLS; i++) {
            int toolIndex = i;
            toolButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                tool = toolIndex;
                updateLabels();
            }).bounds(left + i % 2 * 47, y + i / 2 * 22, 45, BUTTON_HEIGHT).build());
        }
        y += 46;
        svX = left;
        svY = y;
        svWidth = sideWidth;
        svHeight = 46;
        hueY = svY + svHeight + 3;
        swatchY = hueY + 10;
        hexBox = new EditBox(font, left + 20, swatchY, sideWidth - 20, 16, Component.literal("#RRGGBB"));
        hexBox.setMaxLength(9);
        hexBox.setResponder(this::onHexTyped);
        addRenderableWidget(hexBox);
        paletteY = swatchY + 20;

        // Work area: 2D canvas and 3D view
        int bottomY = height - 24;
        int partsY = bottomY - 46;
        areaY = top + 26;
        areaHeight = Math.max(40, partsY - 4 - areaY);
        int areaX = left + sideWidth + 8;
        int areaWidth = width - areaX - 6;
        canvasX = areaX;
        canvasWidth = areaWidth * 55 / 100;
        viewX = canvasX + canvasWidth + 6;
        viewWidth = areaWidth - canvasWidth - 6;

        // Part visibility toggles, one row per layer
        int labelWidth = 40;
        int partWidth = (areaWidth - labelWidth) / ModelRaster.PARTS;
        for (int part = 0; part < ModelRaster.PARTS; part++) {
            for (int layer = 0; layer < 2; layer++) {
                int p = part;
                int l = layer;
                partButtons[part * 2 + layer] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    visible[p][l] = !visible[p][l];
                    viewDirty = true;
                    updateLabels();
                }).bounds(areaX + labelWidth + part * partWidth, partsY + layer * 22, partWidth - 2, BUTTON_HEIGHT).build());
            }
        }

        // Bottom bar
        String[] keys = {"mogged.editor.apply.skin", "mogged.editor.apply.cape", "mogged.editor.apply.both", "mogged.editor.export", "gui.done"};
        Runnable[] actions = {() -> apply(true, false), () -> apply(false, true), () -> apply(true, true), this::export, this::onClose};
        int buttonWidth = (width - 12 - 8) / keys.length;
        for (int i = 0; i < keys.length; i++) {
            Runnable action = actions[i];
            addRenderableWidget(Button.builder(Component.translatable(keys[i]), b -> action.run())
                    .bounds(6 + i * (buttonWidth + 2), bottomY, buttonWidth, BUTTON_HEIGHT).build());
        }

        renderScale = Mth.clamp((int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()), 1, 4);
        zoom = -1.0F;
        viewDirty = true;
        canvasDirty = true;
        updateLabels();
        updateHex();
    }

    private void updateLabels() {
        tabSkin.active = mode != MODE_SKIN;
        tabCape.active = mode != MODE_CAPE;
        hdButton.setMessage(Component.translatable("mogged.editor.hd",
                canvas().width > 64 ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
        modelButton.setMessage(Component.translatable("mogged.button.model",
                Component.translatable(slim ? "mogged.model.slim" : "mogged.model.classic")));
        layerButton.setMessage(Component.translatable("mogged.editor.layer",
                Component.translatable(paintOuter ? "mogged.editor.layer.outer" : "mogged.editor.layer.base")));
        showButton.setMessage(Component.translatable("mogged.editor.show", PreviewBack.label(preset)));
        modelButton.visible = mode == MODE_SKIN;
        layerButton.visible = mode == MODE_SKIN;
        showButton.visible = mode == MODE_CAPE;

        for (int i = 0; i < TOOLS; i++) {
            MutableComponent label = Component.translatable(TOOL_KEYS[i]);
            toolButtons[i].setMessage(tool == i ? label.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD) : label);
        }
        for (int part = 0; part < ModelRaster.PARTS; part++) {
            for (int layer = 0; layer < 2; layer++) {
                Button button = partButtons[part * 2 + layer];
                MutableComponent label = Component.translatable(PART_KEYS[part]);
                button.setMessage(visible[part][layer]
                        ? label
                        : label.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH));
                button.visible = mode == MODE_SKIN;
            }
        }
    }

    private void setMode(int newMode) {
        mode = newMode;
        zoom = -1.0F;
        canvasDirty = true;
        viewDirty = true;
        updateLabels();
    }

    // --- Color ---

    private void setColor(int argb) {
        color = argb;
        float r = (argb >> 16 & 0xFF) / 255.0F;
        float g = (argb >> 8 & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        value = max;
        saturation = max == 0.0F ? 0.0F : (max - min) / max;
        // Keep the previous hue for greys
        if (max != min) {
            float h;
            if (max == r) {
                h = (g - b) / (max - min) + (g < b ? 6 : 0);
            } else if (max == g) {
                h = (b - r) / (max - min) + 2.0F;
            } else {
                h = (r - g) / (max - min) + 4.0F;
            }
            hue = h / 6.0F;
        }
        updateHex();
    }

    private static int hsv(float h, float s, float v) {
        return 0xFF000000 | Mth.hsvToRgb((h % 1.0F + 1.0F) % 1.0F, Mth.clamp(s, 0.0F, 1.0F), Mth.clamp(v, 0.0F, 1.0F));
    }

    private void setFromHsv() {
        color = color & 0xFF000000 | hsv(hue, saturation, value) & 0x00FFFFFF;
        if (color >>> 24 == 0) {
            color |= 0xFF000000;
        }
        updateHex();
    }

    private void updateHex() {
        if (hexBox == null) {
            return;
        }
        updatingHex = true;
        int alpha = color >>> 24;
        hexBox.setValue(alpha == 255 ? String.format("#%06X", color & 0x00FFFFFF) : String.format("#%08X", color));
        updatingHex = false;
    }

    private void onHexTyped(String text) {
        if (updatingHex) {
            return;
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            if (hex.length() == 6) {
                setColorKeepText(0xFF000000 | Integer.parseInt(hex, 16));
            } else if (hex.length() == 8) {
                setColorKeepText((int) Long.parseLong(hex, 16));
            }
        } catch (NumberFormatException ignored) {
            // Still typing
        }
    }

    private void setColorKeepText(int argb) {
        updatingHex = true;
        setColor(argb);
        updatingHex = false;
    }

    // --- Undo ---

    private void pushUndo(Canvas canvas) {
        canvas.undo.push(canvas.pixels.clone());
        while (canvas.undo.size() > MAX_UNDO) {
            canvas.undo.removeLast();
        }
        canvas.redo.clear();
    }

    private void undo() {
        Canvas canvas = canvas();
        if (!canvas.undo.isEmpty()) {
            canvas.redo.push(canvas.pixels);
            canvas.pixels = canvas.undo.pop();
            changed();
        }
    }

    private void redo() {
        Canvas canvas = canvas();
        if (!canvas.redo.isEmpty()) {
            canvas.undo.push(canvas.pixels);
            canvas.pixels = canvas.redo.pop();
            changed();
        }
    }

    private void changed() {
        canvasDirty = true;
        viewDirty = true;
        if (mode == MODE_CAPE) {
            derivedElytra = null;
        }
    }

    // --- Painting ---

    /** Skin box of a texel, -1 if unused, -2 if its part is hidden (locked). */
    private int skinRegion(int texel) {
        // Texel index -> 64 px layout coordinates -> UV box (part * 2 + layer)
        int scale = skin.width / 64;
        int region = SkinLayout.region(texel % skin.width / scale, texel / skin.width / scale);
        return region >= 0 && !visible[region / 2][region % 2] ? -2 : region;
    }

    private boolean editable(Canvas canvas, int texel) {
        if (texel < 0 || texel >= canvas.pixels.length) {
            return false;
        }
        return canvas != skin || skinRegion(texel) >= 0;
    }

    private void applyTool(Canvas canvas, int texel) {
        if (!editable(canvas, texel)) {
            return;
        }
        // Base layer is always opaque in Minecraft
        boolean baseLayer = canvas == skin && skinRegion(texel) % 2 == 0;
        switch (tool) {
            case PENCIL -> canvas.pixels[texel] = baseLayer ? color | 0xFF000000 : color;
            case ERASER -> {
                if (!baseLayer) {
                    canvas.pixels[texel] = 0;
                }
            }
            case PICKER -> {
                int picked = canvas.pixels[texel];
                if (picked >>> 24 != 0) {
                    setColor(picked);
                }
                return;
            }
            case FILL -> fill(canvas, texel, baseLayer);
            default -> {
                return;
            }
        }
        changed();
    }

    /** Flood fill limited to the same skin box. */
    private void fill(Canvas canvas, int start, boolean baseLayer) {
        int target = canvas.pixels[start];
        int replacement = baseLayer ? color | 0xFF000000 : color;
        if (target == replacement) {
            return;
        }
        int region = canvas == skin ? skinRegion(start) : 0;
        int[] stack = new int[canvas.pixels.length];
        boolean[] seen = new boolean[canvas.pixels.length];
        int size = 0;
        stack[size++] = start;
        seen[start] = true;

        while (size > 0) {
            int texel = stack[--size];
            canvas.pixels[texel] = replacement;
            int x = texel % canvas.width;
            int y = texel / canvas.width;
            int[] neighbours = {
                    x > 0 ? texel - 1 : -1,
                    x < canvas.width - 1 ? texel + 1 : -1,
                    y > 0 ? texel - canvas.width : -1,
                    y < canvas.height - 1 ? texel + canvas.width : -1
            };
            for (int next : neighbours) {
                if (next >= 0 && !seen[next] && canvas.pixels[next] == target
                        && (canvas != skin || skinRegion(next) == region)) {
                    seen[next] = true;
                    stack[size++] = next;
                }
            }
        }
    }

    /** Bresenham line so fast strokes have no gaps. */
    private void line(Canvas canvas, int from, int to) {
        int x = from % canvas.width;
        int y = from / canvas.width;
        int endX = to % canvas.width;
        int endY = to / canvas.width;
        int dx = Math.abs(endX - x);
        int dy = -Math.abs(endY - y);
        int stepX = x < endX ? 1 : -1;
        int stepY = y < endY ? 1 : -1;
        int error = dx + dy;

        while (true) {
            applyTool(canvas, y * canvas.width + x);
            if (x == endX && y == endY) {
                return;
            }
            int doubled = 2 * error;
            if (doubled >= dy) {
                error += dy;
                x += stepX;
            }
            if (doubled <= dx) {
                error += dx;
                y += stepY;
            }
        }
    }

    /** Switches between 64 px and 128 px resolution (nearest-neighbour). */
    private void toggleHd() {
        Canvas canvas = canvas();
        boolean hd = canvas.width > 64;
        int newWidth = hd ? canvas.width / 2 : canvas.width * 2;
        int newHeight = hd ? canvas.height / 2 : canvas.height * 2;
        int[] resized = new int[newWidth * newHeight];
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                resized[y * newWidth + x] = canvas.pixels[y * canvas.height / newHeight * canvas.width + x * canvas.width / newWidth];
            }
        }
        canvas.width = newWidth;
        canvas.height = newHeight;
        canvas.pixels = resized;
        canvas.undo.clear();
        canvas.redo.clear();
        zoom = -1.0F;
        changed();
        updateLabels();
    }

    // --- 2D canvas geometry ---

    private void fitCanvas() {
        Canvas canvas = canvas();
        float fit = Math.min((float) (canvasWidth - 4) / canvas.width, (float) (areaHeight - 4) / canvas.height);
        zoom = fit >= 1.0F ? (float) Math.floor(fit) : fit;
        panX = 0.0F;
        panY = 0.0F;
    }

    private float originX() {
        return canvasX + canvasWidth / 2.0F - canvas().width * zoom / 2.0F + panX;
    }

    private float originY() {
        return areaY + areaHeight / 2.0F - canvas().height * zoom / 2.0F + panY;
    }

    private boolean inCanvasArea(double x, double y) {
        return x >= canvasX && x < canvasX + canvasWidth && y >= areaY && y < areaY + areaHeight;
    }

    private boolean inViewArea(double x, double y) {
        return x >= viewX && x < viewX + viewWidth && y >= areaY && y < areaY + areaHeight;
    }

    private int texelAt2d(double mouseX, double mouseY) {
        if (!inCanvasArea(mouseX, mouseY)) {
            return -1;
        }
        Canvas canvas = canvas();
        int x = (int) Math.floor((mouseX - originX()) / zoom);
        int y = (int) Math.floor((mouseY - originY()) / zoom);
        return x >= 0 && y >= 0 && x < canvas.width && y < canvas.height ? y * canvas.width + x : -1;
    }

    // --- 3D view ---

    private int pickAt3d(double mouseX, double mouseY) {
        if (!inViewArea(mouseX, mouseY)) {
            return 0;
        }
        return raster.pickAt((int) ((mouseX - viewX) * renderScale), (int) ((mouseY - areaY) * renderScale));
    }

    /** Texel of the current canvas under a pick value, or -1. */
    private int texelFromPick(int pick) {
        if (pick == 0) {
            return -1;
        }
        int target = ModelRaster.target(pick);
        boolean matches = mode == MODE_SKIN && target == ModelRaster.TARGET_SKIN
                || mode == MODE_CAPE && target == ModelRaster.TARGET_CAPE
                // Elytra painting edits the cape texture only when the elytra are not generated from it
                || mode == MODE_CAPE && target == ModelRaster.TARGET_ELYTRA && !elytraDerived;
        return matches ? ModelRaster.texel(pick) : -1;
    }

    private void renderView() {
        if (mode == MODE_CAPE && derivedElytra == null) {
            ModelRaster.Texture capeTexture = new ModelRaster.Texture(cape.width, cape.height, cape.pixels);
            ModelRaster.Texture derived = ElytraFromCape.derive(capeTexture);
            elytraDerived = derived != null;
            derivedElytra = derived != null ? derived : capeTexture;
        }

        scene.skin = new ModelRaster.Texture(skin.width, skin.height, skin.pixels);
        scene.slim = slim;
        scene.cape = new ModelRaster.Texture(cape.width, cape.height, cape.pixels);
        scene.elytra = derivedElytra;
        scene.back = mode == MODE_CAPE ? PreviewBack.get(preset) : ModelRaster.BACK_NONE;
        for (int part = 0; part < ModelRaster.PARTS; part++) {
            scene.visible[part][0] = mode == MODE_CAPE || visible[part][0];
            scene.visible[part][1] = mode == MODE_CAPE || visible[part][1];
        }
        scene.pickTransparentOverlay = mode == MODE_SKIN && paintOuter;
        scene.yaw = yaw;
        scene.pitch = pitch;
        scene.zoom = viewZoom;
        scene.highlight = hoverPick != 0 && texelFromPick(hoverPick) >= 0 ? hoverPick : 0;
        raster.render(scene, viewWidth * renderScale, areaHeight * renderScale);
        viewTexture.upload(raster.color(), raster.width(), raster.height());
    }

    // --- Input ---

    @Override
    protected boolean onMouseClicked(double x, double y, int button) {
        boolean leftButton = button == MOUSE_LEFT;
        boolean inPickerColumn = x >= svX && x < svX + svWidth;

        if (inPickerColumn && y >= svY && y < svY + svHeight) {
            drag = DRAG_SV;
            pickSv(x, y);
            return true;
        }
        if (inPickerColumn && y >= hueY && y < hueY + 7) {
            drag = DRAG_HUE;
            pickHue(x);
            return true;
        }
        int swatch = swatchAt(x, y);
        if (swatch >= 0) {
            setColor(PALETTE[swatch]);
            return true;
        }

        if (inCanvasArea(x, y)) {
            if (leftButton) {
                drag = DRAG_PAINT_2D;
                startStroke(texelAt2d(x, y));
            } else {
                drag = DRAG_PAN_2D;
            }
            return true;
        }
        if (inViewArea(x, y)) {
            if (leftButton) {
                drag = DRAG_PAINT_3D;
                startStroke(texelFromPick(pickAt3d(x, y)));
            } else {
                drag = DRAG_ROTATE_3D;
            }
            return true;
        }
        return false;
    }

    private void startStroke(int texel) {
        if (texel >= 0) {
            if (tool != PICKER) {
                pushUndo(canvas());
            }
            applyTool(canvas(), texel);
        }
        lastTexel = texel;
    }

    @Override
    protected boolean onMouseDragged(double x, double y, int button, double dragX, double dragY) {
        switch (drag) {
            case DRAG_PAINT_2D -> {
                int texel = texelAt2d(x, y);
                if (texel >= 0 && tool != FILL) {
                    if (lastTexel >= 0 && tool != PICKER) {
                        line(canvas(), lastTexel, texel);
                    } else {
                        applyTool(canvas(), texel);
                    }
                }
                lastTexel = texel;
            }
            case DRAG_PAN_2D -> {
                panX += (float) dragX;
                panY += (float) dragY;
            }
            case DRAG_PAINT_3D -> {
                int texel = texelFromPick(pickAt3d(x, y));
                if (texel >= 0 && texel != lastTexel && tool != FILL) {
                    applyTool(canvas(), texel);
                }
                lastTexel = texel;
            }
            case DRAG_ROTATE_3D -> {
                yaw += (float) dragX * 2.5F;
                pitch = Mth.clamp(pitch - (float) dragY * 2.5F, -80.0F, 80.0F);
                viewDirty = true;
            }
            case DRAG_SV -> pickSv(x, y);
            case DRAG_HUE -> pickHue(x);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (drag == DRAG_NONE) {
            return false;
        }
        drag = DRAG_NONE;
        lastTexel = -1;
        return true;
    }

    @Override
    protected boolean onMouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (inCanvasArea(mouseX, mouseY)) {
            zoomCanvas(mouseX, mouseY, scrollY > 0.0);
            return true;
        }
        if (inViewArea(mouseX, mouseY)) {
            viewZoom = Mth.clamp(viewZoom * (scrollY > 0.0 ? 1.15F : 1.0F / 1.15F), 0.5F, 4.0F);
            viewDirty = true;
            return true;
        }
        return false;
    }

    /** Zooms around the cursor; integer steps above 1x keep pixels sharp. */
    private void zoomCanvas(double mouseX, double mouseY, boolean in) {
        float oldZoom = zoom;
        float newZoom = Mth.clamp(in ? zoom * 1.25F : zoom / 1.25F, MIN_ZOOM, MAX_ZOOM);
        if (newZoom >= 1.0F) {
            newZoom = Math.round(newZoom);
        }
        if (newZoom == oldZoom) {
            newZoom = Mth.clamp(oldZoom + (in ? 1 : -1), MIN_ZOOM, MAX_ZOOM);
        }

        // Keep the texel under the cursor in place: remember it at the old zoom, then shift the pan
        // so that the same texel lands under the cursor at the new zoom
        float texelX = (float) (mouseX - originX()) / oldZoom;
        float texelY = (float) (mouseY - originY()) / oldZoom;
        zoom = newZoom;
        panX += (float) (mouseX - texelX * newZoom) - originX();
        panY += (float) (mouseY - texelY * newZoom) - originY();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        int pick = pickAt3d(mouseX, mouseY);
        if (pick != hoverPick) {
            hoverPick = pick;
            viewDirty = true;
        }
    }

    @Override
    protected boolean onKeyPressed(KeyPress event) {
        boolean ctrl = event.ctrl();
        boolean shift = event.shift();

        if (ctrl && event.key() == InputConstants.KEY_Z) {
            if (shift) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (ctrl && event.key() == InputConstants.KEY_Y) {
            redo();
            return true;
        }
        if (hexBox.isFocused() || ctrl) {
            return false;
        }

        int newTool = switch (event.key()) {
            case InputConstants.KEY_B -> PENCIL;
            case InputConstants.KEY_E -> ERASER;
            case InputConstants.KEY_I -> PICKER;
            case InputConstants.KEY_G -> FILL;
            default -> -1;
        };
        if (newTool < 0) {
            return false;
        }
        tool = newTool;
        updateLabels();
        return true;
    }

    private void pickSv(double mouseX, double mouseY) {
        saturation = Mth.clamp((float) (mouseX - svX) / svWidth, 0.0F, 1.0F);
        value = 1.0F - Mth.clamp((float) (mouseY - svY) / svHeight, 0.0F, 1.0F);
        setFromHsv();
    }

    private void pickHue(double mouseX) {
        hue = Mth.clamp((float) (mouseX - svX) / svWidth, 0.0F, 0.999F);
        setFromHsv();
    }

    private int swatchAt(double mouseX, double mouseY) {
        for (int i = 0; i < PALETTE.length; i++) {
            int x = svX + i % PALETTE_COLUMNS * (SWATCH + 1);
            int y = paletteY + i / PALETTE_COLUMNS * (SWATCH + 1);
            if (mouseX >= x && mouseX < x + SWATCH && mouseY >= y && mouseY < y + SWATCH) {
                return i;
            }
        }
        return -1;
    }

    // --- Saving ---

    private static void writePng(Canvas canvas, Path path) throws IOException {
        SkinImages.writePng(new ModelRaster.Texture(canvas.width, canvas.height, canvas.pixels), path);
    }

    /** Saves the edited textures into the config folder and points the preset at them. */
    private void apply(boolean applySkin, boolean applyCape) {
        SkinConfig config = SkinConfig.get();
        SkinConfig.Preset presetConfig = config.preset(preset);
        try {
            if (applySkin) {
                Path path = SkinConfig.dataDir().resolve("preset" + preset + "_skin.png");
                writePng(skin, path);
                presetConfig.skin = path.toAbsolutePath().toString();
                presetConfig.slim = slim;
            }
            if (applyCape) {
                Path path = SkinConfig.dataDir().resolve("preset" + preset + "_cape.png");
                writePng(cape, path);
                presetConfig.cape = path.toAbsolutePath().toString();
            }
            config.save();
            if (applySkin) {
                ClientSkins.loadSelf(SkinKinds.skin(preset));
            }
            if (applyCape) {
                ClientSkins.loadSelf(SkinKinds.cape(preset));
            }
            showMessage(Component.translatable("mogged.editor.applied", ClientSkins.presetName(preset)).withStyle(ChatFormatting.GREEN));
        } catch (IOException | RuntimeException e) {
            Mogged.LOGGER.warn("Could not save edited texture", e);
            showMessage(Component.translatable("mogged.error.read").withStyle(ChatFormatting.RED));
        }
    }

    private void export() {
        Canvas current = canvas();
        // Snapshot, the user may keep painting while the dialog is open
        Canvas snapshot = new Canvas(current.width, current.height, current.pixels.clone());
        String title = Component.translatable("mogged.editor.export").getString();
        String defaultName = mode == MODE_SKIN ? "skin.png" : "cape.png";

        if (!FileDialogs.isAvailable()) {
            // No native dialog on this version: save next to the mod's other files
            exportTo(snapshot, SkinConfig.dataDir().resolve("export").resolve(defaultName).toAbsolutePath().toString());
            return;
        }
        FileDialogs.savePng(title, defaultName, chosen -> exportTo(snapshot, chosen));
    }

    private void exportTo(Canvas snapshot, String chosen) {
        String file = chosen.toLowerCase(Locale.ROOT).endsWith(".png") ? chosen : chosen + ".png";
        try {
            writePng(snapshot, Path.of(file));
            showMessage(Component.translatable("mogged.editor.exported", file).withStyle(ChatFormatting.GREEN));
        } catch (IOException | RuntimeException e) {
            Mogged.LOGGER.warn("Could not export texture", e);
            showMessage(Component.translatable("mogged.error.read").withStyle(ChatFormatting.RED));
        }
    }

    private void showMessage(Component text) {
        message = text;
        messageUntil = System.currentTimeMillis() + MESSAGE_MILLIS;
    }

    /** Dropping a PNG onto the window loads it into the current canvas. */
    @Override
    public void onFilesDrop(List<Path> files) {
        if (files.isEmpty()) {
            return;
        }
        try {
            ModelRaster.Texture image = SkinImages.readPng(Files.readAllBytes(files.get(0)));
            if (image == null) {
                throw new IOException("Not an image");
            }
            int imageWidth = image.width();
            int imageHeight = image.height();
            boolean validSize = imageWidth % 64 == 0
                    && (mode == MODE_SKIN ? imageHeight == imageWidth : imageHeight * 2 == imageWidth);
            if (!validSize) {
                String key = mode == MODE_SKIN ? "mogged.error.skin_size" : "mogged.error.cape_size";
                showMessage(Component.translatable(key, imageWidth, imageHeight).withStyle(ChatFormatting.RED));
                return;
            }

            Canvas canvas = canvas();
            pushUndo(canvas);
            canvas.width = imageWidth;
            canvas.height = imageHeight;
            canvas.pixels = image.argb().clone();
            zoom = -1.0F;
            changed();
            updateLabels();
        } catch (Exception e) {
            showMessage(Component.translatable("mogged.error.not_png").withStyle(ChatFormatting.RED));
        }
    }

    // --- Rendering ---

    @Override
    protected void drawScreen(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        if (zoom <= 0.0F) {
            fitCanvas();
        }
        drawCanvas(graphics, mouseX, mouseY);
        drawView(graphics);
        drawColorPicker(graphics);

        if (mode == MODE_SKIN) {
            int y = partButtons[0].getY();
            graphics.text(font, Component.translatable("mogged.editor.layer.base"), canvasX, y + 6, LABEL_COLOR);
            graphics.text(font, Component.translatable("mogged.editor.layer.outer"), canvasX, y + 28, LABEL_COLOR);
        }

        Canvas canvas = canvas();
        graphics.text(font, title, viewX + 4, areaY + 4, 0xFFFFFFFF);
        graphics.text(font, canvas.width + "x" + canvas.height, canvasX + 4, areaY + 4, LABEL_COLOR);

        if (message != null && System.currentTimeMillis() < messageUntil) {
            String text = font.plainSubstrByWidth(message.getString(), viewWidth - 8);
            int color = message.getStyle().getColor() != null ? 0xFF000000 | message.getStyle().getColor().getValue() : 0xFFFFFFFF;
            graphics.text(font, text, viewX + 4, areaY + areaHeight - 12, color);
        }
    }

    private void drawCanvas(Gfx graphics, int mouseX, int mouseY) {
        Canvas canvas = canvas();
        graphics.fill(canvasX - 1, areaY - 1, canvasX + canvasWidth + 1, areaY + areaHeight + 1, FRAME_COLOR);
        graphics.enableScissor(canvasX, areaY, canvasX + canvasWidth, areaY + areaHeight);
        graphics.fill(canvasX, areaY, canvasX + canvasWidth, areaY + areaHeight, 0xFF1A1A1A);

        float originX = originX();
        float originY = originY();
        int left = Math.round(originX);
        int top = Math.round(originY);
        int right = Math.round(originX + canvas.width * zoom);
        int bottom = Math.round(originY + canvas.height * zoom);
        int clipLeft = Math.max(left, canvasX);
        int clipTop = Math.max(top, areaY);
        int clipRight = Math.min(right, canvasX + canvasWidth);
        int clipBottom = Math.min(bottom, areaY + areaHeight);

        // Checkerboard behind transparent pixels
        for (int y = clipTop; y < clipBottom; y += CHECKER) {
            for (int x = clipLeft; x < clipRight; x += CHECKER) {
                boolean light = ((x - left) / CHECKER + (y - top) / CHECKER) % 2 == 0;
                graphics.fill(x, y, Math.min(x + CHECKER, clipRight), Math.min(y + CHECKER, clipBottom), light ? 0xFF3A3A3A : 0xFF2C2C2C);
            }
        }

        if (canvasDirty) {
            canvasDirty = false;
            canvasTexture.upload(canvas.pixels, canvas.width, canvas.height);
        }
        graphics.blit(canvasTexture, left, top, right, bottom);

        // Skin box outlines; hidden (locked) parts are dimmed
        float scale = canvas.width / 64.0F;
        if (mode == MODE_SKIN) {
            for (int i = 0; i < SkinLayout.BOXES.length; i++) {
                int[] bounds = SkinLayout.bounds(i);
                int x1 = Math.round(originX + bounds[0] * scale * zoom);
                int y1 = Math.round(originY + bounds[1] * scale * zoom);
                int x2 = Math.round(originX + (bounds[0] + bounds[2]) * scale * zoom);
                int y2 = Math.round(originY + (bounds[1] + bounds[3]) * scale * zoom);
                if (!visible[i / 2][i % 2]) {
                    graphics.fill(x1, y1, x2, y2, 0xB0101010);
                }
                graphics.outline(x1, y1, x2 - x1, y2 - y1, 0x40FFFFFF);
            }
        }

        if (zoom >= GRID_ZOOM) {
            for (int i = 0; i <= canvas.width; i++) {
                int x = Math.round(originX + i * zoom);
                if (x >= clipLeft && x <= clipRight) {
                    graphics.fill(x, clipTop, x + 1, clipBottom, 0x18FFFFFF);
                }
            }
            for (int i = 0; i <= canvas.height; i++) {
                int y = Math.round(originY + i * zoom);
                if (y >= clipTop && y <= clipBottom) {
                    graphics.fill(clipLeft, y, clipRight, y + 1, 0x18FFFFFF);
                }
            }
        }

        // Hovered texel, from either the canvas or the 3D view
        int hovered = texelAt2d(mouseX, mouseY);
        if (hovered < 0 && hoverPick != 0) {
            hovered = texelFromPick(hoverPick);
        }
        if (hovered >= 0 && hovered < canvas.pixels.length) {
            int x = Math.round(originX + hovered % canvas.width * zoom);
            int y = Math.round(originY + hovered / canvas.width * zoom);
            int size = Math.max(1, Math.round(zoom));
            graphics.outline(x - 1, y - 1, size + 2, size + 2, 0xFFFFFFFF);
        }
        graphics.disableScissor();
    }

    private void drawView(Gfx graphics) {
        if (viewDirty) {
            viewDirty = false;
            renderView();
        }
        graphics.fill(viewX - 1, areaY - 1, viewX + viewWidth + 1, areaY + areaHeight + 1, FRAME_COLOR);
        graphics.fill(viewX, areaY, viewX + viewWidth, areaY + areaHeight, 0xFF20242A);
        graphics.blit(viewTexture, viewX, areaY, viewX + viewWidth, areaY + areaHeight);
    }

    private void drawColorPicker(Gfx graphics) {
        // Saturation/value square as a coarse grid of cells
        int columns = 23;
        int rows = 12;
        for (int column = 0; column < columns; column++) {
            for (int row = 0; row < rows; row++) {
                int x1 = svX + column * svWidth / columns;
                int x2 = svX + (column + 1) * svWidth / columns;
                int y1 = svY + row * svHeight / rows;
                int y2 = svY + (row + 1) * svHeight / rows;
                graphics.fill(x1, y1, x2, y2, hsv(hue, (column + 0.5F) / columns, 1.0F - (row + 0.5F) / rows));
            }
        }
        int markerX = svX + Math.round(saturation * svWidth);
        int markerY = svY + Math.round((1.0F - value) * svHeight);
        graphics.outline(markerX - 2, markerY - 2, 5, 5, 0xFFFFFFFF);

        // Hue strip
        int hueSteps = 46;
        for (int i = 0; i < hueSteps; i++) {
            graphics.fill(svX + i * svWidth / hueSteps, hueY, svX + (i + 1) * svWidth / hueSteps, hueY + 7, hsv(i / (float) hueSteps, 1.0F, 1.0F));
        }
        int hueX = svX + Math.round(hue * svWidth);
        graphics.fill(hueX - 1, hueY - 1, hueX + 1, hueY + 8, 0xFFFFFFFF);

        // Current color and palette
        graphics.fill(svX, swatchY, svX + 16, swatchY + 16, 0xFF808080);
        graphics.fill(svX + 1, swatchY + 1, svX + 15, swatchY + 15, color);
        for (int i = 0; i < PALETTE.length; i++) {
            int x = svX + i % PALETTE_COLUMNS * (SWATCH + 1);
            int y = paletteY + i / PALETTE_COLUMNS * (SWATCH + 1);
            graphics.fill(x, y, x + SWATCH, y + SWATCH, FRAME_COLOR);
            graphics.fill(x + 1, y + 1, x + SWATCH - 1, y + SWATCH - 1, PALETTE[i]);
        }
    }

    @Override
    public void removed() {
        canvasTexture.release();
        viewTexture.release();
    }

    @Override
    public void onClose() {
        open(parent);
    }

    private static final class Canvas {
        int width;
        int height;
        int[] pixels;
        final ArrayDeque<int[]> undo = new ArrayDeque<>();
        final ArrayDeque<int[]> redo = new ArrayDeque<>();

        Canvas(int width, int height, int[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }
}
