package io.github.forgestove.mlog.client.gui.component;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** 松开触发的按钮基类：按下记录状态，松开仍悬停才执行。 */
public abstract class ReleaseButton extends Button {
	/** 按下贴图在松开后的保留时长。 */
	private static final long VISUAL_PRESS_MS = 100;
	private boolean armed;
	/** 按下贴图保留到的时刻，从按下时算起。 */
	private long visualPressedUntil;
	protected ReleaseButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}
	/** 触发改在松开时，按下不执行。 */
	@Override
	public void onClick(double mouseX, double mouseY) {}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		armed = active && isValidClickButton(button) && isMouseOver(mouseX, mouseY);
		if (armed) visualPressedUntil = Util.getMillis() + VISUAL_PRESS_MS;
		return super.mouseClicked(mouseX, mouseY, button);
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		var trigger = armed && isValidClickButton(button) && isMouseOver(mouseX, mouseY);
		armed = false;
		if (trigger) onPress();
		return super.mouseReleased(mouseX, mouseY, button);
	}
	/** 按下后拖出按钮即失效；松开后的保留见 {@link #visualPressedUntil}。 */
	protected boolean isPressed() {
		return armed && isHovered() || Util.getMillis() < visualPressedUntil;
	}
}
