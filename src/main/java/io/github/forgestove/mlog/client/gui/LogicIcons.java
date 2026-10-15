package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/** 图标以字体渲染，位图仅用于生成字体；码点由字体生成器配置分配，枚举名与图集名可能不同（{@code search} = {@code zoom}、{@code pencil} = {@code pencil_}）。 */
public enum LogicIcons {
	ADD((char) 0xE813),
	COPY((char) 0xE874),
	CANCEL((char) 0xE815),
	PENCIL((char) 0xE869),
	SEARCH((char) 0xE88A),
	REFRESH((char) 0xE86A),
	DOWNLOAD((char) 0xE879),
	EXPORT((char) 0xE878),
	BACK((char) 0xE802),
	LINK((char) 0xE81C),
	SAVE((char) 0xE81B),
	TRASH((char) 0xE86F),
	/** 主界面底部的「编辑」按钮。 */
	EDITOR((char) 0xE816),
	MENU((char) 0xE88C),
	/** 语句表分类标题前的小图标。 */
	LOGIC((char) 0xE80E),
	EFFECT((char) 0xE853),
	SETTINGS((char) 0xE87C),
	ROTATE((char) 0xE823),
	TERRAIN((char) 0xE864),
	/** 获取数据弹窗顶部的分类图标。 */
	BOX((char) 0xE81E),
	LIQUID((char) 0xE85C),
	TREE((char) 0xE875),
	;
	/** 字形中心相对基线的高度：实测范围 -9.34 ~ +1.66，中点 -3.84，垂直居中时需将其加回。 */
	private static final float GLYPH_CENTER = -3.84F;
	/** 居中后的额外下移量：字形几何中点与视觉重心不重合，实测偏高。 */
	private static final float GLYPH_NUDGE = 0.5F;
	private final Component text;
	LogicIcons(char code) {
		// 枚举构造器不可引用尚未初始化的静态字段，故需即时取值
		text = Component.literal(String.valueOf(code)).withStyle(style -> style.withFont(fontId()));
	}
	public static ResourceLocation fontId() {
		return getMLogRes("icons");
	}
	/** {@link #render} 垂直居中所需的 {@code y}；该 {@code y} 为文字基线而非顶部，按行高计算会偏出容器；供世界渲染等自行绘制处使用。 */
	public static int centerY(int containerY, int containerHeight) {
		return Math.round(containerY + containerHeight / 2F + GLYPH_CENTER + GLYPH_NUDGE);
	}
	public Component component() {
		return text;
	}
	/** 按比例绘制图标；分类标题等行高较小的位置需单独缩放。缩放锚点取左边缘加图标垂直中心，以原点缩放会使字形相对原点的偏移一同缩放而整体向左上偏移。 */
	public void renderScaled(GuiGraphics gui, int x, int y, float scale, int color) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(x * (1F - scale), (y + GLYPH_CENTER) * (1F - scale), 0F);
		pose.scale(scale, scale, 1F);
		render(gui, x, y, color);
		pose.popPose();
	}
	public void render(GuiGraphics gui, int x, int y, int color) {
		// 不启用阴影：图标线条细，投影会使轮廓模糊
		gui.drawString(mc.font, text, x, y, color, false);
	}
	public int width(float scale) {
		return Math.round(width() * scale);
	}
	public int width() {
		return mc.font.width(text);
	}
}
