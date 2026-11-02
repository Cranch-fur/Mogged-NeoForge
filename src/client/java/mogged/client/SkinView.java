package mogged.client;

/**
 * Version-neutral player skin: body texture, optional cape and elytra, model type.
 * The platform layer converts it from and to the version's own skin representation.
 */
public record SkinView(TexKey body, TexKey cape, TexKey elytra, boolean slim) {
}
