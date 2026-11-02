package mogged.client;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import mogged.Mogged;
import mogged.client.gui.SkinButton;
import mogged.client.platform.ClientNetworking;
import mogged.client.platform.ClientPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The client side of the mod, without any loader API: the NeoForge glue of each Minecraft version family
 * (mogged.neoforge.client.NeoForgeClient) calls these methods from NeoForge's events.
 */
public final class MoggedClient {
    private static final int ICON_BUTTON_SIZE = 20;
    private static final int GAP = 4;

    // Before 26.2 the title screen has its icon buttons on both sides of Options/Quit Game
    // and there is no free spot for one more; the mod is then opened from the pause menu only
    private static final boolean HAS_TITLE_BUTTON = minecraftAtLeast(26, 2);

    private static KeyMapping toggleKey;
    private static boolean started;

    private MoggedClient() {
    }

    public static KeyMapping toggleKey() {
        return toggleKey;
    }

    /** Mod construction on the client: the key (registered by the caller) and the network receivers. */
    public static KeyMapping init() {
        toggleKey = ClientPlatform.createToggleKey();
        ClientNetworking.registerReceivers(ClientSkins::onChunk, ClientSkins::onState, ClientSkins::onRemove);
        return toggleKey;
    }

    /** End of every client tick. */
    public static void onEndTick() {
        // Fabric's CLIENT_STARTED: the first tick happens once the game has fully started
        if (!started) {
            started = true;
            ClientSkins.reloadAllFromConfig();
        }
        if (toggleKey != null) {
            while (toggleKey.consumeClick()) {
                ClientSkins.toggle();
            }
        }
        ClientSkins.tick();
    }

    public static void onJoin() {
        ClientSkins.onJoin();
    }

    public static void onDisconnect() {
        ClientSkins.onDisconnect();
    }

    /**
     * A screen finished building its widgets (run after other mods). {@code listeners} are the screen's
     * widgets, {@code add} adds one to the screen.
     */
    public static void onScreenInit(Screen screen, List<GuiEventListener> listeners, Consumer<AbstractWidget> add) {
        List<AbstractWidget> widgets = new ArrayList<>();
        for (GuiEventListener listener : listeners) {
            if (listener instanceof AbstractWidget widget) {
                widgets.add(widget);
            }
        }
        if (screen instanceof TitleScreen && HAS_TITLE_BUTTON) {
            addTitleScreenButton(screen, widgets, add);
        } else if (screen instanceof PauseScreen) {
            addPauseScreenButton(screen, widgets, add);
        }
    }

    /** Puts the button at the end of the line with the small icon buttons (language, accessibility...) and recenters that line. */
    private static void addTitleScreenButton(Screen screen, List<AbstractWidget> widgets, Consumer<AbstractWidget> add) {
        Map<Integer, List<AbstractWidget>> iconRows = new HashMap<>();
        for (AbstractWidget widget : widgets) {
            if (widget.visible && widget.getWidth() == ICON_BUTTON_SIZE && widget.getHeight() == ICON_BUTTON_SIZE) {
                iconRows.computeIfAbsent(widget.getY(), y -> new ArrayList<>()).add(widget);
            }
        }

        List<AbstractWidget> row = null;
        for (List<AbstractWidget> candidate : iconRows.values()) {
            if (candidate.size() >= 2 && (row == null || candidate.size() > row.size())) {
                row = candidate;
            }
        }

        AbstractWidget button;
        if (row != null) {
            // Take every widget on that line, not only the icons: in 26.1 the icons sit on both sides
            // of Options/Quit Game, in 26.2+ they are grouped, and shifting only icons would overlap buttons
            int rowY = row.get(0).getY();
            List<AbstractWidget> line = new ArrayList<>();
            int right = 0;
            for (AbstractWidget widget : widgets) {
                if (widget.visible && widget.getY() == rowY) {
                    line.add(widget);
                    right = Math.max(right, widget.getX() + widget.getWidth());
                }
            }
            button = SkinButton.create(screen, right + GAP, rowY);

            // Shift the whole line left by half of the added width to keep it centered
            int shift = (ICON_BUTTON_SIZE + GAP) / 2;
            for (AbstractWidget widget : line) {
                widget.setX(widget.getX() - shift);
            }
            button.setX(button.getX() - shift);
        } else {
            button = SkinButton.create(screen, screen.width / 2 + 104, screen.height / 4 + 48 + 72);
        }
        add.accept(button);
    }

    /** Next to "Back to Game"; the menu-less pause screen (F3+Esc) has no such button and gets none. */
    private static void addPauseScreenButton(Screen screen, List<AbstractWidget> widgets, Consumer<AbstractWidget> add) {
        AbstractWidget backButton = null;
        for (AbstractWidget widget : widgets) {
            if (widget.getMessage().getContents() instanceof TranslatableContents translatable
                    && "menu.returnToGame".equals(translatable.getKey())) {
                backButton = widget;
                break;
            }
        }
        if (backButton == null) {
            return;
        }
        int x = backButton.getX() + backButton.getWidth() + GAP;
        add.accept(SkinButton.create(screen, x, backButton.getY()));
    }

    /** The Minecraft version this jar was built for (mogged-build.properties, written by Gradle). */
    private static boolean minecraftAtLeast(int major, int minor) {
        try (InputStream in = MoggedClient.class.getResourceAsStream("/mogged-build.properties")) {
            if (in == null) {
                return true;
            }
            Properties properties = new Properties();
            properties.load(in);
            String[] parts = properties.getProperty("minecraft", "").split("\\.");
            int first = Integer.parseInt(parts[0]);
            int second = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return first > major || first == major && second >= minor;
        } catch (Exception e) {
            Mogged.LOGGER.debug("Could not read the build's Minecraft version", e);
            return true;
        }
    }
}
