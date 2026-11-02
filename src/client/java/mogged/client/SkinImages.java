package mogged.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import mogged.client.render.ModelRaster;
import mogged.net.SkinKinds;
import mogged.server.SkinServer;

/** Loading and normalizing skin/cape PNGs from files and URLs. */
public final class SkinImages {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    public static final int MAX_SKIN_SIZE = 1024;
    public static final int MAX_CAPE_WIDTH = 2048;
    public static final int MAX_CAPE_HEIGHT = 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    // Downloads and file reads run here, off the render thread
    private static final ExecutorService IO = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "Mogged IO");
        thread.setDaemon(true);
        return thread;
    });

    private SkinImages() {
    }

    public static boolean isUrl(String source) {
        String lower = source.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /** Trims the source and strips surrounding quotes (as in "Copy as path" on Windows). */
    public static String clean(String source) {
        String result = source == null ? "" : source.trim();
        boolean quoted = result.length() >= 2
                && (result.startsWith("\"") && result.endsWith("\"") || result.startsWith("'") && result.endsWith("'"));
        if (quoted) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    public static CompletableFuture<byte[]> readBytes(String rawSource) {
        String source = clean(rawSource);
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] bytes = isUrl(source) ? download(source) : readFile(source);
                if (bytes.length > MAX_BYTES) {
                    throw new LoadException("mogged.error.too_big");
                }
                if (!SkinServer.isPng(bytes)) {
                    throw new LoadException("mogged.error.not_png");
                }
                return bytes;
            } catch (LoadException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LoadException("mogged.error.download");
            } catch (IOException | IllegalArgumentException e) {
                throw new LoadException(isUrl(source) ? "mogged.error.download" : "mogged.error.read");
            }
        }, IO);
    }

    private static byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mogged-Minecraft-Mod")
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) {
            throw new LoadException("mogged.error.http", response.statusCode());
        }
        return response.body();
    }

    private static byte[] readFile(String source) throws IOException {
        Path path = source.regionMatches(true, 0, "file:", 0, 5) ? Path.of(URI.create(source)) : Path.of(source);
        if (!Files.isRegularFile(path)) {
            throw new LoadException("mogged.error.not_found");
        }
        if (Files.size(path) > MAX_BYTES) {
            throw new LoadException("mogged.error.too_big");
        }
        return Files.readAllBytes(path);
    }

    /** Decodes a PNG and normalizes it for its slot (skin or cape). */
    public static ModelRaster.Texture decode(byte[] png, int kind) {
        ModelRaster.Texture image = readPng(png);
        if (image == null) {
            throw new LoadException("mogged.error.not_png");
        }
        return SkinKinds.isSkin(kind) ? processSkin(image) : processCape(image);
    }

    /** PNG bytes -> ARGB pixels, or null if the data is not a readable image. */
    public static ModelRaster.Texture readPng(byte[] png) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                return null;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            return new ModelRaster.Texture(width, height, image.getRGB(0, 0, width, height, null, 0, width));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** ARGB pixels -> PNG file. */
    public static void writePng(ModelRaster.Texture pixels, Path path) throws IOException {
        BufferedImage image = new BufferedImage(pixels.width(), pixels.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, pixels.width(), pixels.height(), pixels.argb(), 0, pixels.width());
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("No PNG writer");
        }
    }

    /** Same as vanilla skin processing, but for any multiple of 64 px (HD skins). */
    static ModelRaster.Texture processSkin(ModelRaster.Texture source) {
        int width = source.width();
        int height = source.height();
        boolean validSize = width >= 64 && width <= MAX_SKIN_SIZE && width % 64 == 0 && (height == width || height * 2 == width);
        if (!validSize) {
            throw new LoadException("mogged.error.skin_size", width, height);
        }

        int scale = width / 64;
        boolean legacy = height * 2 == width;
        // Always work on a square copy; the lower half stays transparent for legacy skins
        int[] argb = new int[width * width];
        System.arraycopy(source.argb(), 0, argb, 0, width * height);

        if (legacy) {
            // Convert an old 64x32 layout to 64x64 by mirroring the right limbs into the left limb slots,
            // the same face-by-face copies vanilla does (arguments are in 64 px units, scaled for HD)
            copy(argb, width, scale, 4, 16, 16, 32, 4, 4);
            copy(argb, width, scale, 8, 16, 16, 32, 4, 4);
            copy(argb, width, scale, 0, 20, 24, 32, 4, 12);
            copy(argb, width, scale, 4, 20, 16, 32, 4, 12);
            copy(argb, width, scale, 8, 20, 8, 32, 4, 12);
            copy(argb, width, scale, 12, 20, 16, 32, 4, 12);
            copy(argb, width, scale, 44, 16, -8, 32, 4, 4);
            copy(argb, width, scale, 48, 16, -8, 32, 4, 4);
            copy(argb, width, scale, 40, 20, 0, 32, 4, 12);
            copy(argb, width, scale, 44, 20, -8, 32, 4, 12);
            copy(argb, width, scale, 48, 20, -16, 32, 4, 12);
            copy(argb, width, scale, 52, 20, -8, 32, 4, 12);
        }

        setNoAlpha(argb, width, 0, 0, 32 * scale, 16 * scale);
        if (legacy) {
            notchTransparencyHack(argb, width, 32 * scale, 0, 64 * scale, 32 * scale);
        }
        setNoAlpha(argb, width, 0, 16 * scale, 64 * scale, 32 * scale);
        setNoAlpha(argb, width, 16 * scale, 48 * scale, 48 * scale, 64 * scale);
        return new ModelRaster.Texture(width, width, argb);
    }

    /** Pads non-standard capes onto a 2:1 canvas that is a multiple of 64x32. */
    static ModelRaster.Texture processCape(ModelRaster.Texture source) {
        int width = source.width();
        int height = source.height();
        if (width > MAX_CAPE_WIDTH || height > MAX_CAPE_HEIGHT) {
            throw new LoadException("mogged.error.cape_size", width, height);
        }
        if (width % 64 == 0 && height * 2 == width) {
            return source;
        }

        int scale = Math.max(1, Math.max((width + 63) / 64, (height + 31) / 32));
        int paddedWidth = 64 * scale;
        int[] padded = new int[paddedWidth * 32 * scale];
        for (int y = 0; y < height; y++) {
            System.arraycopy(source.argb(), y * width, padded, y * paddedWidth, width);
        }
        return new ModelRaster.Texture(paddedWidth, 32 * scale, padded);
    }

    /** Mirrored rectangle copy, like NativeImage.copyRect(..., flipX = true, flipY = false). */
    private static void copy(int[] argb, int stride, int scale, int x, int y, int dx, int dy, int w, int h) {
        int srcX = x * scale;
        int srcY = y * scale;
        int width = w * scale;
        int height = h * scale;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int from = (srcY + row) * stride + srcX + col;
                int to = (srcY + dy * scale + row) * stride + srcX + dx * scale + (width - 1 - col);
                argb[to] = argb[from];
            }
        }
    }

    private static void setNoAlpha(int[] argb, int stride, int x1, int y1, int x2, int y2) {
        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                argb[y * stride + x] |= 0xFF000000;
            }
        }
    }

    /** Old skins used a fully opaque hat layer to mean "no hat". */
    private static void notchTransparencyHack(int[] argb, int stride, int x1, int y1, int x2, int y2) {
        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                if (argb[y * stride + x] >>> 24 < 128) {
                    return;
                }
            }
        }
        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                argb[y * stride + x] &= 0x00FFFFFF;
            }
        }
    }

    /** Load error with a translation key for the UI. */
    public static final class LoadException extends RuntimeException {
        public final String key;
        public final transient Object[] args;

        public LoadException(String key, Object... args) {
            super(key);
            this.key = key;
            this.args = args;
        }
    }
}
