package mogged.client.platform;

import mogged.client.gui.WidgetLogic;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/** Vanilla widget that hosts a shared {@link WidgetLogic} (1.20.1 / 1.21.1). */
public class WidgetHost extends AbstractButton {
    private final WidgetLogic logic;
    private final boolean buttonBackground;
    private final boolean clickSound;

    public WidgetHost(int x, int y, int width, int height, Component message, WidgetLogic logic,
                      boolean buttonBackground, boolean clickSound) {
        super(x, y, width, height, message);
        this.logic = logic;
        this.buttonBackground = buttonBackground;
        this.clickSound = clickSound;
    }

    /** Current fade-in alpha (the title screen fades its widgets in). */
    public float alpha() {
        return alpha;
    }

    @Override
    public void onPress() {
        logic.onPress();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        logic.onClick(mouseX, mouseY);
        super.onClick(mouseX, mouseY);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        logic.onDrag(dragX, dragY);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (buttonBackground) {
            // Draws the vanilla button background; the label is suppressed in renderString
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
        logic.draw(new GfxImpl(graphics), this, mouseX, mouseY);
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
        // The message is only for narration and tooltips
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        if (clickSound) {
            super.playDownSound(soundManager);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
