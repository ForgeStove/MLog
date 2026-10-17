package io.github.forgestove.mlog.client.gui.component;
import io.github.forgestove.mlog.client.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
/** 竖直滚动条：滚动量、滑块位置、命中、拖动、翻页与平滑，只在内容超过一屏时出现。 */
public final class ScrollBar {
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
	private int x, y, height, content;
	/** 可视区变化后、其余调用之前调一次。 */
	public void area(int x, int y, int height, int content) {
		this.x = x;
		this.y = y;
		this.height = height;
		this.content = content;
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
		var max = Math.max(0, content - height);
		target = Math.clamp(target, 0, max);
		scroll = Math.clamp(scroll, 0, max);
		var seconds = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks() / 20.0;
		var rest = target - scroll;
		var step = Math.signum(rest) * Math.max(MIN_SPEED, Math.abs(rest) * RATIO) * seconds;
		scroll = Math.abs(step) >= Math.abs(rest) ? target : scroll + step;
	}
	/** {@code amount} 为滚轮格数，正数向下；一格距离由 {@link #step} 决定。 */
	public void wheel(double amount) {
		target += amount * step;
	}
	public void render(GuiGraphics gui, int mouseX, int mouseY) {
		if (!shown()) return;
		// 滑块可拖动，悬停与拖动中显示手型
		if (dragging || mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height) LogicCursor.setHand();
		LogicGuiTextures.SCROLL.render(gui, x, y, WIDTH, height);
		var h = knobHeight();
		LogicGuiTextures.SCROLL_KNOB.render(gui, x, knobY(h), WIDTH, h);
	}
	/** 内容不超出一屏时整条不绘制也不响应事件。 */
	public boolean shown() {
		return content > height;
	}
	private int knobHeight() {
		return content > height ? Math.max(MIN_KNOB_H, height * height / content) : 0;
	}
	private int knobY(int knobH) {
		return y + (int) ((height - knobH) * (scroll / (content - height)));
	}
	/** 点在滑块上为抓取并继续拖动（不跳位置），空白处为翻页；不在滚动条上返回 {@code false}。 */
	public boolean mousePressed(double mouseX, double mouseY) {
		if (mouseX < x || mouseX >= x + WIDTH || !shown()) return false;
		var knobH = knobHeight();
		var knobY = knobY(knobH);
		if (knobY <= mouseY && mouseY < knobY + knobH) {
			dragging = true;
			grab = mouseY - knobY;
		} else scrollBy(mouseY < knobY ? -height : height);
		return true;
	}
	public void scrollBy(double delta) {
		target += delta;
	}
	/** 拖动中：按抓取时的偏移还原滑块位置，从鼠标按下处继续拖动；未在拖动时返回 {@code false}。 */
	public boolean mouseDragged(double mouseY) {
		if (!dragging || !shown()) return false;
		var travel = height - knobHeight();
		if (travel <= 0) return false;
		// 直接落位，不经过插值：滑块按 scroll 绘制，写目标值会让鼠标移开后仍继续追赶
		scroll = target = Math.clamp((mouseY - grab - y) * (content - height) / (double) travel, 0, content - height);
		return true;
	}
	public boolean dragging() {
		return dragging;
	}
	public void release() {
		dragging = false;
	}
}
