package io.github.forgestove.mlog.client.gui;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/** 界面字体，配置见 {@code assets/mlog/font/font.json}；文本只需应用本字体样式，{@code Font} 会通过 {@code Style#getFont} 选择字体进行渲染和测宽。 */
public class LogicFont {
	public static final ResourceLocation ID = getMLogRes("font");
	/** 界面字体的行高。 */
	public static final int LINE_H = 8;
	/** 描边环的深灰分量（{@code 0x3f3f3f}），字形烘焙时即使用该颜色，绘制时再乘以正文色。 */
	public static final int OUTLINE_FACTOR = 0x3F;
	/**
	 * 膨胀字形在 {@link #ID} 字体中的码点偏移，需与 {@code assets/mlog/font/font.json} 中 {@code mlog:outlined} provider 的 {@code offset} 保持一致；取 {@code
	 * 0xF0000}（15 号平面私用区 A）以避免与正常文字冲突。
	 */
	public static final int OUTLINE_OFFSET = 0xF0000;
	/** 颜色标记表：{@code [name]…[]}，查表使用小写；{@code grey} 拼法一并收录。 */
	private static final Map<String, Integer> TAGS = Map.ofEntries(
		Map.entry("clear", 0x00000000),
		Map.entry("black", 0xFF000000),
		Map.entry("white", 0xFFFFFFFF),
		Map.entry("lightgray", 0xFFBFBFBF),
		Map.entry("lightgrey", 0xFFBFBFBF),
		Map.entry("gray", 0xFF7F7F7F),
		Map.entry("grey", 0xFF7F7F7F),
		Map.entry("darkgray", 0xFF3F3F3F),
		Map.entry("darkgrey", 0xFF3F3F3F),
		Map.entry("blue", 0xFF4169E1),
		Map.entry("navy", 0xFF000080),
		Map.entry("royal", 0xFF4169E1),
		Map.entry("slate", 0xFF708090),
		Map.entry("sky", 0xFF87CEEB),
		Map.entry("cyan", 0xFF00FFFF),
		Map.entry("teal", 0xFF008080),
		Map.entry("green", 0xFF38D667),
		Map.entry("acid", 0xFF7FFF00),
		Map.entry("lime", 0xFF32CD32),
		Map.entry("forest", 0xFF228B22),
		Map.entry("olive", 0xFF6B8E23),
		Map.entry("yellow", 0xFFFFFF00),
		Map.entry("gold", 0xFFFFD700),
		Map.entry("goldenrod", 0xFFDAA520),
		Map.entry("orange", 0xFFFFA500),
		Map.entry("brown", 0xFF8B4513),
		Map.entry("tan", 0xFFD2B48C),
		Map.entry("brick", 0xFFB22222),
		Map.entry("red", 0xFFE55454),
		Map.entry("scarlet", 0xFFFF341C),
		Map.entry("crimson", 0xFFDC143C),
		Map.entry("coral", 0xFFFF7F50),
		Map.entry("salmon", 0xFFFA8072),
		Map.entry("pink", 0xFFFF69B4),
		Map.entry("magenta", 0xFFFF00FF),
		Map.entry("purple", 0xFFA020F0),
		Map.entry("violet", 0xFFEE82EE),
		Map.entry("maroon", 0xFFB03060),
		Map.entry("accent", LogicColors.ACCENT),
		Map.entry("unlaunched", 0xFF8982ED),
		Map.entry("highlight", 0xFFFFE0A5),
		Map.entry("stat", LogicColors.ACCENT),
		Map.entry("negstat", 0xFFE55454)
	);
	/** 文本缓存，随语言实例失效。 */
	private static final Map<String, Component> TEXTS = new HashMap<>(), LITERALS = new HashMap<>();
	private static @Nullable Language language;
	public static int outlineColor(int color) {
		return (color >> 16 & 0xFF) * OUTLINE_FACTOR / 0xFF << 16
			| (color >> 8 & 0xFF) * OUTLINE_FACTOR / 0xFF << 8
			| (color & 0xFF) * OUTLINE_FACTOR / 0xFF
			| 0xFF000000;
	}
	/** 描边字形是同一字体中的第二套，通过码点区分。 */
	public static Component outlineShift(Component text) {
		var contents = text.getContents();
		// 外层保留原样式，确保偏移后的码点仍由同一字体解析。
		var out = Component.empty().setStyle(text.getStyle());
		// 翻译组件需先展开再偏移，否则会退回普通字形。
		if (contents instanceof PlainTextContents plain) out.append(Component.literal(shiftCodePoints(plain.text())));
		else contents.visit(
			(style, part) -> {
				// visit 提供的样式已合并组件样式，字体仍保留。
				var piece = Component.literal(shiftCodePoints(part));
				piece.setStyle(style);
				out.append(piece);
				return Optional.empty();
			}, text.getStyle()
		);
		for (var sibling : text.getSiblings()) out.append(outlineShift(sibling));
		return out;
	}
	/** 代理对按码点处理。 */
	private static String shiftCodePoints(String text) {
		var out = new StringBuilder(text.length());
		text.codePoints().forEach(c -> out.appendCodePoint(c + OUTLINE_OFFSET));
		return out.toString();
	}
	/** 语言文件中不存在该 key 时返回 {@code null}。 */
	public static @Nullable Component tip(String key) {
		return Language.getInstance().has(key) ? text(key) : null;
	}
	/** key 不存在时按纯文本处理；直接使用 {@code translatable} 会按格式串解析缺失的 key，可能改变 {@code %} 等字符。 */
	public static Component text(String key, Object... args) {
		if (args.length > 0) return withFont(Language.getInstance().has(key) ? Component.translatable(key, args) : Component.literal(key));
		refresh();
		return TEXTS.computeIfAbsent(key, k -> withFont(Language.getInstance().has(k) ? Component.translatable(k) : Component.literal(k)));
	}
	private static Component withFont(MutableComponent text) {
		return text.withStyle(style -> style.withFont(ID));
	}
	/** 语言实例变化后清空缓存：key 是否有译文本依语言判定。 */
	private static void refresh() {
		if (Language.getInstance() == language) return;
		language = Language.getInstance();
		TEXTS.clear();
		LITERALS.clear();
	}
	public static Component literal(String text) {
		refresh();
		return LITERALS.computeIfAbsent(text, t -> withFont(Component.literal(t)));
	}
	/** 颜色标记为 {@code [名字]…[]}，无法识别的方括号按普通文字处理。 */
	public static Component rich(String text) {
		var out = Component.empty();
		Integer color = null;
		var from = 0;
		var i = 0;
		while (i < text.length()) {
			var open = text.indexOf('[', i);
			if (open < 0) break;
			var close = text.indexOf(']', open);
			if (close < 0) break;
			var tag = text.substring(open + 1, close);
			// 空标记结束强调；未知标记保留原样，继续向后扫描。
			Integer next = tag.isEmpty() ? null : TAGS.get(tag.toLowerCase(Locale.ROOT));
			if (next == null && !tag.isEmpty()) {
				i = open + 1;
				continue;
			}
			if (open > from) out.append(part(text.substring(from, open), color));
			color = next;
			from = close + 1;
			i = from;
		}
		if (from < text.length()) out.append(part(text.substring(from), color));
		return out.withStyle(style -> style.withFont(ID));
	}
	/** {@code color} 为 null 时使用默认色。 */
	private static Component part(String text, @Nullable Integer color) {
		var part = Component.literal(text);
		return color == null ? part : part.withStyle(style -> style.withColor(color));
	}
	/** 不启用原版默认阴影。 */
	public static void draw(GuiGraphics gui, Component text, int x, int y, int color) {
		gui.drawString(mc.font, text, x, y, color, false);
	}
	/** 文本需已按样式排序。 */
	public static void draw(GuiGraphics gui, FormattedCharSequence text, int x, int y, int color) {
		gui.drawString(mc.font, text, x, y, color, false);
	}
	public static void drawCentered(GuiGraphics gui, Component text, int centerX, int y, int color) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(centerOffset(text), 0F, 0F);
		gui.drawString(mc.font, text, centeredX(text, centerX), y, color, false);
		pose.popPose();
	}
	/** {@code Font.width} 为整数，宽度为奇数时需补偿半像素。 */
	private static float centerOffset(Component text) {
		return (width(text) & 1) == 0 ? 0F : -0.5F;
	}
	/** 配合 {@link #centerOffset} 使用。 */
	private static int centeredX(Component text, int centerX) {
		return centerX - width(text) / 2;
	}
	/** 使用 {@code getVisualOrderText}，确保测量与绘制基于同一展开结果。 */
	public static int width(Component text) {
		return mc.font.width(text.getVisualOrderText());
	}
	/** 超出可用宽度时左右往返滚动并裁掉两端，否则居中显示。 */
	public static void drawScrollingCentered(GuiGraphics gui, Component text, int left, int right, int y, int color) {
		var width = width(text);
		var available = Math.max(0, right - left);
		if (width <= available) {
			drawCentered(gui, text, (left + right) / 2, y, color);
			return;
		}
		var overflow = width - available;
		var period = Math.max(overflow * 0.5, 3.0);
		var seconds = Util.getMillis() / 1000.0;
		var wave = Math.sin(Math.PI / 2 * Math.cos(Math.PI * 2 * seconds / period)) / 2.0 + 0.5;
		var offset = (int) Mth.lerp(wave, 0.0, overflow);
		var clip = LogicClip.begin(gui, left, y, right, y + LINE_H);
		try {
			draw(gui, text, left - offset, y, color);
		} finally {
			clip.end();
		}
	}
	public static void drawOutlinedCentered(GuiGraphics gui, Component text, int centerX, int y, int color) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(centerOffset(text), 0F, 0F);
		drawOutlined(gui, text, centeredX(text, centerX), y, color);
		pose.popPose();
	}
	/** 描边由膨胀字形自带，一次绘制即包含描边与正文。 */
	public static void drawOutlined(GuiGraphics gui, Component text, int x, int y, int color) {
		gui.drawString(mc.font, outlineShift(text), x, y, color, false);
	}
}
