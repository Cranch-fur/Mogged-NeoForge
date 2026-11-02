package mogged.client.gui;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import mogged.client.platform.GpuTexture;
import mogged.client.platform.WidgetHost;
import mogged.client.render.ModelRaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Rotatable software-rendered player preview with an optional toggle in the bottom-left corner. */
public final class ModelPreview implements WidgetLogic {
    private static final float DRAG_SPEED = 2.5F;
    private static final float MAX_PITCH = 60.0F;
    private static final int CORNER_HEIGHT = 12;
    private static final int HINT_COLOR = 0xFFB0B0B0;

    private final Consumer<ModelRaster.Scene> filler;
    private final ModelRaster raster = new ModelRaster();
    private final GpuTexture texture = new GpuTexture("gui/preview");
    private final ModelRaster.Scene scene = new ModelRaster.Scene();
    private final WidgetHost host;
    private float yaw = 25.0F;
    private float pitch = -8.0F;
    private int lastKey;
    private boolean dirty = true;
    private Supplier<Component> cornerLabel;
    private Runnable cornerAction;

    public ModelPreview(int x, int y, int width, int height, Consumer<ModelRaster.Scene> filler) {
        this.filler = filler;
        this.host = new WidgetHost(x, y, width, height, Component.empty(), this, false, false);
    }

    /** The vanilla widget to add to the screen. */
    public WidgetHost host() {
        return host;
    }

    public ModelPreview corner(Supplier<Component> label, Runnable action) {
        this.cornerLabel = label;
        this.cornerAction = action;
        return this;
    }

    private int cornerWidth() {
        return cornerLabel == null ? 0 : Minecraft.getInstance().font.width(cornerLabel.get()) + 8;
    }

    private boolean inCorner(double mouseX, double mouseY) {
        int bottom = host.getY() + host.getHeight();
        return cornerLabel != null
                && mouseX >= host.getX() + 2 && mouseX < host.getX() + 2 + cornerWidth()
                && mouseY >= bottom - CORNER_HEIGHT - 2 && mouseY < bottom - 2;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (inCorner(mouseX, mouseY)) {
            cornerAction.run();
            dirty = true;
        }
    }

    @Override
    public void onDrag(double dragX, double dragY) {
        yaw += (float) dragX * DRAG_SPEED;
        pitch = Mth.clamp(pitch - (float) dragY * DRAG_SPEED, -MAX_PITCH, MAX_PITCH);
    }

    @Override
    public void draw(Gfx gfx, WidgetHost widget, int mouseX, int mouseY) {
        int x = widget.getX();
        int y = widget.getY();
        int width = widget.getWidth();
        int height = widget.getHeight();
        filler.accept(scene);
        scene.yaw = yaw;
        scene.pitch = pitch;

        // Render at real screen resolution for crisp pixels
        int scale = Mth.clamp((int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()), 1, 4);
        int pixelWidth = width * scale;
        int pixelHeight = height * scale;

        // Re-raster only when something visible changed
        int key = Objects.hash(
                identity(scene.skin), identity(scene.cape), identity(scene.elytra),
                scene.slim, scene.back, yaw, pitch, pixelWidth, pixelHeight, scene.zoom);
        if (dirty || key != lastKey) {
            dirty = false;
            lastKey = key;
            raster.render(scene, pixelWidth, pixelHeight);
            texture.upload(raster.color(), pixelWidth, pixelHeight);
        }
        gfx.blit(texture, x, y, x + width, y + height);

        Font font = Minecraft.getInstance().font;
        if (widget.isHovered()) {
            String hint = font.plainSubstrByWidth(Component.translatable("mogged.hint.rotate").getString(), width - 4);
            gfx.text(font, hint, x + (width - font.width(hint)) / 2, y + 2, HINT_COLOR);
        }

        if (cornerLabel != null) {
            int cornerX = x + 2;
            int cornerY = y + height - CORNER_HEIGHT - 2;
            int cornerWidth = cornerWidth();
            boolean hovered = inCorner(mouseX, mouseY);
            gfx.fill(cornerX, cornerY, cornerX + cornerWidth, cornerY + CORNER_HEIGHT, hovered ? 0xC0505050 : 0xA0202020);
            gfx.outline(cornerX, cornerY, cornerWidth, CORNER_HEIGHT, hovered ? 0xFFFFFFFF : 0xFF808080);
            gfx.text(font, cornerLabel.get(), cornerX + 4, cornerY + 2, 0xFFFFFFFF);
        }
    }

    private static int identity(ModelRaster.Texture texture) {
        return texture == null ? 0 : System.identityHashCode(texture.argb());
    }

    public void release() {
        texture.release();
    }
}
