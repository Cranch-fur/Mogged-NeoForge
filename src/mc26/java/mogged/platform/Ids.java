package mogged.platform;

import mogged.Mogged;
import net.minecraft.resources.Identifier;

/** Resource ids (26.x: Identifier). */
public final class Ids {
    private Ids() {
    }

    public static Identifier mod(String path) {
        return Identifier.fromNamespaceAndPath(Mogged.MOD_ID, path);
    }
}
