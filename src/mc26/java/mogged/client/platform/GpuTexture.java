package mogged.client.platform;

import com.mojang.blaze3d.platform.NativeImage;
import mogged.client.TexKey;
import mogged.platform.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

/** A texture we fill from ARGB pixels on the CPU (26.x). Reallocated when the size changes. */
public final class GpuTexture {
    private static int counter;

    private final String name;
    private DynamicTexture texture;
    private Identifier id;
    private TexKey key;
    private int width;
    private int height;

    /** @param name readable part of the texture id, e.g. "dynamic/self/body" */
    public GpuTexture(String name) {
        this.name = name;
    }

    public void upload(int[] argb, int width, int height) {
        if (texture == null || this.width != width || this.height != height) {
            release();
            this.width = width;
            this.height = height;
            id = Ids.mod(name + "_" + counter++);
            key = new TexKey(id.toString(), new ClientAsset.ResourceTexture(id, id));
            Identifier label = id;
            texture = new DynamicTexture(label::toString, new NativeImage(width, height, true));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        NativeImage pixels = texture.getPixels();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels.setPixel(x, y, argb[y * width + x]);
            }
        }
        texture.upload();
    }

    /** Null until the first upload. */
    public TexKey key() {
        return key;
    }

    Identifier id() {
        return id;
    }

    public void release() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
        }
    }
}
