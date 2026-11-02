package mogged.client.platform;

import com.mojang.blaze3d.platform.InputConstants;
import mogged.client.gui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base of our screens: turns this version's input events and drawing class into the
 * version-neutral hooks below, so the screens themselves are shared (1.21.1).
 */
public abstract class BaseScreen extends Screen {
    public static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;

    protected BaseScreen(Component title) {
        super(title);
    }

    public static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    // --- Hooks for subclasses ---

    /** Drawn on top of the widgets. */
    protected void drawScreen(Gfx gfx, int mouseX, int mouseY, float partialTick) {
    }

    protected boolean onKeyPressed(KeyPress key) {
        return false;
    }

    /** Called only when no widget took the click. */
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    // --- Version glue ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawScreen(new GfxImpl(graphics), mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return onKeyPressed(new KeyPress(keyCode, scanCode, modifiers)) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button) || onMouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return onMouseDragged(mouseX, mouseY, button, dragX, dragY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return onMouseReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return onMouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
