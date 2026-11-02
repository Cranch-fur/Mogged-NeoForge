package mogged.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import mogged.client.ClientSkins;
import mogged.client.MoggedClient;
import mogged.client.SkinConfig;
import mogged.client.platform.BaseScreen;
import mogged.client.platform.KeyPress;
import mogged.net.SkinKinds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Main screen: three preset columns (Default, 1, 2) and global settings. */
public class MoggedScreen extends BaseScreen {
    private static final int COLUMNS = SkinKinds.MAX_PRESET + 1;
    private static final int MAX_COLUMN_WIDTH = 150;
    private static final int COLUMN_GAP = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW = 22;
    private static final int SLIDER_MIN_MS = SkinConfig.MIN_DURATION_MS;
    private static final int SLIDER_MAX_MS = 3000;
    private static final int ACTIVE_COLOR = 0xFF55FF55;
    private static final int MUTED_COLOR = 0xFF909090;

    private final Screen parent;
    private final ModelPreview[] previews = new ModelPreview[COLUMNS];
    private final Button[] selectButtons = new Button[COLUMNS];
    private Button keyButton;
    private boolean listeningForKey;
    private int columnWidth;
    private int top;

    public MoggedScreen(Screen parent) {
        super(Component.translatable("mogged.title"));
        this.parent = parent;
    }

    private int totalWidth() {
        return columnWidth * COLUMNS + COLUMN_GAP * (COLUMNS - 1);
    }

    private int columnX(int column) {
        return width / 2 - totalWidth() / 2 + column * (columnWidth + COLUMN_GAP);
    }

    @Override
    protected void init() {
        SkinConfig config = SkinConfig.get();
        columnWidth = Math.min(MAX_COLUMN_WIDTH, (width - 40) / COLUMNS);
        top = 6;

        int previewY = top + 26;
        int previewHeight = Math.max(40, height - previewY - 4 - ROW - ROW - 26 - 26);
        int selectY = previewY + previewHeight + 4;
        int settingsY = selectY + ROW;
        int optionsY = settingsY + 26;

        for (int column = 0; column < COLUMNS; column++) {
            int preset = column;
            int x = columnX(column);

            addRenderableWidget(CheckToggle.create(x + columnWidth - 12, top + 12, config.cycle[preset],
                    Component.translatable("mogged.tooltip.cycle"), checked -> {
                        SkinConfig.get().cycle[preset] = checked;
                        SkinConfig.get().save();
                    }));

            previews[column] = new ModelPreview(x, previewY, columnWidth, previewHeight,
                    scene -> ClientSkins.fillScene(preset, scene, PreviewBack.get(preset)))
                    .corner(() -> PreviewBack.label(preset), () -> PreviewBack.toggle(preset));
            addRenderableWidget(previews[column].host());

            selectButtons[column] = addRenderableWidget(Button.builder(Component.empty(), button -> {
                ClientSkins.select(preset);
                updateSelectLabels();
            }).bounds(x, selectY, columnWidth, BUTTON_HEIGHT).build());

            if (preset != SkinKinds.DEFAULT_PRESET) {
                addRenderableWidget(Button.builder(Component.translatable("mogged.button.settings"),
                                button -> open(new PresetScreen(this, preset)))
                        .bounds(x, settingsY, columnWidth, BUTTON_HEIGHT).build());
            }
        }
        updateSelectLabels();

        // Bottom row: effects | duration | key
        int left = columnX(0);
        int third = (totalWidth() - 8) / 3;

        addRenderableWidget(Button.builder(effectsLabel(), button -> {
                    SkinConfig.get().effects = !SkinConfig.get().effects;
                    button.setMessage(effectsLabel());
                })
                .bounds(left, optionsY, third, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("mogged.tooltip.effects")))
                .build());

        addRenderableWidget(new DurationSlider(left + third + 4, optionsY, third, BUTTON_HEIGHT));

        keyButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    listeningForKey = !listeningForKey;
                    updateKeyLabel();
                })
                .bounds(left + 2 * (third + 4), optionsY, totalWidth() - 2 * (third + 4), BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("mogged.tooltip.key")))
                .build());
        updateKeyLabel();

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 100, Math.min(height - ROW, optionsY + 24), 200, BUTTON_HEIGHT)
                .build());
    }

    private void updateSelectLabels() {
        int active = ClientSkins.SELF.active;
        for (int i = 0; i < selectButtons.length; i++) {
            selectButtons[i].setMessage(i == active
                    ? Component.translatable("mogged.button.selected").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("mogged.button.select"));
        }
    }

    private static Component effectsLabel() {
        Component state = SkinConfig.get().effects ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        return Component.translatable("mogged.button.effects", state);
    }

    private void updateKeyLabel() {
        Component key = MoggedClient.toggleKey().getTranslatedKeyMessage();
        if (listeningForKey) {
            MutableComponent highlighted = Component.literal("> ")
                    .append(key.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                    .append(" <")
                    .withStyle(ChatFormatting.YELLOW);
            key = highlighted;
        }
        keyButton.setMessage(Component.translatable("mogged.button.key", key));
    }

    @Override
    protected boolean onKeyPressed(KeyPress press) {
        if (!listeningForKey) {
            return false;
        }
        // Esc unbinds the key
        InputConstants.Key key = press.isEscape() ? InputConstants.UNKNOWN : press.toInputKey();
        MoggedClient.toggleKey().setKey(key);
        KeyMapping.resetMapping();
        minecraft.options.save();
        listeningForKey = false;
        updateKeyLabel();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        updateSelectLabels();
    }

    @Override
    protected void drawScreen(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(font, title, width / 2, top, 0xFFFFFFFF);

        for (int column = 0; column < COLUMNS; column++) {
            int color = ClientSkins.SELF.active == column ? ACTIVE_COLOR : 0xFFFFFFFF;
            graphics.text(font, ClientSkins.presetName(column), columnX(column) + 2, top + 14, color);
        }

        // The Default column has no settings button, explain what it is instead
        int y = selectButtons[0].getY() + ROW;
        String label = font.plainSubstrByWidth(Component.translatable("mogged.label.game").getString(), columnWidth);
        graphics.text(font, label, columnX(0) + 2, y + 6, MUTED_COLOR);
    }

    @Override
    public void removed() {
        for (ModelPreview preview : previews) {
            if (preview != null) {
                preview.release();
            }
        }
    }

    @Override
    public void onClose() {
        SkinConfig config = SkinConfig.get();
        config.save();
        ClientSkins.SELF.durationMs = config.durationMs;
        ClientSkins.SELF.effects = config.effects;
        ClientSkins.sendState(false);
        open(parent);
    }

    private static final class DurationSlider extends AbstractSliderButton {
        private static final int RANGE = SLIDER_MAX_MS - SLIDER_MIN_MS;

        DurationSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (SkinConfig.get().durationMs - SLIDER_MIN_MS) / (double) RANGE);
            value = Math.max(0.0, Math.min(1.0, value));
            updateMessage();
            setTooltip(Tooltip.create(Component.translatable("mogged.tooltip.duration")));
        }

        /** Slider value rounded to 0.1 s. */
        private int milliseconds() {
            return (int) Math.round((SLIDER_MIN_MS + value * RANGE) / 100.0) * 100;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("mogged.button.duration", String.format("%.1f", milliseconds() / 1000.0F)));
        }

        @Override
        protected void applyValue() {
            SkinConfig.get().durationMs = milliseconds();
        }
    }
}
