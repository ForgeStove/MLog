package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/**
 * 界面字体，配置见 {@code assets/mlog/font/main.json}。
 * <p>使用 TTF provider 由 FreeType 动态生成字形，避免原版位图字体放大后模糊。
 * 文本只需应用本字体样式，{@code Font} 会通过 {@code Style#getFont} 选择字体进行渲染和测宽。
 */
@OnlyIn(Dist.CLIENT)
public final class LogicFont {
	/** 字体资源位置。 */
	public static final ResourceLocation ID = getMLogRes("main");
	/**
	 * 描边环的深灰分量（{@code 0x3f3f3f}）。
	 * 字形烘焙时使用该颜色，绘制时再乘以正文色 tint。
	 */
	public static final int OUTLINE_FACTOR = 0x3F;
	/**
	 * 膨胀字形在 {@link #ID} 字体中的码点偏移，需与 {@code assets/mlog/font/main.json} 中
	 * {@code mlog:outlined} provider 的 {@code offset} 保持一致。
	 * <p>取 {@code 0xF0000}（15 号平面私用区 A），避免与正常文字冲突。
	 */
	public static final int OUTLINE_OFFSET = 0xF0000;
	/** 颜色标记：{@code [name]…[]}，当前仅支持 accent。 */
	private static final Map<String, Integer> TAGS = Map.of("accent", LogicColors.ACCENT);
	/** 文本缓存，按语言实例作废：界面每帧都在取同一批 key。 */
	private static final Map<String, Component> TEXTS = new HashMap<>(), LITERALS = new HashMap<>();
	private static @Nullable Language language;
	/** @return 由 {@code color} 缩放得到的描边色，用于下划线外框等。 */
	public static int outlineColor(int color) {
		return (color >> 16 & 0xFF) * OUTLINE_FACTOR / 0xFF << 16
			| (color >> 8 & 0xFF) * OUTLINE_FACTOR / 0xFF << 8
			| (color & 0xFF) * OUTLINE_FACTOR / 0xFF
			| 0xFF000000;
	}
	/**
	 * 返回与 {@code text} 结构相同、所有码点增加 {@link #OUTLINE_OFFSET} 的副本，样式保持不变。
	 * <p>描边字形是同一字体中的第二套，通过码点区分。
	 * 翻译组件需先展开再偏移。
	 */
	public static Component outlineShift(Component text) {
		var contents = text.getContents();
		// 外层保留原样式，确保偏移后的码点仍由同一字体解析。
		var out = Component.empty().setStyle(text.getStyle());
		// 纯文本直接偏移；翻译组件先展开再偏移，否则会退回普通字形。
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
	/** @return 每个码点增加 {@link #OUTLINE_OFFSET} 的文本；代理对按码点处理。 */
	private static String shiftCodePoints(String text) {
		var out = new StringBuilder(text.length());
		text.codePoints().forEach(c -> out.appendCodePoint(c + OUTLINE_OFFSET));
		return out.toString();
	}
	/** @return 带样式的说明文本；语言文件中不存在该 key 时返回 {@code null}。 */
	public static @Nullable Component tip(String key) {
		return Language.getInstance().has(key) ? text(key) : null;
	}
	/**
	 * 返回使用界面字体的本地化文本；key 不存在时按纯文本处理。
	 * <p>直接使用 {@code translatable} 会在缺失 key 时按格式串解析，可能改变 {@code %} 等字符。
	 */
	public static Component text(String key, Object... args) {
		// literal 分支无需 args：key 即最终文本。
		if (args.length > 0) return withFont(Language.getInstance().has(key) ? Component.translatable(key, args) : Component.literal(key));
		refresh();
		return TEXTS.computeIfAbsent(key, k -> withFont(Language.getInstance().has(k) ? Component.translatable(k) : Component.literal(k)));
	}
	/** @return 应用界面字体样式的文本。 */
	private static Component withFont(MutableComponent text) {
		return text.withStyle(style -> style.withFont(ID));
	}
	/** 语言实例变化后清空缓存：某个 key 有没有译文本就按语言判定。 */
	private static void refresh() {
		if (Language.getInstance() == language) return;
		language = Language.getInstance();
		TEXTS.clear();
		LITERALS.clear();
	}
	/** @return 使用界面字体的纯文本。 */
	public static Component literal(String text) {
		refresh();
		return LITERALS.computeIfAbsent(text, t -> withFont(Component.literal(t)));
	}
	/**
	 * 解析 {@code [accent]…[]} 标记并返回界面字体文本。
	 * <p>无法识别的方括号按普通文字处理。
	 */
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
			Integer next = tag.isEmpty() ? null : TAGS.get(tag);
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
	/** @return 带颜色的片段；{@code color} 为 null 时使用默认色。 */
	private static Component part(String text, @Nullable Integer color) {
		var part = Component.literal(text);
		return color == null ? part : part.withStyle(style -> style.withColor(color));
	}
	/** 绘制一行界面字体文本，不启用原版默认阴影。 */
	public static void draw(GuiGraphics gui, Component text, int x, int y, int color) {
		gui.drawString(mc.font, text, x, y, color, false);
	}
	/** 同上，接受已按样式排序的文本。 */
	public static void draw(GuiGraphics gui, FormattedCharSequence text, int x, int y, int color) {
		gui.drawString(mc.font, text, x, y, color, false);
	}
	/** 绘制一行水平居中的界面字体文本。 */
	public static void drawCentered(GuiGraphics gui, Component text, int centerX, int y, int color) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(centerOffset(text), 0F, 0F);
		gui.drawString(mc.font, text, centeredX(text, centerX), y, color, false);
		pose.popPose();
	}
	/**
	 * @return 居中绘制时的额外平移量。
	 * 	<p>{@code Font.width} 为整数，宽度为奇数时需补偿半像素。
	 */
	private static float centerOffset(Component text) {
		return (width(text) & 1) == 0 ? 0F : -0.5F;
	}
	/** @return 居中绘制时的整数起点，配合 {@link #centerOffset} 使用。 */
	private static int centeredX(Component text, int centerX) {
		return centerX - width(text) / 2;
	}
	/**
	 * @return 文本宽度。
	 * 	<p>使用 {@code getVisualOrderText}，确保测量与绘制基于同一展开结果。
	 */
	public static int width(Component text) {
		return mc.font.width(text.getVisualOrderText());
	}
	/** 绘制一行水平居中、带描边的文本。 */
	public static void drawOutlinedCentered(GuiGraphics gui, Component text, int centerX, int y, int color) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(centerOffset(text), 0F, 0F);
		drawOutlined(gui, text, centeredX(text, centerX), y, color);
		pose.popPose();
	}
	/**
	 * 绘制一行带描边的文本。
	 * <p>描边由膨胀字形自带，一次绘制即包含描边与正文。
	 */
	public static void drawOutlined(GuiGraphics gui, Component text, int x, int y, int color) {
		gui.drawString(mc.font, outlineShift(text), x, y, color, false);
	}
}
