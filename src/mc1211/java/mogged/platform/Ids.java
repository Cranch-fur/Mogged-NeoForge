package mogged.platform;

import mogged.Mogged;
import net.minecraft.resources.ResourceLocation;

/** Resource ids (1.21.1: ResourceLocation). */
public final class Ids {
    private Ids() {
    }

    public static ResourceLocation mod(String path) {
        return ResourceLocation.fromNamespaceAndPath(Mogged.MOD_ID, path);
    }
}
