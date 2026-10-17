package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.LogicFont;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 自绘悬停提示：黑底描边文字；锚点在出现时定下，出现与收起带缩放淡入淡出。 */
public class LogicTooltip {
	/** 内边距、鼠标距离、边缘留距、行高；前三个由 4 / 15 / 19 / 7 按 0.4 折算。 */
	private static final int PAD = 2, OFFSET_X = 6, OFFSET_Y = 8, EDGE = 3, LINE_H = 8;
	/** 物品经 {@code GuiGraphics.renderItem} 绘于 z=150，须置于其上。 */
	private static final float Z = 200;
	/** 出现 0.1 秒、收起 0.2 秒；出现时缩放由 0.05 到 1、透明度由 0.2 到 1。 */
	private static final float SHOW_TIME = 0.1F, HIDE_TIME = 0.2F, SCALE_FROM = 0.05F, ALPHA_FROM = 0.2F;
	/** 鼠标锚点与活动范围，动画进度 0 收起、1 显示完整。 */
	private static @Nullable Component text;
	private static int mouseX, mouseY, boundW, boundH;
	private static float progress;
	/** 上层界面正在重画父界面：此间父界面不出提示。 */
	private static boolean parentRendering;
	/** 淡出期间也须每帧调用；同一时刻只能一个界面上报，否则互相重置进度。 */
	public static void render(GuiGraphics gui, @Nullable Component text, int mouseX, int mouseY, int boundW, int boundH) {
		if (text != null) {
			// 更换提示即重置进度并定下锚点；同一提示显示期间锚点不动
			if (LogicTooltip.text == null || !text.getString().equals(LogicTooltip.text.getString())) {
				progress = 0;
				LogicTooltip.mouseX = mouseX;
				LogicTooltip.mouseY = mouseY;
			}
			LogicTooltip.text = text;
			LogicTooltip.boundW = boundW;
			LogicTooltip.boundH = boundH;
		} else if (LogicTooltip.text == null) return;
		var step = mc.getTimer().getRealtimeDeltaTicks() / 20F / (text == null ? HIDE_TIME : SHOW_TIME);
		progress = Math.clamp(progress + (text == null ? -step : step), 0, 1);
		var tip = LogicTooltip.text;
		if (tip == null || progress <= 0) {
			LogicTooltip.text = null;
			return;
		}
		draw(gui, tip, smoother(progress));
	}
	/** 重画父界面期间屏蔽提示：两界同一帧各报一次会叠出缩小副本。 */
	public static void whileRenderingParent(Runnable render) {
		parentRendering = true;
		try {
			render.run();
		} finally {
			parentRendering = false;
		}
	}
	public static boolean available() {
		return !parentRendering;
	}
	private static void draw(GuiGraphics gui, Component text, float eased) {
		// 换行由文本自身给定，不做自动折行
		var lines = text.getString().split("\n", -1);
		var w = 0;
		for (var line : lines) w = Math.max(w, LogicFont.width(LogicFont.rich(line)));
		w += PAD * 2;
		var h = lines.length * LINE_H + PAD * 2;
		// 优先放指针右下；下方放不下就翻到右上
		var tx = mouseX + OFFSET_X;
		var ty = mouseY + OFFSET_Y;
		if (ty + h > boundH - EDGE) ty = mouseY - OFFSET_Y - h;
		// 越出活动范围即钳回
		tx = Math.max(tx, EDGE);
		tx = Math.min(tx, boundW - EDGE - w);
		ty = Math.min(ty, boundH - EDGE - h);
		var scale = SCALE_FROM + (1 - SCALE_FROM) * eased;
		var alpha = ALPHA_FROM + (1 - ALPHA_FROM) * eased;
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(0F, 0F, Z);
		// 缩放以鼠标点为轴：提示位置按鼠标点计算
		pose.translate(mouseX, mouseY, 0F);
		pose.scale(scale, scale, 1F);
		pose.translate(-mouseX, -mouseY, 0F);
		gui.fill(tx, ty, tx + w, ty + h, withAlpha(TIP_BG, alpha));
		for (var i = 0; i < lines.length; i++)
			LogicFont.drawOutlined(gui, LogicFont.rich(lines[i]), tx + PAD, ty + PAD + i * LINE_H, withAlpha(TIP_TEXT, alpha));
		pose.popPose();
	}
	private static float smoother(float t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}
	private static int withAlpha(int color, float factor) {
		return (int) ((color >>> 24) * factor) << 24 | color & 0xFFFFFF;
	}
}
