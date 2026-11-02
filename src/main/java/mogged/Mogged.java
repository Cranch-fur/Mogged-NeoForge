package mogged;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared constants. The NeoForge entry point is mogged.neoforge.MoggedNeoForge (one per Minecraft version family). */
public final class Mogged {
    public static final String MOD_ID = "mogged";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Mogged() {
    }
}
