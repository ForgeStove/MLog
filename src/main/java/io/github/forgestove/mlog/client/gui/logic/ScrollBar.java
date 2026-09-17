package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.LogicGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.*;
/**
 * 竖直滚动条：滚动量、滑块位置、命中、拖动、翻页与平滑都在这里。
 * <p>持有它的界面只管两件事：把可视区与内容高传进来，以及在滚轮之类的输入上调用
 * {@link #scrollBy}。平移的数值钳制与逐帧插值都在这儿做，各处不必各写一套。
 * <p>只在内容超过一屏时出现，滚动条宽度见 {@link #WIDTH}。
 */
@OnlyIn(Dist.CLIENT)
public final class ScrollBar {
	/** 滚动条宽度。 */
	public static final int WIDTH = 10;
	/**
	 * 滑块的最小高度，以及滚轮一次滚过的默认距离。
	 * <p>默认步长取两张表那种行的两行高（行高 16 + 行距 2），和编辑器画布「滚两行语句」的手感一致。
	 */
	private static final int MIN_KNOB_H = 12, DEFAULT_STEP = 36;
	/**
	 * 平滑的两个速度系数，单位都是每秒，取自 arc 的 {@code ScrollPane.act}。
	 * <p>{@code 7} 是比例项：每秒走掉剩余距离的 7 倍，越接近目标越小；
	 * {@code 200} 是最小速度，收尾时由它兜住。纯比例逼近的话越接近越慢，末尾会拖出顿挫。
	 */
	private static final double RATIO = 7, MIN_SPEED = 200;
	/** 当前与目标滚动量。渲染用前者，拖动与翻页写后者，再由 {@link #update} 逼近。 */
	private double scroll, target;
	/** 滚轮一格滚多远。按行滚的界面会把它设成整行的像素高。 */
	private double step = DEFAULT_STEP;
	/** 鼠标按下时相对滑块顶端的偏移。拖动时用它还原滑块位置，滑块才不会跳到鼠标中心。 */
	private double grab;
	/** 是否正在拖动。 */
	private boolean dragging;
	/** @return 当前滚动量，渲染内容时按它平移。 */
	public double scroll() {
		return scroll;
	}
	/** 直接落位，用于内容变化后重置。 */
	public void reset() {
		scroll = target = 0;
	}
	/** 设定滚轮一格滚多远，比如按整行高。 */
	public void step(double step) {
		this.step = step;
	}
	/** 钳到合法范围并平滑逼近目标。每帧渲染内容之前调一次。 */
	public void update(int height, int content) {
		var max = Math.max(0, content - height);
		target = Math.clamp(target, 0, max);
		scroll = Math.clamp(scroll, 0, max);
		var seconds = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks() / 20.0;
		var rest = target - scroll;
		var step = Math.signum(rest) * Math.max(MIN_SPEED, Math.abs(rest) * RATIO) * seconds;
		scroll = Math.abs(step) >= Math.abs(rest) ? target : scroll + step;
	}
	/**
	 * 滚轮：{@code amount} 是滚轮格数，正数往下。
	 * <p>一格滚多远由 {@link #step} 定，默认 {@link #DEFAULT_STEP}，按行滚的界面可以自己改。
	 */
	public void wheel(double amount) {
		target += amount * step;
	}
	/**
	 * 画滑槽与滑块。滑块恒定用原色，不做悬停高亮。
	 *
	 * @param height  轨道高度，即可视区高度
	 * @param content 内容总高
	 */
	public void render(GuiGraphics gui, int x, int y, int height, int content) {
		if (!shown(height, content)) return;
		LogicGuiTextures.SCROLL.render(gui, x, y, WIDTH, height);
		var h = knobHeight(height, content);
		LogicGuiTextures.SCROLL_KNOB.render(gui, x, knobY(y, height, content, h), WIDTH, h);
	}
	/** 内容不超一屏时整条不画、也不响应。 */
	public boolean shown(int height, int content) {
		return content > height;
	}
	/** @return 滑块高度，内容不超一屏时返回 0。 */
	private static int knobHeight(int height, int content) {
		return content > height ? Math.max(MIN_KNOB_H, height * height / content) : 0;
	}
	/** @return 滑块顶端在轨道内的 y。 */
	private int knobY(int y, int height, int content, int knobH) {
		return y + (int) ((height - knobH) * (scroll / (content - height)));
	}
	/**
	 * 按下。点在滑块上是抓起它接着拖（不跳位置），点在轨道空白上是翻一页。
	 *
	 * @return 事件是否处理掉了；鼠标不在滚动条上时返回 {@code false}
	 */
	public boolean mousePressed(double mouseX, double mouseY, int x, int y, int height, int content) {
		if (mouseX < x || mouseX >= x + WIDTH || !shown(height, content)) return false;
		var knobH = knobHeight(height, content);
		var knobY = knobY(y, height, content, knobH);
		if (knobY <= mouseY && mouseY < knobY + knobH) {
			dragging = true;
			grab = mouseY - knobY;
		} else scrollBy(mouseY < knobY ? -height : height);
		return true;
	}
	/** 相对滚一段，拖动时的自动滚动用。 */
	public void scrollBy(double delta) {
		target += delta;
	}
	/**
	 * 拖动中：按抓取时的偏移还原滑块位置，鼠标在哪儿按下就从哪儿接着拖。
	 *
	 * @return 事件是否处理掉了；当前没在拖动时返回 {@code false}
	 */
	public boolean mouseDragged(double mouseY, int y, int height, int content) {
		if (!dragging || !shown(height, content)) return false;
		var travel = height - knobHeight(height, content);
		if (travel <= 0) return false;
		// 直接落位，不给插值留余地：滑块就是按 scroll 画的，写进目标的话鼠标走了滑块还在追，看着就是不跟手
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
