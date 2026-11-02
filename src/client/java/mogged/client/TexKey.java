package mogged.client;

import java.util.Objects;

/**
 * Version-neutral reference to a texture. {@code handle} is the version's own type
 * (ClientAsset.Texture, ResourceLocation...) and only the platform layer looks at it.
 * Two keys are equal when they point at the same texture path.
 */
public final class TexKey {
    private final String path;
    private final Object handle;

    public TexKey(String path, Object handle) {
        this.path = Objects.requireNonNull(path);
        this.handle = handle;
    }

    public String path() {
        return path;
    }

    public Object handle() {
        return handle;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TexKey key && key.path.equals(path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return path;
    }
}
