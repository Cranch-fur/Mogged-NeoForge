package mogged.client.platform;

import mogged.client.gui.Gfx;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/** {@link Gfx} on top of 26.x GuiGraphicsExtractor. */
final class GfxImpl implements Gfx {
    private final GuiGraphicsExtractor graphics;

    GfxImpl(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, color);
    }

    @Override
    public void outline(int x, int y, int width, int height, int color) {
        graphics.outline(x, y, width, height, color);
    }

    @Override
    public void text(Font font, Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
    }

    @Override
    public void text(Font font, String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
    }

    @Override
    public void centeredText(Font font, Component text, int centerX, int y, int color) {
        graphics.centeredText(font, text, centerX, y, color);
    }

    @Override
    public void blit(GpuTexture texture, int x1, int y1, int x2, int y2) {
        if (texture.id() != null) {
            graphics.blit(texture.id(), x1, y1, x2, y2, 0.0F, 1.0F, 0.0F, 1.0F);
        }
    }

    @Override
    public void enableScissor(int x1, int y1, int x2, int y2) {
        graphics.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void disableScissor() {
        graphics.disableScissor();
    }

    @Override
    public void ownFace(int x, int y, int size, float alpha) {
        PlayerFaceExtractor.extractRenderState(graphics, ClientPlatform.ownPlayerSkin(), x, y, size, ARGB.white(alpha));
    }
}
