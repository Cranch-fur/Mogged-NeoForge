package mogged.client.platform;

import mogged.client.gui.WidgetLogic;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/** Vanilla widget that hosts a shared {@link WidgetLogic} (26.x). */
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
    public void onPress(InputWithModifiers input) {
        logic.onPress();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        logic.onClick(event.x(), event.y());
        super.onClick(event, doubleClick);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        logic.onDrag(dragX, dragY);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (buttonBackground) {
            extractDefaultSprite(graphics);
        }
        logic.draw(new GfxImpl(graphics), this, mouseX, mouseY);
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
