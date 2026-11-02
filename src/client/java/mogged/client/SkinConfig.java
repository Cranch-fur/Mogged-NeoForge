package mogged.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import mogged.Mogged;
import mogged.net.SkinKinds;
import mogged.platform.ConfigDir;

/** User settings, stored in config/mogged.json. */
public final class SkinConfig {
    public static final int MIN_DURATION_MS = 200;
    public static final int MAX_DURATION_MS = 5000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static SkinConfig instance;

    public Preset preset1 = new Preset();
    public Preset preset2 = new Preset();
    public int activePreset = SkinKinds.DEFAULT_PRESET;
    // Which presets the switch key cycles through: Default, 1, 2
    public boolean[] cycle = {true, true, true};
    public int durationMs = 1200;
    public boolean effects = true;

    public static Path path() {
        return ConfigDir.get().resolve(Mogged.MOD_ID + ".json");
    }

    /** Folder for files created by the mod (e.g. editor output). */
    public static Path dataDir() {
        return ConfigDir.get().resolve(Mogged.MOD_ID);
    }

    public static SkinConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static SkinConfig load() {
        Path path = path();
        if (Files.isRegularFile(path)) {
            try {
                SkinConfig config = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), SkinConfig.class);
                if (config != null) {
                    config.sanitize();
                    return config;
                }
            } catch (Exception e) {
                Mogged.LOGGER.warn("Failed to read {}", path, e);
            }
        }
        return new SkinConfig();
    }

    private void sanitize() {
        if (preset1 == null) {
            preset1 = new Preset();
        }
        if (preset2 == null) {
            preset2 = new Preset();
        }
        preset1.sanitize();
        preset2.sanitize();
        if (cycle == null || cycle.length != SkinKinds.MAX_PRESET + 1) {
            cycle = new boolean[]{true, true, true};
        }
        activePreset = Math.max(SkinKinds.DEFAULT_PRESET, Math.min(SkinKinds.MAX_PRESET, activePreset));
        durationMs = Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, durationMs));
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mogged.LOGGER.warn("Failed to save {}", path(), e);
        }
    }

    public Preset preset(int preset) {
        return preset == 2 ? preset2 : preset1;
    }

    /** Source that is actually loaded for a slot (elytra slot is empty unless enabled). */
    public String source(int kind) {
        Preset preset = preset(SkinKinds.presetOf(kind));
        return switch (kind / SkinKinds.PRESETS) {
            case 0 -> preset.skin;
            case 1 -> preset.cape;
            default -> preset.separateElytra ? preset.elytra : "";
        };
    }

    /** Source as typed by the user. */
    public String rawSource(int kind) {
        Preset preset = preset(SkinKinds.presetOf(kind));
        return switch (kind / SkinKinds.PRESETS) {
            case 0 -> preset.skin;
            case 1 -> preset.cape;
            default -> preset.elytra;
        };
    }

    public void setSource(int kind, String source) {
        Preset preset = preset(SkinKinds.presetOf(kind));
        switch (kind / SkinKinds.PRESETS) {
            case 0 -> preset.skin = source;
            case 1 -> preset.cape = source;
            default -> preset.elytra = source;
        }
    }

    public boolean slim(int kind) {
        return SkinKinds.isSkin(kind) && preset(SkinKinds.presetOf(kind)).slim;
    }

    public static final class Preset {
        public String skin = "";
        public boolean slim = false;
        public String cape = "";
        public boolean separateElytra = false;
        public String elytra = "";

        void sanitize() {
            if (skin == null) {
                skin = "";
            }
            if (cape == null) {
                cape = "";
            }
            if (elytra == null) {
                elytra = "";
            }
        }
    }
}
