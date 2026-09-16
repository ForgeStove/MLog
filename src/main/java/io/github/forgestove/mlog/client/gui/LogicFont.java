package io.github.forgestove.mlog.client.gui;
import io.github.forgestove.mlog.MLog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
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
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MLog.ID, "main");
	/**
	 * 描边色，对应 Mindustry 的 {@code Fonts.outline}（它的 {@code borderColor} 是 darkGray）。
	 * <p>这里比那边压得更暗：MC 没有把描边烘焙进字形的能力，{@link #drawOutlined} 得叠两层画，
	 * 而正文层边缘的抗锯齿像素是半透明的，会跟下面这层混色。这层越暗，混出来越接近"分类色变暗"；
	 * 用那边的 darkGray 就是灰蒙蒙一片，彩色文字尤其明显。
	 */
	public static final int OUTLINE = 0xFF202020;
	/** @return 用界面字体渲染的本地化文本。 */
	public static Component text(String key, Object... args) {
		return Component.translatable(key, args).withStyle(style -> style.withFont(ID));
	}
	/** @return 用界面字体渲染的纯文本。 */
	public static Component literal(String text) {
		return Component.literal(text).withStyle(style -> style.withFont(ID));
	}
	/**
	 * @return 用界面字体渲染、超宽就截断的单行文本。
	 * <p>{@code Font.plainSubstrByWidth} 是拿默认字体测宽的，换成界面字体后宽度对不上，
	 * 所以这里走 {@link net.minecraft.client.gui.Font#split}，它按样式里的字体算。
	 */
	public static FormattedCharSequence clipped(String text, int width) {
		var lines = mc.font.split(literal(text), width);
		return lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.getFirst();
	}
	/** 画一行界面字体文字。原版 {@code drawString} 默认带阴影，所以统一走这里。 */
	public static void draw(GuiGraphics gui, Component text, int x, int y, int color) {
		gui.drawString(mc.font, text, x, y, color, false);
	}
	/** 同上，接受已经按样式排好行的文本。 */
	public static void draw(GuiGraphics gui, FormattedCharSequence text, int x, int y, int color) {
		gui.drawString(mc.font, text, x, y, color, false);
	}
	/**
	 * @return 这行文字的宽度。
	 * <p>先取 {@code getVisualOrderText} 再量：测宽和绘制因此看的是同一份展开结果
	 * （{@code translatable} 之类会在这里展开一次），不会各算各的。
	 */
	public static int width(Component text) {
		return mc.font.width(text.getVisualOrderText());
	}
	/**
	 * @return 居中这行文字还要额外平移的量。
	 * <p>{@code Font.width} 是整数，宽度为奇数时正中点落在两个像素中间，起点取整后必然偏半个像素，
	 * 方向取决于往哪边取的整。这里补上半像素，让中心正好对上去。
	 */
	private static float centerOffset(Component text) {
		return (width(text) & 1) == 0 ? 0F : -0.5F;
	}
	/** @return 居中绘制时的整数起点，配合 {@link #centerOffset} 用。 */
	private static int centeredX(Component text, int centerX) {
		return centerX - width(text) / 2;
	}
	/** 画一行水平居中的界面字体文字。 */
	public static void drawCentered(GuiGraphics gui, Component text, int centerX, int y, int color) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(centerOffset(text), 0F, 0F);
		gui.drawString(mc.font, text, centeredX(text, centerX), y, color, false);
		pose.popPose();
	}
	/** 描边字体的资源位置，见 {@code assets/mlog/font/outline.json}。 */
	public static final ResourceLocation OUTLINE_ID = ResourceLocation.fromNamespaceAndPath(MLog.ID, "outline");
	/**
	 * 画一行带描边的文字，对齐 Mindustry 的 {@code Fonts.outline} 与 {@code Styles.outlineLabel}。
	 * <p>描边字体 {@link #OUTLINE_ID} 的字形是向外膨胀过的，先用它铺一层描边色，
	 * 再把正文色压上去，被盖住的中心就只剩一圈描边。两层各画一次——
	 * Mindustry 把描边烘焙进了字形，一次就能画完；MC 没有那个参数，多铺一层是等价的本地做法。
	 */
	public static void drawOutlined(GuiGraphics gui, Component text, int x, int y, int color) {
		gui.drawString(mc.font, text.copy().withStyle(style -> style.withFont(OUTLINE_ID)), x, y, OUTLINE, false);
		gui.drawString(mc.font, text, x, y, color, false);
	}
	/** 画一行水平居中、带描边的文字。 */
	public static void drawOutlinedCentered(GuiGraphics gui, Component text, int centerX, int y, int color) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(centerOffset(text), 0F, 0F);
		drawOutlined(gui, text, centeredX(text, centerX), y, color);
		pose.popPose();
	}
}
