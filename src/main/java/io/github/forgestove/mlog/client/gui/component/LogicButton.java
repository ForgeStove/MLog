package io.github.forgestove.mlog.client.gui.component;
import io.github.forgestove.mlog.client.gui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
/** 以界面纹理绘制的按钮，可附带一个左侧图标。 */
public class LogicButton extends ReleaseButton {
	private static final int DISABLED_COLOR = 0xFF808080, TEXT_COLOR = 0xFFFFFFFF;
	/** 图标字形的字号；左侧留白按它取与上下对称的值。 */
	private static final int ICON_SIZE = 11;
	private final @Nullable LogicIcons icon;
	public LogicButton(int x, int y, int width, int height, Component message, @Nullable LogicIcons icon, OnPress onPress) {
		super(x, y, width, height, message, onPress);
		this.icon = icon;
	}
	/** {@code AbstractWidget.playDownSound} 是原版按钮音的唯一出口，点击与回车均经此，在此替换即整体替换按钮音。 */
	@Override
	public void playDownSound(SoundManager handler) {
		LogicSounds.button();
	}
	/** 优先级：禁用、按下、悬停、常态。 */
	private LogicGuiTextures texture() {
		if (!active) return LogicGuiTextures.BUTTON_DISABLED;
		if (isPressed()) return LogicGuiTextures.BUTTON_DOWN;
		return isHoveredOrFocused() ? LogicGuiTextures.BUTTON_OVER : LogicGuiTextures.BUTTON;
	}
	@Override
	protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		// 光标只认悬停：聚焦会一直留在控件上，带焦点会让手型光标移开也不恢复
		if (isHovered() && active) LogicCursor.setHand();
		// 不复位会沿用先前绘制残留的颜色，按钮纹理随之变色
		gui.setColor(1F, 1F, 1F, 1F);
		texture().render(gui, getX(), getY(), getWidth(), getHeight());
		var color = active ? TEXT_COLOR : DISABLED_COLOR;
		var left = getX() + 2;
		if (icon != null) {
			icon.render(gui, getX() + Math.max(0, (getHeight() - ICON_SIZE) / 2), LogicIcons.centerY(getY(), getHeight()), color);
			left += icon.width();
		}
		// 不可走 AbstractButton.renderString：其内部固定启用阴影，会使细笔画模糊
		LogicFont.drawScrollingCentered(gui, getMessage(), left, getX() + getWidth() - 2, getY() + getHeight() / 2 - 4, color);
	}
}
