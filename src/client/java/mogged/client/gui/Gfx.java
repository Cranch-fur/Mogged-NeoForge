package mogged.client.gui;

import mogged.client.platform.GpuTexture;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * The drawing calls our screens and widgets need. Each Minecraft version has its own
 * GUI drawing class (GuiGraphics, GuiGraphicsExtractor...), wrapped by the platform layer.
 */
public interface Gfx {
    void fill(int x1, int y1, int x2, int y2, int color);

    void outline(int x, int y, int width, int height, int color);

    void text(Font font, Component text, int x, int y, int color);

    void text(Font font, String text, int x, int y, int color);

    void centeredText(Font font, Component text, int centerX, int y, int color);

    /** Draws the whole texture stretched over the rectangle. */
    void blit(GpuTexture texture, int x1, int y1, int x2, int y2);

    void enableScissor(int x1, int y1, int x2, int y2);

    void disableScissor();

    /** The player's own account face (without presets), e.g. for the menu button. */
    void ownFace(int x, int y, int size, float alpha);
}
