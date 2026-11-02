package mogged.client;

import mogged.client.render.ElytraFromCape;
import mogged.client.render.ModelRaster;
import mogged.net.SkinKinds;

/** Custom textures of one player plus the animated transition between presets. */
public final class PlayerSkins {
    private static final long UPLOAD_INTERVAL_NANOS = 8_000_000L;

    public final String owner;
    public final SkinTexture[] textures = new SkinTexture[SkinKinds.COUNT];
    public int active = 1;
    public int durationMs = 1200;
    public boolean effects = true;

    // Elytra generated from a preset's cape when it has no own elytra texture
    private final SkinTexture[] derivedElytra = new SkinTexture[SkinKinds.PRESETS];
    private final TextureBlend bodyBlend;
    private final TextureBlend capeBlend;
    private final TextureBlend elytraBlend;

    private boolean transitioning;
    private boolean needsSetup;
    private long startNanos;
    private long lastUploadNanos;
    private int from;
    private int to;
    private float progress;
    private SkinView fromSkin;
    private SkinView toSkin;
    private boolean blendBody;
    private boolean blendCape;
    private boolean blendElytra;

    private SkinView cachedFor;
    private SkinView cachedResult;

    public PlayerSkins(String owner) {
        this.owner = owner;
        this.bodyBlend = new TextureBlend(owner, "body");
        this.capeBlend = new TextureBlend(owner, "cape");
        this.elytraBlend = new TextureBlend(owner, "elytra");
    }

    public void setTexture(int kind, SkinTexture texture) {
        SkinTexture old = textures[kind];
        textures[kind] = texture;
        if (old != null && old != texture) {
            old.release();
        }

        if (SkinKinds.isCape(kind)) {
            int index = SkinKinds.presetOf(kind) - 1;
            if (derivedElytra[index] != null) {
                derivedElytra[index].release();
                derivedElytra[index] = null;
            }
            if (texture != null) {
                ModelRaster.Texture elytra = ElytraFromCape.derive(texture.pixels());
                if (elytra != null) {
                    derivedElytra[index] = SkinTexture.fromPixels(owner, "elytra" + (index + 1), elytra);
                }
            }
        }

        if (transitioning) {
            finishTransition();
        }
        invalidate();
    }

    public void invalidate() {
        cachedFor = null;
        cachedResult = null;
    }

    public boolean hasAnything() {
        for (SkinTexture texture : textures) {
            if (texture != null) {
                return true;
            }
        }
        return false;
    }

    public boolean presetHasContent(int preset) {
        if (preset == SkinKinds.DEFAULT_PRESET) {
            return true;
        }
        return textures[SkinKinds.skin(preset)] != null
                || textures[SkinKinds.cape(preset)] != null
                || textures[SkinKinds.elytra(preset)] != null;
    }

    /** True when the active preset (or a running transition) brings its own cape or elytra. */
    public boolean hasOwnCosmetics() {
        if (transitioning) {
            return true;
        }
        return active != SkinKinds.DEFAULT_PRESET
                && (textures[SkinKinds.cape(active)] != null || textures[SkinKinds.elytra(active)] != null);
    }

    public boolean isTransitioning() {
        return transitioning;
    }

    public float progress() {
        return transitioning ? progressAt(System.nanoTime()) : 1.0F;
    }

    private float progressAt(long now) {
        return Math.min(1.0F, (now - startNanos) / (durationMs * 1_000_000.0F));
    }

    public void setActive(int preset, boolean animate) {
        preset = Math.max(SkinKinds.DEFAULT_PRESET, Math.min(SkinKinds.MAX_PRESET, preset));
        if (transitioning) {
            finishTransition();
        }
        if (preset == active) {
            return;
        }

        int previous = active;
        active = preset;
        invalidate();

        if (animate && durationMs > 0) {
            from = previous;
            to = preset;
            startNanos = System.nanoTime();
            lastUploadNanos = 0L;
            progress = 0.0F;
            transitioning = true;
            needsSetup = true;
        }
    }

    private void finishTransition() {
        transitioning = false;
        needsSetup = false;
        fromSkin = null;
        toSkin = null;
        bodyBlend.clearData();
        capeBlend.clearData();
        elytraBlend.clearData();
        invalidate();
    }

    /** Builds the skin of a preset on top of the vanilla one. */
    public SkinView resolve(int preset, SkinView vanilla) {
        if (preset == SkinKinds.DEFAULT_PRESET) {
            return vanilla;
        }
        SkinTexture skin = textures[SkinKinds.skin(preset)];
        SkinTexture cape = textures[SkinKinds.cape(preset)];
        SkinTexture elytra = textures[SkinKinds.elytra(preset)];
        SkinTexture derived = derivedElytra[preset - 1];

        TexKey capeKey = cape != null ? cape.key() : vanilla.cape();
        // Own elytra > elytra derived from cape > cape itself > vanilla elytra
        TexKey elytraKey;
        if (elytra != null) {
            elytraKey = elytra.key();
        } else if (derived != null) {
            elytraKey = derived.key();
        } else if (cape != null) {
            elytraKey = cape.key();
        } else {
            elytraKey = vanilla.elytra();
        }

        boolean slim = skin != null ? skin.slim : vanilla.slim();
        return new SkinView(skin != null ? skin.key() : vanilla.body(), capeKey, elytraKey, slim);
    }

    /** Pixels of a texture: ours from memory, anything else read back from the game. */
    public ModelRaster.Texture pixelsOf(TexKey key) {
        if (key == null) {
            return null;
        }
        for (SkinTexture texture : textures) {
            if (texture != null && key.equals(texture.key())) {
                return texture.pixels();
            }
        }
        for (SkinTexture texture : derivedElytra) {
            if (texture != null && key.equals(texture.key())) {
                return texture.pixels();
            }
        }
        return VanillaPixels.get(key);
    }

    private static boolean same(TexKey a, TexKey b) {
        return a == null ? b == null : a.equals(b);
    }

    /**
     * Prepares the blended textures for a transition. Done lazily on the first frame,
     * because only then do we know the vanilla skin the presets are layered on.
     * Parts that do not change (e.g. same cape in both presets) are not blended.
     */
    private void setupTransition(SkinView vanilla) {
        needsSetup = false;
        fromSkin = resolve(from, vanilla);
        toSkin = resolve(to, vanilla);
        int seed = owner.hashCode();

        blendBody = !same(fromSkin.body(), toSkin.body())
                && bodyBlend.setup(pixelsOf(fromSkin.body()), pixelsOf(toSkin.body()), true, seed);
        blendCape = fromSkin.cape() != null && toSkin.cape() != null
                && !same(fromSkin.cape(), toSkin.cape())
                && capeBlend.setup(pixelsOf(fromSkin.cape()), pixelsOf(toSkin.cape()), false, seed + 1);
        blendElytra = fromSkin.elytra() != null && toSkin.elytra() != null
                && !same(fromSkin.elytra(), toSkin.elytra())
                && elytraBlend.setup(pixelsOf(fromSkin.elytra()), pixelsOf(toSkin.elytra()), false, seed + 2);
    }

    /** Returns the skin to render right now. */
    public SkinView apply(SkinView vanilla) {
        if (transitioning) {
            if (needsSetup) {
                setupTransition(vanilla);
            }
            long now = System.nanoTime();
            float t = progressAt(now);
            if (t < 1.0F) {
                return transitionFrame(now, t);
            }
            finishTransition();
        }

        if (active == SkinKinds.DEFAULT_PRESET) {
            return vanilla;
        }
        // Vanilla skins arrive as fresh objects every frame, so compare by value
        if (vanilla.equals(cachedFor) && cachedResult != null) {
            return cachedResult;
        }
        SkinView result = resolve(active, vanilla);
        cachedFor = vanilla;
        cachedResult = result;
        return result;
    }

    private SkinView transitionFrame(long now, float t) {
        // Throttle texture uploads, they are the expensive part
        if (now - lastUploadNanos >= UPLOAD_INTERVAL_NANOS) {
            lastUploadNanos = now;
            progress = t;
            // Ease in-out (quadratic) so the sweep starts and ends softly
            float eased = t < 0.5F ? 2.0F * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 2.0) / 2.0F;
            // Widen the range so the glow band fully enters and leaves the texture
            float line = eased * (1.0F + 2 * TextureBlend.HALF_BAND) - TextureBlend.HALF_BAND;
            if (blendBody) {
                bodyBlend.update(line);
            }
            if (blendCape) {
                capeBlend.update(line);
            }
            if (blendElytra) {
                elytraBlend.update(line);
            }
        }

        // Parts that are not blended (and the model type) switch at the halfway point
        SkinView target = progress >= 0.5F ? toSkin : fromSkin;
        return new SkinView(
                blendBody ? bodyBlend.key() : target.body(),
                blendCape ? capeBlend.key() : target.cape(),
                blendElytra ? elytraBlend.key() : target.elytra(),
                target.slim());
    }

    public void releaseAll() {
        for (int i = 0; i < derivedElytra.length; i++) {
            if (derivedElytra[i] != null) {
                derivedElytra[i].release();
                derivedElytra[i] = null;
            }
        }
        for (int i = 0; i < textures.length; i++) {
            if (textures[i] != null) {
                textures[i].release();
                textures[i] = null;
            }
        }
        transitioning = false;
        bodyBlend.release();
        capeBlend.release();
        elytraBlend.release();
        invalidate();
    }
}
