package io.github.forgestove.mlog.client.gui.component;
import io.github.forgestove.mlog.client.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
/** 竖直滚动条：滚动量、滑块位置、命中、拖动、翻页与平滑，只在内容超过一屏时出现。 */
public class ScrollBar extends AbstractWidget {
	public static final int WIDTH = 10;
	/** 滚轮一格滚过的可视区比例（{@code 0.9f / 4}）。 */
	public static final float WHEEL_RATIO = 0.9F / 4;
	/** 滑块的最小高度与滚轮一次的默认距离（列表两行高：16 + 2）。 */
	private static final int MIN_KNOB_H = 12, DEFAULT_STEP = 36;
	/** 每秒的平滑速度系数：{@code 7} 为比例项，{@code 200} 为最小速度，保证收尾不顿挫。 */
	private static final double RATIO = 7, MIN_SPEED = 200;
	/** 当前与目标滚动量：渲染用前者，拖动与翻页写后者，由 {@link #update} 逼近。 */
	private double scroll, target;
	/** 滚轮一格的距离；按行滚动的界面设为整行像素高。 */
	private double step = DEFAULT_STEP;
	/** 鼠标按下时相对滑块顶端的偏移，拖动时按它还原滑块位置。 */
	private double grab;
	private boolean dragging;
	private int content;
	/** 滚轮命中区，默认与滚动条本身相同，可扩大到整列/整块内容。 */
	private int wheelX, wheelY, wheelW, wheelH;
	public ScrollBar() {
		super(0, 0, WIDTH, 0, Component.empty());
	}
	/** 可视区变化后、其余调用之前调一次。 */
	public void area(int x, int y, int height, int content) {
		setX(x);
		setY(y);
		setWidth(WIDTH);
		setHeight(height);
		this.content = content;
		wheelArea(x, y, WIDTH, height);
	}
	/** 滚轮落在该区域内时由这条滚动条处理。 */
	public void wheelArea(int x, int y, int width, int height) {
		wheelX = x;
		wheelY = y;
		wheelW = width;
		wheelH = height;
	}
	public boolean wheelHit(double mouseX, double mouseY) {
		return shown() && mouseX >= wheelX && mouseX < wheelX + wheelW && mouseY >= wheelY && mouseY < wheelY + wheelH;
	}
	/** 渲染内容时按它平移。 */
	public double scroll() {
		return scroll;
	}
	public void reset() {
		scroll = target = 0;
	}
	/** 恢复上次位置用；超出部分由 {@link #update} 钳回。 */
	public void seek(double scroll) {
		this.scroll = target = scroll;
	}
	public void step(double step) {
		this.step = step;
	}
	/** 每帧渲染内容之前调一次。 */
	public void update() {
		var max = Math.max(0, content - getHeight());
		target = Math.clamp(target, 0, max);
		scroll = Math.clamp(scroll, 0, max);
		var seconds = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks() / 20.0;
		var rest = target - scroll;
		var step = Math.signum(rest) * Math.max(MIN_SPEED, Math.abs(rest) * RATIO) * seconds;
		scroll = Math.abs(step) >= Math.abs(rest) ? target : scroll + step;
	}
	/** {@code amount} 为滚轮格数，正数向上；一格距离由 {@link #step} 决定。 */
	public void wheel(double amount) {
		target -= amount * step;
	}
	@Override
	protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		if (!shown()) return;
		// 滑块可拖动，悬停与拖动中显示手型
		if (dragging || isHovered()) LogicCursor.setHand();
		LogicGuiTextures.SCROLL.render(gui, getX(), getY(), WIDTH, getHeight());
		var h = knobHeight();
		LogicGuiTextures.SCROLL_KNOB.render(gui, getX(), knobY(h), WIDTH, h);
	}
	/** 点在滑块上为抓取并继续拖动（不跳位置），空白处为翻页。 */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
		var knobH = knobHeight();
		var knobY = knobY(knobH);
		if (knobY <= mouseY && mouseY < knobY + knobH) {
			dragging = true;
			grab = mouseY - knobY;
		} else scrollBy(mouseY < knobY ? -getHeight() : getHeight());
		return true;
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (!dragging) return false;
		dragging = false;
		return true;
	}
	/** 拖动中：按抓取时的偏移还原滑块位置，从鼠标按下处继续拖动。 */
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (!dragging) return false;
		var travel = getHeight() - knobHeight();
		if (travel <= 0) return false;
		// 直接落位，不经过插值：滑块按 scroll 绘制，写目标值会让鼠标移开后仍继续追赶
		scroll = target = Math.clamp((mouseY - grab - getY()) * (content - getHeight()) / (double) travel, 0, content - getHeight());
		return true;
	}
	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return shown() && super.isMouseOver(mouseX, mouseY);
	}
	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {}
	/** 内容不超出一屏时整条不绘制也不响应事件。 */
	public boolean shown() {
		return content > getHeight();
	}
	private int knobHeight() {
		return content > getHeight() ? Math.max(MIN_KNOB_H, getHeight() * getHeight() / content) : 0;
	}
	private int knobY(int knobH) {
		return getY() + (int) ((getHeight() - knobH) * (scroll / (content - getHeight())));
	}
	public void scrollBy(double delta) {
		target += delta;
	}
}
