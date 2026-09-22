package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
/** 自绘的悬停提示：黑底 + 描边文字。 */
@OnlyIn(Dist.CLIENT)
public final class LogicTooltip {
	/** 内边距、相对鼠标的间距、行高。内边距由 4 按 0.4 折算。 */
	private static final int PAD = 2, GAP = 8, LINE_H = 8;
	/** 提示的 z。物品经 {@code GuiGraphics.renderItem} 绘制于 z=150，需置于其上。 */
	private static final float Z = 200;
	/**
	 * @param boundW 横向活动范围，超出右边界时回推
	 * @param boundH 纵向活动范围
	 */
	public static void render(GuiGraphics gui, Component text, int mouseX, int mouseY, int boundW, int boundH) {
		// 说明可含多行，按 \n 拆分逐行绘制；换行由文本自身指定，不做自动折行
		var lines = text.getString().split("\n", -1);
		var w = 0;
		for (var line : lines) w = Math.max(w, LogicFont.width(LogicFont.rich(line)));
		w += PAD * 2;
		var h = lines.length * LINE_H + PAD * 2;
		// 跟着鼠标走，贴到屏幕外就推回来
		var tx = Math.clamp(mouseX + GAP, 0, Math.max(0, boundW - w));
		var ty = Math.clamp(mouseY + GAP, 0, Math.max(0, boundH - h));
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(0F, 0F, Z);
		gui.fill(tx, ty, tx + w, ty + h, CARD_BG);
		for (var i = 0; i < lines.length; i++)
			LogicFont.drawOutlined(gui, LogicFont.rich(lines[i]), tx + PAD, ty + PAD + i * LINE_H, TEXT);
		pose.popPose();
	}
}
