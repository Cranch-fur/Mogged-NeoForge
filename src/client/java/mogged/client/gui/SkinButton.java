package mogged.client.gui;

import mogged.client.platform.BaseScreen;
import mogged.client.platform.WidgetHost;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 20x20 button with the player's face that opens the Mogged screen. */
public final class SkinButton implements WidgetLogic {
    public static final int SIZE = 20;
    private static final int FACE_SIZE = 12;

    private final Screen parent;

    private SkinButton(Screen parent) {
        this.parent = parent;
    }

    public static WidgetHost create(Screen parent, int x, int y) {
        Component label = Component.translatable("mogged.button.open");
        WidgetHost host = new WidgetHost(x, y, SIZE, SIZE, label, new SkinButton(parent), true, true);
        host.setTooltip(Tooltip.create(label));
        return host;
    }

    @Override
    public void onPress() {
        BaseScreen.open(new MoggedScreen(parent));
    }

    @Override
    public void draw(Gfx gfx, WidgetHost host, int mouseX, int mouseY) {
        // The title screen fades its widgets in; the face must follow, or it pops up before the rest of the UI
        if (host.alpha() <= 0.0F) {
            return;
        }
        int offset = (SIZE - FACE_SIZE) / 2;
        gfx.ownFace(host.getX() + offset, host.getY() + offset, FACE_SIZE, host.alpha());
    }
}
