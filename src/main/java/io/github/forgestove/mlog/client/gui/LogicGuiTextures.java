package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.*;
import net.minecraft.resources.*;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogUtil.*;
/**
 * 取自 Mindustry 的界面纹理。
 * <p>九宫格纹理（边距非 0）：四角原样，四边与中心拉伸；目标小于边距之和时边距会等比收缩，
 * 见 {@link #render}。
 * <p>图标不在这里——它们是 Mindustry 的字体字形，见 {@link LogicIcons}。
 */
@OnlyIn(Dist.CLIENT)
public enum LogicGuiTextures {
	/** 实心不透明面板，用于对话框与弹出列表。边距就是那圈灰边的宽度，得和纹理对得上。 */
	PANE_SOLID("pane_solid", 36, 27, 2, 2, 2, 2),
	/** 按钮底纹，取自 Mindustry 原图。配了 {@code blur} 的 mcmeta，圆角放大时不会有硬台阶。 */
	BUTTON("button", 36, 27, 12, 12, 12, 12),
	BUTTON_OVER("button_over", 36, 27, 12, 12, 12, 12),
	/** 卡片边框：白色边 + 透明中心，染色后作为类别色边框。 */
	WHITE_PANE("white_pane", 36, 27, 12, 12, 12, 12),
	/**
	 * 界面里所有的横条——参数框底线、标题横条、列表的分隔线，染色后使用。
	 * <p>线只占纹理高度的五分之四，画到两像素高就是 1.6——Mindustry 那边是 4 像素对 40 的行高，
	 * 比例正好。不到一个像素的那部分靠 {@code .mcmeta} 里的 {@code blur}（线性过滤）表现，
	 * 所以边距必须留 0 让整张拉伸，走不了九宫格。
	 */
	UNDERLINE("underline", 8, 5, 0, 0, 0, 0),
	LOGIC_NODE("logic_node", 32, 32, 0, 0, 0, 0),
	/** 滚动条槽。 */
	SCROLL("scroll", 24, 35, 10, 10, 6, 5),
	/** 滚动条滑块，整张拉伸。 */
	SCROLL_KNOB("scroll_knob", 24, 40, 0, 0, 0, 0),
	;
	/** {@link #UNDERLINE} 的绘制高度。所有横条都按它画，粗细才一致。 */
	public static final int UNDERLINE_H = 2;
	/**
	 * 四角各边最多占目标尺寸的这个比例。
	 * <p>纹理里的圆角是 12px，直接按原尺寸画在 26px 高的按钮上会占满整条边，
	 * 那条长弧线看起来全是锯齿。限制占比后圆角会被缩小，形状不变。
	 * <p>取 0.2 是为了让 24 高的按钮正好落在 0.4 的缩放上——纹理是按 Mindustry 那边 40 的行高画的，
	 * 这里的行高是 16。想整体调按钮的边框粗细就动这个值：{@code 缩放 = 2 × MAX_CORNER}。
	 */
	private static final float MAX_CORNER = 0.2F;
	public final ResourceLocation location;
	/** 纹理原始尺寸与四边九宫格边距。 */
	private final int width, height, left, right, top, bottom;
	LogicGuiTextures(String name, int width, int height, int left, int right, int top, int bottom) {
		location = getMLogRes("textures/gui/" + name + ".png");
		this.width = width;
		this.height = height;
		this.left = left;
		this.right = right;
		this.top = top;
		this.bottom = bottom;
	}
	/**
	 * @return 边距的缩放系数，最大为 1（不放大）。
	 * <p>用 {@link #MAX_CORNER} 限制四角占比，顺带保证了目标尺寸放得下四边边距。
	 * 水平和垂直取同一个系数，圆角才不会被压成椭圆。
	 */
	private float marginScale(int w, int h) {
		var scaleW = left + right > 0 ? w * MAX_CORNER * 2 / (left + right) : 1F;
		var scaleH = top + bottom > 0 ? h * MAX_CORNER * 2 / (top + bottom) : 1F;
		return Math.min(1F, Math.min(scaleW, scaleH));
	}
	/** 用指定颜色画图标，画完复位。 */
	public void renderTinted(GuiGraphics gui, int x, int y, int w, int h, int color) {
		gui.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
		render(gui, x, y, w, h);
		gui.setColor(1F, 1F, 1F, 1F);
	}
	/**
	 * 水平镜像画图标。目标端的跳转箭头要指向卡片，方向和源端的节点图标相反。
	 * <p>镜像靠反转 U 轴纹理坐标实现，<b>不能</b>用 {@code pose.scale(-1,1,1)}：那会把顶点绕序翻过来，
	 * 被背面剔除吃掉，图标就整个不见了。
	 */
	public void renderTintedFlipped(GuiGraphics gui, int x, int y, int w, int h, int color) {
		gui.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
		gui.blit(location, x, y, w, h, width, 0F, -width, height, width, height);
		gui.setColor(1F, 1F, 1F, 1F);
	}
	/** 把纹理按九宫格铺满指定矩形，四角按目标尺寸自动收缩。 */
	public void render(GuiGraphics gui, int x, int y, int w, int h) {
		render(gui, x, y, w, h, marginScale(w, h));
	}
	/**
	 * 按指定比例铺满指定矩形。
	 * <p>源区域保持原尺寸、目标按 {@code scale} 缩，等于把整块纹理等比缩小后再拼接：
	 * 边框和圆角一起变细，形状不变。同一个纹理铺在大框和小框上想要一致观感时，用它压住大框那边。
	 * @param scale 缩放系数，最大 1——纹理只缩不放，想要更粗得换更大的图。
	 */
	public void render(GuiGraphics gui, int x, int y, int w, int h, float scale) {
		var s = Math.clamp(scale, 0F, 1F);
		var l = Math.round(left * s);
		var r = Math.round(right * s);
		var t = Math.round(top * s);
		var b = Math.round(bottom * s);
		var midW = Math.max(0, w - l - r);
		var midH = Math.max(0, h - t - b);
		var srcMidW = Math.max(0, width - left - right);
		var srcMidH = Math.max(0, height - top - bottom);
		// 四角：目标缩小、源保持原边距，等于把圆角压扁而不是切掉
		blit(gui, x, y, l, t, 0, 0, left, top);
		blit(gui, x + w - r, y, r, t, width - right, 0, right, top);
		blit(gui, x, y + h - b, l, b, 0, height - bottom, left, bottom);
		blit(gui, x + w - r, y + h - b, r, b, width - right, height - bottom, right, bottom);
		// 四边
		blit(gui, x + l, y, midW, t, left, 0, srcMidW, top);
		blit(gui, x + l, y + h - b, midW, b, left, height - bottom, srcMidW, bottom);
		blit(gui, x, y + t, l, midH, 0, top, left, srcMidH);
		blit(gui, x + w - r, y + t, r, midH, width - right, top, right, srcMidH);
		// 中心
		blit(gui, x + l, y + t, midW, midH, left, top, srcMidW, srcMidH);
	}
	private void blit(GuiGraphics gui, int x, int y, int w, int h, int u, int v, int uWidth, int vHeight) {
		if (w <= 0 || h <= 0 || uWidth <= 0 || vHeight <= 0) return;
		gui.blit(location, x, y, w, h, u, v, uWidth, vHeight, width, height);
	}
}
