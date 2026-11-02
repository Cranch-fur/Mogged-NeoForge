package mogged.client.gui;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

/**
 * Native open/save dialogs for PNG files via TinyFD.
 * Minecraft 26.3 no longer ships TinyFD, so it is looked up at runtime and {@link #isAvailable()} may be false.
 */
final class FileDialogs {
    private static final String TINYFD = "org.lwjgl.util.tinyfd.TinyFileDialogs";
    private static final MethodHandle OPEN_DIALOG = find("tinyfd_openFileDialog", MethodType.methodType(String.class,
            CharSequence.class, CharSequence.class, PointerBuffer.class, CharSequence.class, boolean.class));
    private static final MethodHandle SAVE_DIALOG = find("tinyfd_saveFileDialog", MethodType.methodType(String.class,
            CharSequence.class, CharSequence.class, PointerBuffer.class, CharSequence.class));

    private FileDialogs() {
    }

    private static MethodHandle find(String name, MethodType type) {
        try {
            return MethodHandles.publicLookup().findStatic(Class.forName(TINYFD), name, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    static boolean isAvailable() {
        return OPEN_DIALOG != null && SAVE_DIALOG != null;
    }

    static void openPng(String title, String defaultPath, Consumer<String> onChosen) {
        show(() -> withPngFilter(filter -> (String) OPEN_DIALOG.invoke(title, defaultPath, filter, "PNG", false)), onChosen);
    }

    static void savePng(String title, String defaultName, Consumer<String> onChosen) {
        show(() -> withPngFilter(filter -> (String) SAVE_DIALOG.invoke(title, defaultName, filter, "PNG")), onChosen);
    }

    private interface Dialog {
        String run(PointerBuffer filter) throws Throwable;
    }

    private static String withPngFilter(Dialog dialog) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filter = stack.mallocPointer(1);
            filter.put(stack.UTF8("*.png"));
            filter.flip();
            return dialog.run(filter);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Dialogs block, so run them off the render thread; macOS requires the main thread. */
    private static void show(Supplier<String> dialog, Consumer<String> onChosen) {
        if (!isAvailable()) {
            return;
        }
        Runnable task = () -> {
            String path = dialog.get();
            if (path != null) {
                Minecraft.getInstance().execute(() -> onChosen.accept(path));
            }
        };
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")) {
            task.run();
        } else {
            Thread thread = new Thread(task, "Mogged file dialog");
            thread.setDaemon(true);
            thread.start();
        }
    }
}
