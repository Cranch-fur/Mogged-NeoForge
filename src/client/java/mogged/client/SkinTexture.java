package mogged.client;

import mogged.client.platform.GpuTexture;
import mogged.client.render.ModelRaster;

/** A decoded texture on the GPU, plus its source PNG (for sending to others) and its pixels (for previews). */
public final class SkinTexture {
    public final byte[] png;
    public final boolean slim;
    public final int width;
    public final int height;
    public final int[] argb;
    private final GpuTexture texture;

    private SkinTexture(String name, byte[] png, boolean slim, ModelRaster.Texture pixels) {
        this.png = png;
        this.slim = slim;
        this.width = pixels.width();
        this.height = pixels.height();
        this.argb = pixels.argb();
        this.texture = new GpuTexture(name);
        texture.upload(argb, width, height);
    }

    public static SkinTexture create(String owner, int kind, byte[] png, boolean slim) {
        return new SkinTexture("dynamic/" + owner + "/" + kind, png, slim, SkinImages.decode(png, kind));
    }

    /** Texture built in memory (no PNG, never sent over the network). */
    public static SkinTexture fromPixels(String owner, String name, ModelRaster.Texture pixels) {
        return new SkinTexture("dynamic/" + owner + "/" + name, null, false, pixels);
    }

    public TexKey key() {
        return texture.key();
    }

    public ModelRaster.Texture pixels() {
        return new ModelRaster.Texture(width, height, argb);
    }

    public void release() {
        texture.release();
    }
}
