package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;
/** 以界面纹理绘制的按钮，可附带一个左侧图标。 */
@OnlyIn(Dist.CLIENT)
public class LogicButton extends Button {
	private static final int DISABLED_COLOR = 0xFF808080, TEXT_COLOR = 0xFFFFFFFF;
	/** 图标距按钮左边缘的距离；文字独立居中，不随图标排布。 */
	private static final int ICON_PAD = 6;
	private final @Nullable LogicIcons icon;
	public LogicButton(int x, int y, int width, int height, Component message, @Nullable LogicIcons icon, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.icon = icon;
	}
	/**
	 * 覆盖原版的按下音，改用 {@code uiButton}。
	 * <p>{@code AbstractWidget.playDownSound} 是原版按钮音的唯一出口，点击与回车均经此，
	 * 在此替换即整体替换按钮音，不会重复播放。
	 */
	@Override
	public void playDownSound(SoundManager handler) {
		LogicSounds.button();
	}
	@Override
	protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		if (isHoveredOrFocused() && active) LogicCursor.setHand();
		// 先前绘制可能残留染色，不复位会污染按钮纹理
		gui.setColor(1F, 1F, 1F, 1F);
		var texture = active && isHoveredOrFocused() ? LogicGuiTextures.BUTTON_OVER : LogicGuiTextures.BUTTON;
		texture.render(gui, getX(), getY(), getWidth(), getHeight());
		var color = active ? TEXT_COLOR : DISABLED_COLOR;
		if (icon == null) {
			// 不可走 AbstractButton.renderString：其内部固定启用阴影，会使细笔画模糊
			LogicFont.drawCentered(gui, getMessage(), getX() + getWidth() / 2, getY() + getHeight() / 2 - 4, color);
			return;
		}
		// 图标贴按钮左边缘，文字在其余区域居中，两者独立定位
		var iconX = getX() + ICON_PAD;
		icon.render(gui, iconX, LogicIcons.centerY(getY(), getHeight()), color);
		var textCenter = (iconX + icon.width() + getX() + getWidth()) / 2;
		LogicFont.drawCentered(gui, getMessage(), textCenter, getY() + getHeight() / 2 - 4, color);
	}
}
