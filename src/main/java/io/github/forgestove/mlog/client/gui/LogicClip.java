package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.*;
/**
 * 裁剪区：被裁掉的部分既不显示，也不该响应鼠标。
 * <p>鼠标坐标经 {@link #mouseX}/{@link #mouseY} 换算后再传给区内内容，落在区外时给哨兵值，
 * 既有的悬停判定自然不成立，各控件无须各自判边界。
 */
@OnlyIn(Dist.CLIENT)
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
	/** 开启裁剪，用完调 {@link #end}。 */
	public static LogicClip begin(GuiGraphics gui, int left, int top, int right, int bottom) {
		return new LogicClip(gui, left, top, right, bottom);
	}
	/** 收起裁剪。 */
	public void end() {
		gui.disableScissor();
	}
	/** @return 落在区内的鼠标 x，区外为 {@link #OUTSIDE}。 */
	public int mouseX(int mouseX) {
		return mouseX >= left && mouseX < right ? mouseX : OUTSIDE;
	}
	/** @return 落在区内的鼠标 y，区外为 {@link #OUTSIDE}。 */
	public int mouseY(int mouseY) {
		return mouseY >= top && mouseY < bottom ? mouseY : OUTSIDE;
	}
}
