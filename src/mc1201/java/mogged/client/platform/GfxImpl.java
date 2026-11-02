package mogged.client.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import mogged.client.gui.Gfx;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;

/** {@link Gfx} on top of GuiGraphics (1.20.1). */
final class GfxImpl implements Gfx {
    private final GuiGraphics graphics;

    GfxImpl(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, color);
    }

    @Override
    public void outline(int x, int y, int width, int height, int color) {
        graphics.renderOutline(x, y, width, height, color);
    }

    @Override
    public void text(Font font, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color);
    }

    @Override
    public void text(Font font, String text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color);
    }

    @Override
    public void centeredText(Font font, Component text, int centerX, int y, int color) {
        graphics.drawCenteredString(font, text, centerX, y, color);
    }

    @Override
    public void blit(GpuTexture texture, int x1, int y1, int x2, int y2) {
        if (texture.id() == null) {
            return;
        }
        int width = texture.width();
        int height = texture.height();
        // Our textures have alpha (previews are transparent around the model)
        RenderSystem.enableBlend();
        graphics.blit(texture.id(), x1, y1, x2 - x1, y2 - y1, 0.0F, 0.0F, width, height, width, height);
        RenderSystem.disableBlend();
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
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        PlayerFaceRenderer.draw(graphics, ClientPlatform.id(ClientPlatform.ownSkin().body()), x, y, size);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
