package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.LogicGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.*;
/**
 * 竖直滚动条：滚动量、滑块位置、命中、拖动、翻页与平滑都在这里。
 * <p>持有它的界面只管两件事：用 {@link #area} 告知可视区与内容高，以及在滚轮之类的输入上调用
 * {@link #scrollBy}。平移的数值钳制与逐帧插值都在此处完成，各调用方不必各自实现。
 * <p>只在内容超过一屏时出现，滚动条宽度见 {@link #WIDTH}。
 */
@OnlyIn(Dist.CLIENT)
public final class ScrollBar {
	/** 滚动条宽度。 */
	public static final int WIDTH = 10;
	/**
	 * 滚轮一格滚过的可视区比例（{@code 0.9f / 4}）。
	 * <p>取值是「可视区高的 22.5%，且不超过可视区高」，前半条恒小于后半条，因此实为纯比例。
	 */
	public static final float WHEEL_RATIO = 0.9F / 4;
	/**
	 * 滑块的最小高度，以及滚轮一次滚过的默认距离。
	 * <p>默认步长取列表行的两行高（行高 16 + 行距 2），与编辑器画布「滚两行语句」的行为一致。
	 */
	private static final int MIN_KNOB_H = 12, DEFAULT_STEP = 36;
	/**
	 * 平滑的两个速度系数，单位均为每秒。
	 * <p>{@code 7} 是比例项：每秒走掉剩余距离的 7 倍，越接近目标越小；
	 * {@code 200} 是最小速度，收尾阶段由它保证。若纯用比例逼近，越接近越慢，末尾会产生顿挫。
	 */
	private static final double RATIO = 7, MIN_SPEED = 200;
	/** 当前与目标滚动量。渲染用前者，拖动与翻页写后者，再由 {@link #update} 逼近。 */
	private double scroll, target;
	/** 滚轮一格滚动的距离。按行滚动的界面会将其设为整行的像素高。 */
	private double step = DEFAULT_STEP;
	/** 鼠标按下时相对滑块顶端的偏移。拖动时用它还原滑块位置，滑块才不会跳到鼠标中心。 */
	private double grab;
	/** 是否正在拖动。 */
	private boolean dragging;
	/** 轨道左边缘与顶端、可视区高度、内容总高，由 {@link #area} 设定，下列方法都按它定位。 */
	private int x, y, height, content;
	/** 设定轨道与可视区几何；可视区变化后、其余调用之前调一次。 */
	public void area(int x, int y, int height, int content) {
		this.x = x;
		this.y = y;
		this.height = height;
		this.content = content;
	}
	/** @return 当前滚动量，渲染内容时按它平移。 */
	public double scroll() {
		return scroll;
	}
	/** 直接落位，用于内容变化后重置。 */
	public void reset() {
		scroll = target = 0;
	}
	/** 直接落位到指定滚动量，用于恢复上次的位置。超出部分由 {@link #update} 钳回。 */
	public void seek(double scroll) {
		this.scroll = target = scroll;
	}
	/** 设定滚轮一格滚动的距离，如按整行高。 */
	public void step(double step) {
		this.step = step;
	}
	/** 钳到合法范围并平滑逼近目标。每帧渲染内容之前调一次。 */
	public void update() {
		var max = Math.max(0, content - height);
		target = Math.clamp(target, 0, max);
		scroll = Math.clamp(scroll, 0, max);
		var seconds = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks() / 20.0;
		var rest = target - scroll;
		var step = Math.signum(rest) * Math.max(MIN_SPEED, Math.abs(rest) * RATIO) * seconds;
		scroll = Math.abs(step) >= Math.abs(rest) ? target : scroll + step;
	}
	/**
	 * 滚轮：{@code amount} 为滚轮格数，正数向下。
	 * <p>一格滚动的距离由 {@link #step} 决定，默认为 {@link #DEFAULT_STEP}，按行滚动的界面可以自行设置。
	 */
	public void wheel(double amount) {
		target += amount * step;
	}
	/**
	 * 绘制滑槽与滑块。滑块恒定使用原色，不做悬停高亮。
	 */
	public void render(GuiGraphics gui) {
		if (!shown()) return;
		LogicGuiTextures.SCROLL.render(gui, x, y, WIDTH, height);
		var h = knobHeight();
		LogicGuiTextures.SCROLL_KNOB.render(gui, x, knobY(h), WIDTH, h);
	}
	/** 内容不超出一屏时整条不绘制，也不响应事件。 */
	public boolean shown() {
		return content > height;
	}
	/** @return 滑块高度，内容不超一屏时返回 0。 */
	private int knobHeight() {
		return content > height ? Math.max(MIN_KNOB_H, height * height / content) : 0;
	}
	/** @return 滑块顶端在轨道内的 y。 */
	private int knobY(int knobH) {
		return y + (int) ((height - knobH) * (scroll / (content - height)));
	}
	/**
	 * 按下。点在滑块上为抓取并继续拖动（不跳位置），点在轨道空白处为翻页。
	 *
	 * @return 事件是否处理掉了；鼠标不在滚动条上时返回 {@code false}
	 */
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
	/** 相对滚动一段，用于拖动时的自动滚动。 */
	public void scrollBy(double delta) {
		target += delta;
	}
	/**
	 * 拖动中：按抓取时的偏移还原滑块位置，从鼠标按下的位置继续拖动。
	 *
	 * @return 事件是否处理掉了；当前没在拖动时返回 {@code false}
	 */
	public boolean mouseDragged(double mouseY) {
		if (!dragging || !shown()) return false;
		var travel = height - knobHeight();
		if (travel <= 0) return false;
		// 直接落位，不经过插值：滑块按 scroll 绘制，若写入目标值，鼠标移开后滑块仍会追赶，表现为不跟手
		scroll = target = Math.clamp((mouseY - grab - y) * (content - height) / (double) travel, 0, content - height);
		return true;
	}
	/** @return 是否正在拖动。 */
	public boolean dragging() {
		return dragging;
	}
	public void release() {
		dragging = false;
	}
}
