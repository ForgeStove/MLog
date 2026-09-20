package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/**
 * 界面字体，取自 Mindustry 的 {@code fonts/font.woff}，配置见 {@code assets/mlog/font/main.json}。
 * <p>MC 默认字体是位图（{@code ascii.png} + unifont），放大就糊；走 ttf provider 则和 Mindustry 一样
 * 由 FreeType 动态生成字形。
 * <p>文字只要带上这里的样式即可——{@code Font} 内部会按 {@code Style#getFont} 找到对应字体来渲染与测宽，
 * 所以不必换掉 {@code mc.font}。
 */
@OnlyIn(Dist.CLIENT)
public final class LogicFont {
	/** 字体资源位置。 */
	public static final ResourceLocation ID = getMLogRes("main");
	/**
	 * 描边环的深灰，取自 Mindustry 的 {@code Color.darkGray}（{@code 0x3f3f3f}）。
	 * <p>那边的描边是 FreeType 烘焙进字形的：环上的像素就是这个深灰，绘制时整个字形再被正文色
	 * tint 一遍，所以描边色实际是「正文色 × darkGray」——彩色的字自带同色调的暗边。
	 * 这边同样把深灰烙进字形，见 {@code OutlinedGlyphProvider#bake}。
	 */
	public static final int OUTLINE_FACTOR = 0x3F;
	/** @return {@code color} 对应的描边色。下划线外圈那条框用得上；文字本身的环烙在字形里，不走这里。 */
	public static int outlineColor(int color) {
		return (color >> 16 & 0xFF) * OUTLINE_FACTOR / 0xFF << 16
			| (color >> 8 & 0xFF) * OUTLINE_FACTOR / 0xFF << 8
			| (color & 0xFF) * OUTLINE_FACTOR / 0xFF
			| 0xFF000000;
	}
	/**
	 * 膨胀字形在 {@link #ID} 这个字体里的码点偏移，和 {@code assets/mlog/font/main.json} 里那条
	 * {@code mlog:outlined} provider 的 {@code offset} 必须一致。
	 * <p>取 0xF0000（15 号平面的私用区 A）：正常文字碰不到，排在它前面的 {@code ttf} provider 也认不了这一段。
	 */
	public static final int OUTLINE_OFFSET = 0xF0000;
	/**
	 * @return 与 {@code text} 结构相同、码点整体加上 {@link #OUTLINE_OFFSET} 的副本，样式原样保留。
	 * 	<p>带描边的字形是同一个字体里的第二套，靠码点区分；它把环和芯烙在自己身上，
	 * 	所以整段文字一次画完就自带描边，不用再铺第二层。
	 */
	public static Component outlineShift(Component text) {
		var contents = text.getContents();
		// 只有纯文本能整体挪码点，翻译组件、计分组件之类原样带过去（本来就靠 literal 拼的，碰不上）
		var out = contents instanceof PlainTextContents plain ? Component.literal(shiftCodePoints(plain.text())) : MutableComponent.create(contents);
		// 字体不动：偏移后的码点得回同一个字体里去找，换成别的字体就没有那套膨胀字形了
		out.setStyle(text.getStyle());
		for (var sibling : text.getSiblings()) out.append(outlineShift(sibling));
		return out;
	}
	/** @return 每个码点都加上 {@link #OUTLINE_OFFSET} 的文本；代理对按码点走，不会拆坏。 */
	private static String shiftCodePoints(String text) {
		var out = new StringBuilder(text.length());
		text.codePoints().forEach(c -> out.appendCodePoint(c + OUTLINE_OFFSET));
		return out.toString();
	}
	/**
	 * @return 用界面字体渲染的本地化文本，key 不在语言文件里则当纯文本画。
	 * 	<p>不能指望 {@code translatable} 兜底：找不到 key 时它会拿 key 当格式串跑一遍，
	 * 	{@code %%} 被吃成一个 {@code %}（{@code emod} 的符号正是 {@code %%}），
	 * 	落单的 {@code %} 则抛格式异常再原样退回——同一个符号换个写法结果就变。
	 * 	<p>物品名、自定义属性名这类普通字符串也会走这里，一并绕开。
	 */
	public static Component text(String key, Object... args) {
		// 走 literal 时 args 没有用武之地：key 就是最终要画的文字，没有占位符可填
		return Language.getInstance().has(key)
			? Component.translatable(key, args).withStyle(style -> style.withFont(ID))
			: literal(key);
	}
	/**
	 * @return 带样式的说明文本，语言文件里没有这条说明时返回 {@code null}。
	 * 	<p>对齐 Mindustry 的 {@code LCanvas#tooltip}：它也是先查 bundle 有没有这条再挂提示，
	 * 	所以没写说明的条目就是不给提示，而不是退化成显示 key。
	 */
	public static @Nullable Component tip(String key) {
		return Language.getInstance().has(key) ? text(key) : null;
	}
	/** @return 用界面字体渲染的纯文本。 */
	public static Component literal(String text) {
		return Component.literal(text).withStyle(style -> style.withFont(ID));
	}
	/** 颜色标记：Mindustry 用 {@code [名字]…[]} 包住要强调的片段，语句说明里只用到 accent 一种。 */
	private static final Map<String, Integer> TAGS = Map.of("accent", LogicColors.ACCENT);
	/**
	 * @return 解析了 {@code [accent]…[]} 标记的界面字体文本。
	 * 	<p>文案照 Mindustry 的 bundle 抄，标记也原样留着、颜色在这里映射，
	 * 	将来同步那边的文案就不用逐条改回来。认不出来的方括号当普通文字。
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
			// 空标记结束强调，认不出来的标记原样留着、继续往后找
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
	/** @return 带颜色的片段，{@code color} 为空就是默认色。 */
	private static Component part(String text, @Nullable Integer color) {
		var part = Component.literal(text);
		return color == null ? part : part.withStyle(style -> style.withColor(color));
	}
	/** 画一行界面字体文字。原版 {@code drawString} 默认带阴影，所以统一走这里。 */
	public static void draw(GuiGraphics gui, Component text, int x, int y, int color) {
		gui.drawString(mc.font, text, x, y, color, false);
	}
	/** 同上，接受已经按样式排好行的文本。 */
	public static void draw(GuiGraphics gui, FormattedCharSequence text, int x, int y, int color) {
		gui.drawString(mc.font, text, x, y, color, false);
	}
	/** 画一行水平居中的界面字体文字。 */
	public static void drawCentered(GuiGraphics gui, Component text, int centerX, int y, int color) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(centerOffset(text), 0F, 0F);
		gui.drawString(mc.font, text, centeredX(text, centerX), y, color, false);
		pose.popPose();
	}
	/**
	 * @return 居中这行文字还要额外平移的量。
	 * 	<p>{@code Font.width} 是整数，宽度为奇数时正中点落在两个像素中间，起点取整后必然偏半个像素，
	 * 	方向取决于往哪边取的整。这里补上半像素，让中心正好对上去。
	 */
	private static float centerOffset(Component text) {
		return (width(text) & 1) == 0 ? 0F : -0.5F;
	}
	/** @return 居中绘制时的整数起点，配合 {@link #centerOffset} 用。 */
	private static int centeredX(Component text, int centerX) {
		return centerX - width(text) / 2;
	}
	/**
	 * @return 这行文字的宽度。
	 * 	<p>先取 {@code getVisualOrderText} 再量：测宽和绘制因此看的是同一份展开结果
	 * 	（{@code translatable} 之类会在这里展开一次），不会各算各的。
	 */
	public static int width(Component text) {
		return mc.font.width(text.getVisualOrderText());
	}
	/** 画一行水平居中、带描边的文字。 */
	public static void drawOutlinedCentered(GuiGraphics gui, Component text, int centerX, int y, int color) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(centerOffset(text), 0F, 0F);
		drawOutlined(gui, text, centeredX(text, centerX), y, color);
		pose.popPose();
	}
	/**
	 * 画一行带描边的文字，对齐 Mindustry 的 {@code Fonts.outline} 与 {@code Styles.outlineLabel}。
	 * <p>描边由字形自己携带（{@link #outlineShift} 取的那套膨胀字形里，环和芯烙在同一格上），
	 * 这里一次画完就同时得到环和正文——不分两层，也就没有谁盖谁的问题。
	 */
	public static void drawOutlined(GuiGraphics gui, Component text, int x, int y, int color) {
		gui.drawString(mc.font, outlineShift(text), x, y, color, false);
	}
}
