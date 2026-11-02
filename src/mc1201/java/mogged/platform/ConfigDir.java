package mogged.platform;

import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;

/** The game's config folder (1.20.1: NeoForge 47.1 keeps Forge's packages). */
public final class ConfigDir {
    private ConfigDir() {
    }

    public static Path get() {
        return FMLPaths.CONFIGDIR.get();
    }
}
