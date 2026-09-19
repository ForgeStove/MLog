package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
	 * 描边色的压暗系数，取自 Mindustry 的 {@code Color.darkGray}（{@code 0x3f3f3f}）。
	 * <p>那边的描边是 FreeType 烘焙进字形的：边缘像素是 borderColor，绘制时整个字形再被正文色
	 * tint 一遍，所以描边色实际是「正文色 × darkGray」——彩色的字自带同色调的暗边。
	 * 我们没有烘焙的能力，{@link #drawOutlined} 只能把颜色自己乘一遍。
	 */
	private static final int OUTLINE_FACTOR = 0x3F;
	/** @return {@code color} 对应的描边色。 */
	public static int outlineColor(int color) {
		return (color >> 16 & 0xFF) * OUTLINE_FACTOR / 0xFF << 16
			| (color >> 8 & 0xFF) * OUTLINE_FACTOR / 0xFF << 8
			| (color & 0xFF) * OUTLINE_FACTOR / 0xFF
			| 0xFF000000;
	}
	/** 描边字体的资源位置，见 {@code assets/mlog/font/outline.json}。 */
	public static final ResourceLocation OUTLINE_ID = getMLogRes("outline");
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
	 * <p>描边字体 {@link #OUTLINE_ID} 的字形是向外膨胀过的，先用它铺一层描边色，
	 * 再把正文色压上去，被盖住的中心就只剩一圈描边。两层各画一次——
	 * Mindustry 把描边烘焙进了字形，一次就能画完；MC 没有那个参数，多铺一层是等价的本地做法。
	 */
	public static void drawOutlined(GuiGraphics gui, Component text, int x, int y, int color) {
		gui.drawString(mc.font, outlineLayer(text, color), x, y, color, false);
		gui.drawString(mc.font, text, x, y, color, false);
	}
	/**
	 * @return 与 {@code text} 结构相同、换成描边字体且每段颜色都压暗过的描边层。
	 * 	<p>要逐段换色，不能整段当纯文本画：{@code rich} 出来的彩色片段得留住自己的色调，
	 * 	一律压成同一个颜色的话，彩色的字就没有同色调的描边了。
	 * 	<p>也不能用 {@code plainCopy} 省事——它只带内容，会把 {@code append} 出来的子组件全丢光。
	 */
	private static Component outlineLayer(Component text, int fallback) {
		var out = MutableComponent.create(text.getContents());
		var style = text.getStyle();
		var own = style.getColor();
		out.setStyle(style.withFont(OUTLINE_ID).withColor(outlineColor(own == null ? fallback : own.getValue())));
		for (var sibling : text.getSiblings()) out.append(outlineLayer(sibling, fallback));
		return out;
	}
}
