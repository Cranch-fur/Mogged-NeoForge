package mogged.client.gui;

import java.util.function.Consumer;
import mogged.client.platform.WidgetHost;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Small 12x12 checkbox with a pixel check mark. */
public final class CheckToggle implements WidgetLogic {
    private static final int SIZE = 12;
    private static final int BORDER = 0xFFA0A0A0;
    private static final int BORDER_HOVER = 0xFFFFFFFF;
    private static final int BACKGROUND = 0xFF202020;
    private static final int CHECK = 0xFF55FF55;

    private final Consumer<Boolean> onChange;
    private boolean checked;

    private CheckToggle(boolean checked, Consumer<Boolean> onChange) {
        this.checked = checked;
        this.onChange = onChange;
    }

    public static WidgetHost create(int x, int y, boolean checked, Component tooltip, Consumer<Boolean> onChange) {
        WidgetHost host = new WidgetHost(x, y, SIZE, SIZE, tooltip, new CheckToggle(checked, onChange), false, true);
        host.setTooltip(Tooltip.create(tooltip));
        return host;
    }

    @Override
    public void onPress() {
        checked = !checked;
        onChange.accept(checked);
    }

    @Override
    public void draw(Gfx gfx, WidgetHost host, int mouseX, int mouseY) {
        int x = host.getX();
        int y = host.getY();
        gfx.fill(x, y, x + SIZE, y + SIZE, host.isHoveredOrFocused() ? BORDER_HOVER : BORDER);
        gfx.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, BACKGROUND);
        if (!checked) {
            return;
        }
        // Check mark drawn as overlapping 2x2 squares
        gfx.fill(x + 3, y + 6, x + 5, y + 8, CHECK);
        gfx.fill(x + 4, y + 7, x + 6, y + 9, CHECK);
        gfx.fill(x + 5, y + 6, x + 7, y + 8, CHECK);
        gfx.fill(x + 6, y + 5, x + 8, y + 7, CHECK);
        gfx.fill(x + 7, y + 4, x + 9, y + 6, CHECK);
        gfx.fill(x + 8, y + 3, x + 10, y + 5, CHECK);
    }
}
