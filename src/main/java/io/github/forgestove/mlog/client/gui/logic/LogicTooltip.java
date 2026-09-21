package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
/**
 * 自绘的悬停提示：黑底 + 描边文字。
 * <p>不走 {@code Screen} 那套提示是因为它的样式改不了，跟界面其余部分对不上。
 */
@OnlyIn(Dist.CLIENT)
public final class LogicTooltip {
	/** 内边距、跟鼠标的间距、行高。内边距按 0.4 折算自 4。 */
	private static final int PAD = 2, GAP = 8, LINE_H = 8;
	/** 提示的 z。物品是通过 {@code GuiGraphics.renderItem} 画的，它在 z=150 那一层，得抬到上面去。 */
	private static final float Z = 200;
	/**
	 * @param boundW 提示横向的活动范围，贴出右边界就推回来
	 * @param boundH 纵向同理
	 */
	public static void render(GuiGraphics gui, Component text, int mouseX, int mouseY, int boundW, int boundH) {
		// 说明可能有多行，按 \n 拆开逐行画；换行是手写的，不用自动折行
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
