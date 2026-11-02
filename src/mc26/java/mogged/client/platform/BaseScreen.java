package mogged.client.platform;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import mogged.client.gui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Base of our screens: turns this version's input events and drawing class into the
 * version-neutral hooks below, so the screens themselves are shared (26.x).
 */
public abstract class BaseScreen extends Screen {
    public static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;

    // 26.2 moved setScreen from Minecraft to Gui, so it is looked up once at runtime
    private static final MethodHandle GUI_SET_SCREEN = findSetScreen(Gui.class);
    private static final MethodHandle MINECRAFT_SET_SCREEN = GUI_SET_SCREEN == null ? findSetScreen(Minecraft.class) : null;

    protected BaseScreen(Component title) {
        super(title);
    }

    private static MethodHandle findSetScreen(Class<?> owner) {
        try {
            return MethodHandles.publicLookup().findVirtual(owner, "setScreen", MethodType.methodType(void.class, Screen.class));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static void open(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (GUI_SET_SCREEN != null) {
                GUI_SET_SCREEN.invoke(minecraft.gui, screen);
            } else {
                MINECRAFT_SET_SCREEN.invoke(minecraft, screen);
            }
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot open screen " + screen, e);
        }
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawScreen(new GfxImpl(graphics), mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return onKeyPressed(new KeyPress(event)) || super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick) || onMouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return onMouseDragged(event.x(), event.y(), event.button(), dragX, dragY) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return onMouseReleased(event.x(), event.y(), event.button()) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return onMouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
