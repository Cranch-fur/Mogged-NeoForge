package mogged.client.gui;

import java.nio.file.Path;
import java.util.List;
import mogged.client.ClientSkins;
import mogged.client.SkinConfig;
import mogged.client.platform.BaseScreen;
import mogged.net.SkinKinds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Settings of one custom preset: skin, cape and elytra sources. */
public class PresetScreen extends BaseScreen {
    private static final int SKIN = 0;
    private static final int CAPE = 1;
    private static final int ELYTRA = 2;
    private static final int FIELDS = 3;
    private static final String[] LABELS = {"mogged.label.skin_src", "mogged.label.cape_src", "mogged.label.elytra_src"};
    // Reload a typed source after this many ticks without changes
    private static final int TYPING_DELAY_TICKS = 12;
    private static final int BUTTON_HEIGHT = 20;
    private static final int LABEL_COLOR = 0xFFA0A0A0;

    private final Screen parent;
    private final int preset;
    private final int[] kinds;
    private final EditBox[] fields = new EditBox[FIELDS];
    private final Button[] browseButtons = new Button[FIELDS];
    private final int[] pending = new int[FIELDS];
    private final int[] labelY = new int[FIELDS];
    private ModelPreview preview;
    private int formX;
    private int formWidth;
    private int lastFocused;

    public PresetScreen(Screen parent, int preset) {
        super(Component.translatable("mogged.preset.title", ClientSkins.presetName(preset)));
        this.parent = parent;
        this.preset = preset;
        this.kinds = new int[]{SkinKinds.skin(preset), SkinKinds.cape(preset), SkinKinds.elytra(preset)};
    }

    private SkinConfig.Preset config() {
        return SkinConfig.get().preset(preset);
    }

    @Override
    protected void init() {
        int previewWidth = Math.max(60, Math.min(150, width / 3));
        formWidth = Math.min(280, width - previewWidth - 30);
        int left = width / 2 - (previewWidth + 10 + formWidth) / 2;
        formX = left + previewWidth + 10;

        int top = 22;
        int previewHeight = Math.max(60, height - top - 32);
        preview = new ModelPreview(left, top, previewWidth, previewHeight,
                scene -> ClientSkins.fillScene(preset, scene, PreviewBack.get(preset)))
                .corner(() -> PreviewBack.label(preset), () -> PreviewBack.toggle(preset));
        addRenderableWidget(preview.host());

        // Skin
        int y = top;
        labelY[SKIN] = y;
        addField(SKIN, y + 10, "mogged.hint.skin");
        y += 44;

        int half = (formWidth - 4) / 2;
        addRenderableWidget(Button.builder(modelLabel(config().slim), button -> {
            SkinConfig.Preset config = config();
            config.slim = !config.slim;
            button.setMessage(modelLabel(config.slim));
            ClientSkins.updateSlim(kinds[SKIN], config.slim);
        }).bounds(formX, y, half, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.translatable("mogged.button.editor"),
                        button -> open(new SkinEditorScreen(this, preset)))
                .bounds(formX + half + 4, y, formWidth - half - 4, BUTTON_HEIGHT)
                .build());
        y += 26;

        // Cape
        labelY[CAPE] = y;
        addField(CAPE, y + 10, "mogged.hint.cape");
        y += 44;

        // Elytra
        addRenderableWidget(Button.builder(elytraLabel(), button -> {
                    SkinConfig.Preset config = config();
                    config.separateElytra = !config.separateElytra;
                    button.setMessage(elytraLabel());
                    updateElytraVisibility();
                    ClientSkins.loadSelf(kinds[ELYTRA]);
                })
                .bounds(formX, y, formWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("mogged.tooltip.elytra")))
                .build());
        y += 24;
        labelY[ELYTRA] = y;
        addField(ELYTRA, y + 10, "mogged.hint.elytra");
        updateElytraVisibility();

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 100, height - 24, 200, BUTTON_HEIGHT)
                .build());
    }

    private void addField(int field, int y, String hintKey) {
        int kind = kinds[field];
        MutableComponent hint = Component.translatable(hintKey);

        // Without native dialogs (26.3) the field takes the full width; drag & drop still works
        boolean browse = FileDialogs.isAvailable();
        EditBox box = new EditBox(font, formX, y, browse ? formWidth - 24 : formWidth, BUTTON_HEIGHT, hint);
        box.setMaxLength(2048);
        box.setValue(SkinConfig.get().rawSource(kind));
        box.setHint(hint.copy().withStyle(ChatFormatting.DARK_GRAY));
        box.setResponder(value -> {
            SkinConfig.get().setSource(kind, value);
            pending[field] = TYPING_DELAY_TICKS;
            lastFocused = field;
        });
        fields[field] = addRenderableWidget(box);

        if (!browse) {
            return;
        }
        browseButtons[field] = addRenderableWidget(Button.builder(Component.literal("..."), button -> browse(field))
                .bounds(formX + formWidth - 22, y, 22, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("mogged.tooltip.browse")))
                .build());
    }

    private void updateElytraVisibility() {
        boolean visible = config().separateElytra;
        fields[ELYTRA].visible = visible;
        if (browseButtons[ELYTRA] != null) {
            browseButtons[ELYTRA].visible = visible;
        }
    }

    private static Component modelLabel(boolean slim) {
        return Component.translatable("mogged.button.model",
                Component.translatable(slim ? "mogged.model.slim" : "mogged.model.classic"));
    }

    private Component elytraLabel() {
        return Component.translatable("mogged.button.elytra",
                Component.translatable(config().separateElytra ? "mogged.elytra.separate" : "mogged.elytra.same"));
    }

    private void browse(int field) {
        lastFocused = field;
        String current = SkinConfig.get().rawSource(kinds[field]);
        String start = current != null && !current.isBlank() && !current.startsWith("http") ? current : "";
        FileDialogs.openPng(Component.translatable("mogged.dialog.title").getString(), start,
                path -> setAndLoad(field, path));
    }

    private void setAndLoad(int field, String source) {
        fields[field].setValue(source);
        SkinConfig.get().setSource(kinds[field], source);
        pending[field] = 0;
        ClientSkins.loadSelf(kinds[field]);
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (files.isEmpty()) {
            return;
        }
        // Hidden elytra field: drop onto the cape instead
        int field = lastFocused == ELYTRA && !config().separateElytra ? CAPE : lastFocused;
        setAndLoad(field, files.get(0).toAbsolutePath().toString());
    }

    @Override
    public void tick() {
        super.tick();
        for (int field = 0; field < FIELDS; field++) {
            if (fields[field] != null && fields[field].isFocused()) {
                lastFocused = field;
            }
            if (pending[field] > 0 && --pending[field] == 0) {
                ClientSkins.loadSelf(kinds[field]);
            }
        }
    }

    @Override
    protected void drawScreen(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(font, title, width / 2, 7, 0xFFFFFFFF);

        for (int field = 0; field < FIELDS; field++) {
            if (field == ELYTRA && !config().separateElytra) {
                continue;
            }
            graphics.text(font, Component.translatable(LABELS[field]), formX, labelY[field], LABEL_COLOR);

            Component status = ClientSkins.status(kinds[field]);
            if (status != null) {
                String text = font.plainSubstrByWidth(status.getString(), formWidth);
                int color = status.getStyle().getColor() != null
                        ? 0xFF000000 | status.getStyle().getColor().getValue()
                        : LABEL_COLOR;
                graphics.text(font, text, formX + 1, labelY[field] + 32, color);
            }
        }
    }

    @Override
    public void removed() {
        if (preview != null) {
            preview.release();
        }
    }

    @Override
    public void onClose() {
        // Apply sources that were still waiting for the typing delay
        for (int field = 0; field < FIELDS; field++) {
            if (pending[field] > 0) {
                pending[field] = 0;
                ClientSkins.loadSelf(kinds[field]);
            }
        }
        SkinConfig.get().save();
        open(parent);
    }
}
