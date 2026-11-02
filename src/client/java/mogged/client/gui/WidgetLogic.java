package mogged.client.gui;

import mogged.client.platform.WidgetHost;

/**
 * Behaviour of a custom widget. The platform's {@link WidgetHost} is the actual vanilla widget
 * and forwards drawing and input here, so the widget code itself is shared by all versions.
 */
public interface WidgetLogic {
    void draw(Gfx gfx, WidgetHost host, int mouseX, int mouseY);

    /** Click or keyboard activation. */
    default void onPress() {
    }

    /** Mouse click with its position (called before {@link #onPress()}). */
    default void onClick(double mouseX, double mouseY) {
    }

    default void onDrag(double dragX, double dragY) {
    }
}
