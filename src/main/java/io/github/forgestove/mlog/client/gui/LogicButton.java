package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;
/** 用 Mindustry 纹理绘制的按钮，可选带一个左侧图标。 */
@OnlyIn(Dist.CLIENT)
public class LogicButton extends Button {
	private static final int DISABLED_COLOR = 0xFF808080, TEXT_COLOR = 0xFFFFFFFF;
	/** 图标距按钮左边缘的距离。文字是独立居中的，不跟图标排在一起。 */
	private static final int ICON_PAD = 6;
	private final @Nullable LogicIcons icon;
	public LogicButton(int x, int y, int width, int height, Component message, @Nullable LogicIcons icon, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.icon = icon;
	}
	@Override
	protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		if (isHoveredOrFocused() && active) LogicCursor.setHand();
		// 前面的绘制可能留下染色，不复位会污染按钮纹理
		gui.setColor(1F, 1F, 1F, 1F);
		var texture = active && isHoveredOrFocused() ? LogicGuiTextures.BUTTON_OVER : LogicGuiTextures.BUTTON;
		texture.render(gui, getX(), getY(), getWidth(), getHeight());
		var color = active ? TEXT_COLOR : DISABLED_COLOR;
		if (icon == null) {
			// 不能走 AbstractButton.renderString：它内部固定按带阴影画，会糊掉细笔画
			LogicFont.drawCentered(gui, getMessage(), getX() + getWidth() / 2, getY() + getHeight() / 2 - 4, color);
			return;
		}
		// 图标贴着按钮左边缘，文字在它右边剩下的那段里居中——两者各自定位，不互相牵引
		var iconX = getX() + ICON_PAD;
		icon.render(gui, iconX, LogicIcons.centerY(getY(), getHeight()), color);
		var textCenter = (iconX + icon.width() + getX() + getWidth()) / 2;
		LogicFont.drawCentered(gui, getMessage(), textCenter, getY() + getHeight() / 2 - 4, color);
	}
}
