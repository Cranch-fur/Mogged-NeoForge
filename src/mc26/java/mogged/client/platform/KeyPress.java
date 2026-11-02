package mogged.client.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

/** A key press as our screens see it (26.x KeyEvent). */
public final class KeyPress {
    private final KeyEvent event;

    KeyPress(KeyEvent event) {
        this.event = event;
    }

    /** Key code, compare with InputConstants.KEY_* (values differ between versions). */
    public int key() {
        return event.key();
    }

    public boolean ctrl() {
        return event.hasControlDown();
    }

    public boolean shift() {
        return event.hasShiftDown();
    }

    public boolean isEscape() {
        return event.isEscape();
    }

    /** For rebinding a KeyMapping to this key. */
    public InputConstants.Key toInputKey() {
        return InputConstants.getKey(event);
    }
}
