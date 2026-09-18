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
 * 屏幕底部那行悬停提示，位置与淡入淡出照抄 Create 的 {@code ValueSettingsClient#showHoverTip}。
 * <p>前五个滴答渐显、后五个渐隐，所以计时每滴答都得重新 {@link #show} 一次才续得住；
 * 调用要排在 {@link #tick} 之前，否则当滴答就先掉一格亮度。
 */
@OnlyIn(Dist.CLIENT)
public final class HoverTip {
	/**
	 * 提示用的字体，见 {@code assets/mlog/font/tip.json}。
	 * <p>和界面正文是同一套 ttf，只是 {@code size} 按原版字体的行高取——正文那份是 {@code 7}，
	 * 比原版字体小一圈，飘在屏幕底部那片空处读起来费劲。
	 */
	private static final ResourceLocation FONT = getMLogRes("tip");
	/** 提示停留的滴答数与渐入渐出的分界，取值同 Create。 */
	private static final int TICKS = 11, FADE = 5;
	/** 离屏幕底部的距离与行距，单位是像素，取值同 Create。 */
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
	/** 每滴答退一格，退到零就自己消失。 */
	public static void tick(Post ignoredEvent) {
		if (hoverTicks > 0) hoverTicks--;
	}
	/** @return 用提示字号渲染的本地化文本。 */
	public static Component text(String key) {
		return Component.translatable(key).withStyle(style -> style.withFont(FONT));
	}
	/** 显示这几行提示，居中。 */
	public static void show(List<Component> tip) {
		show(tip, 0, 0);
	}
	/**
	 * 同上，额外给一个像素级的偏移，用来避开挡着的东西。
	 * <p>界面开着时不提示：这时候玩家的注意力在界面上，Create 那边也是这么判的。
	 */
	public static void show(List<Component> tip, int x, int y) {
		if (mc.screen != null) return;
		// 已经在显示就把它续到半亮以上，而不是从零重新渐显——连点时才不会一闪一闪
		hoverTicks = hoverTicks == 0 ? TICKS : Math.max(hoverTicks, TICKS - FADE);
		HoverTip.tip = tip;
		deltaX = x;
		deltaY = y;
	}
}
