package mogged.client.platform;

import com.mojang.blaze3d.platform.InputConstants;

/** A key press as our screens see it (1.20.1 / 1.21.1: key code, scan code and modifier bits). */
public final class KeyPress {
    // GLFW_MOD_SHIFT / GLFW_MOD_CONTROL
    private static final int MOD_SHIFT = 0x1;
    private static final int MOD_CONTROL = 0x2;

    private final int key;
    private final int scanCode;
    private final int modifiers;

    KeyPress(int key, int scanCode, int modifiers) {
        this.key = key;
        this.scanCode = scanCode;
        this.modifiers = modifiers;
    }

    /** Key code, compare with InputConstants.KEY_*. */
    public int key() {
        return key;
    }

    public boolean ctrl() {
        return (modifiers & MOD_CONTROL) != 0;
    }

    public boolean shift() {
        return (modifiers & MOD_SHIFT) != 0;
    }

    public boolean isEscape() {
        return key == InputConstants.KEY_ESCAPE;
    }

    /** For rebinding a KeyMapping to this key. */
    public InputConstants.Key toInputKey() {
        return InputConstants.getKey(key, scanCode);
    }
}
