package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static io.github.forgestove.mlog.client.gui.LogicColors.ACCENT;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/**
 * 屏幕底部的悬停提示条。
 * <p>前五个滴答渐显、后五个渐隐，故计时需每滴答经 {@link #show} 刷新方能延续；
 * 调用须排在 {@link #tick} 之前，否则同一滴答内亮度先减一格。
 */
@OnlyIn(Dist.CLIENT)
public final class HoverTip {
	/**
	 * 提示用的字体，见 {@code assets/mlog/font/tip.json}。
	 * <p>与界面正文为同一套 ttf，{@code size} 按原版字体行高取；正文那份为 {@code 7}，
	 * 小于原版字体，置于屏幕底部时不易辨读。
	 */
	private static final ResourceLocation FONT = getMLogRes("tip");
	/** 提示停留的滴答数与渐入渐出的分界。 */
	private static final int TICKS = 11, FADE = 5;
	/** 离屏幕底部的距离与行距，单位是像素。 */
	private static final int BOTTOM = 75, LINE_H = 12;
	private static int hoverTicks;
	private static @Nullable List<Component> tip;
	private static int deltaX, deltaY;
	public static void register(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.HOTBAR, getMLogRes("hover_tip"), HoverTip::render);
	}
	public static void render(GuiGraphics gui, DeltaTracker ignoredDelta) {
		if (mc.options.hideGui || hoverTicks == 0 || tip == null) return;
		var x = gui.guiWidth() / 2 + deltaX;
		var y = gui.guiHeight() - BOTTOM - tip.size() * LINE_H + deltaY;
		var fade = hoverTicks > FADE ? (TICKS - hoverTicks) / (float) FADE : Math.min(1, hoverTicks / (float) FADE);
		var color = ARGB32.color((int) (fade * 255), ACCENT);
		for (var line : tip) {
			LogicFont.drawCentered(gui, line, x, y, color);
			y += LINE_H;
		}
	}
	/** 每滴答减一，减至零后不再绘制。 */
	public static void tick(Post ignoredEvent) {
		if (hoverTicks > 0) hoverTicks--;
	}
	/** @return 用提示字号渲染的本地化文本。 */
	public static Component text(String key) {
		return Component.translatable(key).withStyle(style -> style.withFont(FONT));
	}
	/** 居中显示这组提示行。 */
	public static void show(List<Component> tip) {
		show(tip, 0, 0);
	}
	/**
	 * 同上，另可指定像素级偏移以避开遮挡。
	 * <p>界面打开时不提示：此时玩家注意力在界面上。
	 */
	public static void show(List<Component> tip, int x, int y) {
		if (mc.screen != null) return;
		// 已在显示时续至半亮以上而非从零重新渐显，避免连续触发时闪烁
		hoverTicks = hoverTicks == 0 ? TICKS : Math.max(hoverTicks, TICKS - FADE);
		HoverTip.tip = tip;
		deltaX = x;
		deltaY = y;
	}
}
