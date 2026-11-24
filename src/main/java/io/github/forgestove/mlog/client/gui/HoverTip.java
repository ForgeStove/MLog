package io.github.forgestove.mlog.client.gui;
import com.demonwav.mcdev.annotations.Translatable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static io.github.forgestove.mlog.client.gui.LogicColors.ACCENT;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
public class HoverTip {
	private static final ResourceLocation FONT = getMLogRes("tip");
	private static final int TICKS = 11, FADE = 5;
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
	public static void tick(Post ignoredEvent) {
		if (hoverTicks > 0) hoverTicks--;
	}
	public static Component text(@Translatable(foldMethod = true) String key) {
		return Component.translatable(key).withStyle(style -> style.withFont(FONT));
	}
	public static Component withTipFont(Component text) {
		return text.copy().withStyle(style -> style.withFont(FONT));
	}
	public static void show(List<Component> tip) {
		show(tip, 0, 0);
	}
	public static void show(List<Component> tip, int x, int y) {
		if (mc.screen != null) return;
		hoverTicks = hoverTicks == 0 ? TICKS : Math.max(hoverTicks, TICKS - FADE);
		HoverTip.tip = tip;
		deltaX = x;
		deltaY = y;
	}
}
