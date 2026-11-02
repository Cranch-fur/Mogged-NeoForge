package mogged.platform;

import mogged.Mogged;
import net.minecraft.resources.ResourceLocation;

/** Resource ids (1.20.1: ResourceLocation constructor). */
public final class Ids {
    private Ids() {
    }

    public static ResourceLocation mod(String path) {
        return new ResourceLocation(Mogged.MOD_ID, path);
    }
}
