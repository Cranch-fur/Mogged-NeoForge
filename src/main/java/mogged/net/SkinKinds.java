package mogged.net;

/**
 * Texture slot layout shared by client and server.
 * Slots: 0-1 skins, 2-3 capes, 4-5 elytra (one of each per custom preset).
 * Preset 0 is the player's own account skin and has no slots.
 */
public final class SkinKinds {
    public static final int COUNT = 6;
    public static final int PRESETS = 2;
    public static final int DEFAULT_PRESET = 0;
    public static final int MAX_PRESET = PRESETS;

    private SkinKinds() {
    }

    public static int skin(int preset) {
        return preset - 1;
    }

    public static int cape(int preset) {
        return PRESETS + preset - 1;
    }

    public static int elytra(int preset) {
        return 2 * PRESETS + preset - 1;
    }

    public static boolean isSkin(int kind) {
        return kind < PRESETS;
    }

    public static boolean isCape(int kind) {
        return kind / PRESETS == 1;
    }

    public static int presetOf(int kind) {
        return kind % PRESETS + 1;
    }

    public static boolean isValid(int kind) {
        return kind >= 0 && kind < COUNT;
    }
}
