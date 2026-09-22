package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 自绘的悬停提示：黑底 + 描边文字。
	 * <p>位置放在指针右下方，底下放不下才翻到右上；锚点在提示出现那一刻定下，之后不跟着指针跑。
	 * 出现与收起都带缩放淡入淡出。
 */
@OnlyIn(Dist.CLIENT)
public final class LogicTooltip {
	/** 内边距、离鼠标的距离、离屏幕边缘的留距、行高。前三个分别由 4 / 15 / 19 / 7 按 0.4 折算。 */
	private static final int PAD = 2, OFFSET_X = 6, OFFSET_Y = 8, EDGE = 3, LINE_H = 8;
	/** 提示的 z。物品经 {@code GuiGraphics.renderItem} 绘制于 z=150，需置于其上。 */
	private static final float Z = 200;
	/** 出现用 0.1 秒、收起用 0.2 秒；出现时缩放由 0.05 涨到 1、透明度由 0.2 涨到 1，收起时缩回去。 */
	private static final float SHOW_TIME = 0.1F, HIDE_TIME = 0.2F, SCALE_FROM = 0.05F, ALPHA_FROM = 0.2F;
	/** 正在显示的提示、鼠标锚点与活动范围，以及动画进度（0 收起、1 显示完整）。 */
	private static @Nullable Component text;
	private static int mouseX, mouseY, boundW, boundH;
	private static float progress;
	/**
	 * 上报并绘制本帧的悬停提示。{@code text} 为 {@code null} 表示本帧没悬停，上一个提示会缩着淡出。
	 * <p>每帧都要调：内容画完之后才轮到它，淡出那几帧也得有人画。
	 * <p>同一时刻只有一个界面在报——对话框开着时画布在底下也会被重画一遍，那种场合它不报，
	 * 否则两边每帧互相重置进度，淡入永远走不完。
	 */
	public static void render(GuiGraphics gui, @Nullable Component text, int mouseX, int mouseY, int boundW, int boundH) {
		if (text != null) {
			// 换了提示就从零开始长，并在这一刻定下锚点；同一个提示显示期间锚点不动
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
	/** @return 两端慢、中间快的进度曲线，淡入淡出都用它。 */
	private static float smoother(float t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}
	/** 按当前进度算出的透明度与缩放绘制提示。 */
	private static void draw(GuiGraphics gui, Component text, float eased) {
		// 说明可含多行，按 \n 拆分逐行绘制；换行由文本自身指定，不做自动折行
		var lines = text.getString().split("\n", -1);
		var w = 0;
		for (var line : lines) w = Math.max(w, LogicFont.width(LogicFont.rich(line)));
		w += PAD * 2;
		var h = lines.length * LINE_H + PAD * 2;
		// 优先放指针右下：提示的上边缘落在指针下方；底下放不下就翻到右上
		var tx = mouseX + OFFSET_X;
		var ty = mouseY + OFFSET_Y;
		if (ty + h > boundH - EDGE) ty = mouseY - OFFSET_Y - h;
		// 越出活动范围就往回推，先保证看得见
		tx = Math.max(tx, EDGE);
		tx = Math.min(tx, boundW - EDGE - w);
		ty = Math.min(ty, boundH - EDGE - h);
		var scale = SCALE_FROM + (1 - SCALE_FROM) * eased;
		var alpha = ALPHA_FROM + (1 - ALPHA_FROM) * eased;
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(0F, 0F, Z);
		// 缩放围绕鼠标那一点：位置本来就贴着鼠标算，缩起来才像从指针上长出来
		pose.translate(mouseX, mouseY, 0F);
		pose.scale(scale, scale, 1F);
		pose.translate(-mouseX, -mouseY, 0F);
		gui.fill(tx, ty, tx + w, ty + h, withAlpha(TIP_BG, alpha));
		for (var i = 0; i < lines.length; i++)
			LogicFont.drawOutlined(gui, LogicFont.rich(lines[i]), tx + PAD, ty + PAD + i * LINE_H, withAlpha(TIP_TEXT, alpha));
		pose.popPose();
	}
	/** @return 把颜色的透明度乘上 {@code factor} 之后的颜色。 */
	private static int withAlpha(int color, float factor) {
		return (int) ((color >>> 24) * factor) << 24 | color & 0xFFFFFF;
	}
}
