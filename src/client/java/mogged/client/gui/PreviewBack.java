package mogged.client.gui;

import mogged.client.render.ModelRaster;
import mogged.net.SkinKinds;
import net.minecraft.network.chat.Component;

/**
 * What the previews show on the player's back (cape or elytra), per preset.
 * Shared by all screens so the choice stays the same when moving between them.
 */
final class PreviewBack {
    private static final int[] BACK = new int[SkinKinds.MAX_PRESET + 1];

    static {
        java.util.Arrays.fill(BACK, ModelRaster.BACK_CAPE);
    }

    private PreviewBack() {
    }

    static int get(int preset) {
        return BACK[preset];
    }

    static void toggle(int preset) {
        BACK[preset] = BACK[preset] == ModelRaster.BACK_ELYTRA ? ModelRaster.BACK_CAPE : ModelRaster.BACK_ELYTRA;
    }

    static Component label(int preset) {
        return Component.translatable(BACK[preset] == ModelRaster.BACK_ELYTRA ? "mogged.view.elytra" : "mogged.view.cape");
    }
}
