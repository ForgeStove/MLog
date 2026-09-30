package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/**
 * 界面图标。
 * <p>图标为字体而非位图，任意尺寸均清晰；位图素材仅用于生成字体。
 * <p>码点由字体生成器配置分配，枚举名与文件名可能不同，
 * 例如 {@code search} 在图集中名为 {@code zoom}、{@code pencil} 为 {@code pencil_}。
 */
@OnlyIn(Dist.CLIENT)
public enum LogicIcons {
	ADD((char) 0xE813),
	COPY((char) 0xE874),
	CANCEL((char) 0xE815),
	PENCIL((char) 0xE869),
	SEARCH((char) 0xE88A),
	REFRESH((char) 0xE86A),
	DOWNLOAD((char) 0xE879),
	EXPORT((char) 0xE878),
	/** 返回。 */
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
	/**
	 * 字形中心相对基线的高度。
	 * <p>图标字形几乎全在基线上方（实测范围 -9.34 ~ +1.66，中点为 -3.84），
	 * 垂直居中时需将其加回，即把基线相应上移。
	 */
	private static final float GLYPH_CENTER = -3.84F;
	/**
	 * 居中后额外下移的量。
	 * <p>字形几何中点与视觉重心不完全重合，实测图标偏高。
	 */
	private static final float GLYPH_NUDGE = 0.5F;
	private final Component text;
	LogicIcons(char code) {
		// 枚举构造器不可引用静态字段（尚未初始化），故此处即时取值
		text = Component.literal(String.valueOf(code)).withStyle(style -> style.withFont(fontId()));
	}
	/** @return 图标字体的资源位置。 */
	public static ResourceLocation fontId() {
		return getMLogRes("icons");
	}
	/**
	 * @return 图标在指定高度容器中垂直居中时 {@link #render} 应传的 {@code y}。
	 * 	<p>{@code y} 为文字基线而非顶部，按行高直接计算会偏出容器。
	 */
	public static int centerY(int containerY, int containerHeight) {
		return Math.round(containerY + containerHeight / 2F + GLYPH_CENTER + GLYPH_NUDGE);
	}
	/** @return 图标对应的文本，供世界渲染等自行绘制处使用。 */
	public Component component() {
		return text;
	}
	/**
	 * 按比例绘制图标，位置与 {@link #render} 一致。
	 * <p>字号为字体级（{@code icons.json} 的 {@code size}），修改会影响所有图标；
	 * 分类标题等行高较小的位置需单独缩放，故保留此入口。
	 * <p>缩放锚点取左边缘加图标垂直中心而非坐标原点：字形相对原点本身带有偏移，
	 * 以原点缩放会使该偏移一同缩放，图标整体向左上偏移。
	 */
	public void renderScaled(GuiGraphics gui, int x, int y, float scale, int color) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(x * (1F - scale), (y + GLYPH_CENTER) * (1F - scale), 0F);
		pose.scale(scale, scale, 1F);
		render(gui, x, y, color);
		pose.popPose();
	}
	/** 以指定颜色绘制图标。{@code x} 为左边缘，{@code y} 为基线，垂直位置由 {@link #centerY} 计算。 */
	public void render(GuiGraphics gui, int x, int y, int color) {
		// 不启用阴影：图标线条细，投影会使轮廓模糊
		gui.drawString(mc.font, text, x, y, color, false);
	}
	/** @return 按比例缩放后的宽度。 */
	public int width(float scale) {
		return Math.round(width() * scale);
	}
	/** @return 图标宽度，用于居中排布。 */
	public int width() {
		return mc.font.width(text);
	}
}
