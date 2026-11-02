package mogged.platform;

import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/** The game's config folder. */
public final class ConfigDir {
    private ConfigDir() {
    }

    public static Path get() {
        return FMLPaths.CONFIGDIR.get();
    }
}
