package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/**
 * 界面图标，取自 Mindustry 的 {@code icon.ttf}。
 * <p>它不是位图而是一套字体，所以任意尺寸都清晰——Mindustry 的图标也是这么做的，
 * 那些 {@code assets-raw/icons/*.png} 只是生成字体用的素材。
 * <p>码点由 Mindustry 的 {@code assets-raw/fontgen/config.json} 分配，名字和文件名可能不同
 * （比如 {@code search} 在图集里叫 {@code zoom}、{@code pencil} 叫 {@code pencil_}）。
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
	/** 返回，对应 Mindustry 的 {@code Icon.left}。 */
	BACK((char) 0xE802),
	LINK((char) 0xE81C),
	SAVE((char) 0xE81B),
	TRASH((char) 0xE86F),
	/** 主界面底部的「编辑」按钮，对应 Mindustry 的 {@code Icon.edit}。 */
	EDITOR((char) 0xE816),
	MENU((char) 0xE88C),
	/** 语句表分类标题前的小图标，对应 Mindustry 的 {@code Icon.logicSmall} 那几个。 */
	LOGIC((char) 0xE80E),
	EFFECT((char) 0xE853),
	SETTINGS((char) 0xE87C),
	ROTATE((char) 0xE823),
	/** 获取数据弹窗顶部的分类图标，对应 Mindustry 的 {@code Icon.box / liquid / tree}。 */
	BOX((char) 0xE81E),
	LIQUID((char) 0xE85C),
	TREE((char) 0xE875),
	;
	/**
	 * 字形中心比基线高出的距离。
	 * <p>图标字形几乎全在基线<b>上方</b>（实测范围 -9.34 ~ +1.66，中点是 -3.84），
	 * 所以垂直居中时要把它加回去，也就是把基线往上挪同样的量。
	 */
	private static final float GLYPH_CENTER = -3.84F;
	/**
	 * 居中之后再往下压的一点点。
	 * <p>字形的几何中点和视觉重心不完全重合，实测图标会显得略高
	 */
	private static final float GLYPH_NUDGE = 0.5F;
	private final Component text;
	LogicIcons(char code) {
		// 枚举构造器不能引用静态字段（那时它还没初始化），所以现取而不是用常量
		text = Component.literal(String.valueOf(code)).withStyle(style -> style.withFont(fontId()));
	}
	/** @return 图标字体的资源位置。 */
	public static ResourceLocation fontId() {
		return getMLogRes("icons");
	}
	/**
	 * @return 让图标在指定高度的容器里垂直居中时，{@link #render} 该传的 {@code y}。
	 * 	<p>注意 {@code y} 是文字的<b>基线</b>而不是顶部，直接按行高算会偏到容器外面去。
	 */
	public static int centerY(int containerY, int containerHeight) {
		return Math.round(containerY + containerHeight / 2F + GLYPH_CENTER + GLYPH_NUDGE);
	}
	/**
	 * 按比例画图标，位置和 {@link #render} 完全一致。
	 * <p>字号是字体级的（{@code icons.json} 的 {@code size}），改它会影响所有图标；
	 * 分类标题那种行高很小的位置得单独缩，所以留了这个口子。
	 * <p>定点取「左边缘 + 图标垂直中心」而不是坐标原点：字形相对原点本身带着偏移，
	 * 以原点缩放的话那份偏移也会跟着缩，图标就整体往左上跑了。
	 */
	public void renderScaled(GuiGraphics gui, int x, int y, float scale, int color) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(x * (1F - scale), (y + GLYPH_CENTER) * (1F - scale), 0F);
		pose.scale(scale, scale, 1F);
		render(gui, x, y, color);
		pose.popPose();
	}
	/** 用指定颜色画图标。{@code x} 是左边缘，{@code y} 是<b>基线</b>，垂直位置用 {@link #centerY} 算。 */
	public void render(GuiGraphics gui, int x, int y, int color) {
		// 不要阴影：图标线条细，投影会把轮廓糊掉
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
