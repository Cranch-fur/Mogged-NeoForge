package mogged.client.compat;

/**
 * Flashback (the replay mod) exists only for Fabric, so on NeoForge there is never a replay to record into
 * or to play back. The class keeps the Fabric version's API so the shared client code stays the same.
 */
public final class FlashbackCompat {
    private FlashbackCompat() {
    }

    public static boolean isInReplay() {
        return false;
    }

    public static void record(Object message) {
    }
}
