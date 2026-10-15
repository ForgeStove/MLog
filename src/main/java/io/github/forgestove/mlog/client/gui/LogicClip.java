package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
/** 裁剪区：被裁掉的部分既不显示也不该响应鼠标，鼠标坐标须先经 {@link #mouseX}/{@link #mouseY} 换算，区外得到哨兵值。 */
public final class LogicClip {
	/** 鼠标在区外时的坐标：取 int 最小值，任何矩形都不包含它。 */
	public static final int OUTSIDE = Integer.MIN_VALUE;
	private final GuiGraphics gui;
	private final int left, top, right, bottom;
	private LogicClip(GuiGraphics gui, int left, int top, int right, int bottom) {
		this.gui = gui;
		this.left = left;
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		gui.enableScissor(left, top, right, bottom);
	}
	/** 用完调 {@link #end}。 */
	public static LogicClip begin(GuiGraphics gui, int left, int top, int right, int bottom) {
		return new LogicClip(gui, left, top, right, bottom);
	}
	public void end() {
		gui.disableScissor();
	}
	/** 区外返回 {@link #OUTSIDE}。 */
	public int mouseX(int mouseX) {
		return mouseX >= left && mouseX < right ? mouseX : OUTSIDE;
	}
	/** 区外返回 {@link #OUTSIDE}。 */
	public int mouseY(int mouseY) {
		return mouseY >= top && mouseY < bottom ? mouseY : OUTSIDE;
	}
}
