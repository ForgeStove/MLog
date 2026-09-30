package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/**
 * 界面纹理。
 * <p>九宫格纹理（边距非 0）：四角原样，四边与中心拉伸；目标小于边距之和时边距等比收缩，
 * 见 {@link #render}。
 * <p>图标不在此处，图标为字体字形，见 {@link LogicIcons}。
 */
@OnlyIn(Dist.CLIENT)
public enum LogicGuiTextures {
	/** 实心不透明面板，用于对话框与弹出列表。边距为灰边宽度，须与纹理一致。 */
	PANE_SOLID("pane_solid", 36, 27, 2, 2, 2, 2),
	/** 按钮底纹。mcmeta 配置了 {@code blur}，圆角放大时无硬边。 */
	BUTTON("button", 36, 27, 12, 12, 12, 12),
	BUTTON_OVER("button_over", 36, 27, 12, 12, 12, 12),
	/** 卡片边框：白色边 + 透明中心，染色后作为类别色边框。 */
	WHITE_PANE("white_pane", 36, 27, 12, 12, 12, 12),
	/**
	 * 界面所有横条：参数框底线、标题横条、列表分隔线，染色后使用。
	 * <p>线占纹理高度的五分之四，绘制为两像素高时相当于 1.6 像素，即 4 像素对应 40 的行高。
	 * 不足一像素的部分由 {@code .mcmeta} 的 {@code blur}（线性过滤）呈现，
	 * 故边距须为 0 以整张拉伸，不使用九宫格。
	 */
	UNDERLINE("underline", 8, 5, 0, 0, 0, 0),
	LOGIC_NODE("logic_node", 32, 32, 0, 0, 0, 0),
	/** 滚动条槽。 */
	SCROLL("scroll", 24, 35, 10, 10, 6, 5),
	/** 滚动条滑块，整张拉伸。 */
	SCROLL_KNOB("scroll_knob", 24, 40, 0, 0, 0, 0),
	/** 取色器滑块把手的三态：常态深灰、悬停强调色、按下白，都是实心块，整张拉伸。 */
	SLIDER_KNOB("slider_knob", 29, 42, 0, 0, 0, 0),
	SLIDER_KNOB_OVER("slider_knob_over", 29, 42, 0, 0, 0, 0),
	SLIDER_KNOB_DOWN("slider_knob_down", 29, 42, 0, 0, 0, 0),
	/** 取色器滑块轨道的深色边框：四边 4px 实心、中心透明，边距依纹理设定。 */
	SLIDER_BACK("slider_back", 36, 27, 4, 4, 4, 4),
	;
	/** {@link #UNDERLINE} 的绘制高度；所有横条依此绘制以保证粗细一致。 */
	public static final int UNDERLINE_H = 2;
	/** 纹理中的圆角半径（原始像素）；界面据此与绘制缩放计算内容内缩量。 */
	public static final int CORNER = 12;
	/**
	 * 四角各边占目标尺寸的最大比例。
	 * <p>纹理圆角为 12px，按原尺寸绘制在 26px 高的按钮上会占满整条边，长弧线呈现明显锯齿；
	 * 限制占比后圆角随之缩小，形状不变。
	 * <p>取 0.2 使 24 高的按钮恰好落在 0.4 的缩放上：纹理按 40 的行高绘制，此处行高为 16。
	 * 调整按钮边框粗细即修改此值：{@code 缩放 = 2 × MAX_CORNER}。
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
	/** @return 九宫格边距中的较大值，界面据此为框内内容留边。 */
	public int margin() {
		return Math.max(Math.max(left, right), Math.max(top, bottom));
	}
	/** 以指定颜色绘制，绘制后复位颜色。 */
	public void renderTinted(GuiGraphics gui, int x, int y, int w, int h, int color) {
		renderTinted(gui, x, y, w, h, color, marginScale(w, h));
	}
	/**
	 * 按指定缩放以指定颜色绘制。
	 * <p>高度压缩的框（如 {@code end}/{@code stop} 的薄卡）需保持边框与原尺寸等粗时，
	 * 传入参考尺寸的 {@link #scaleFor}，否则边距会随高度收缩。
	 */
	public void renderTinted(GuiGraphics gui, int x, int y, int w, int h, int color, float scale) {
		gui.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
		render(gui, x, y, w, h, scale);
		gui.setColor(1F, 1F, 1F, 1F);
	}
	/**
	 * @return 边距的缩放系数，最大为 1（不放大）。
	 * 	<p>以 {@link #MAX_CORNER} 限制四角占比，同时保证目标尺寸可容纳四边边距。
	 * 	水平与垂直取同一系数，避免圆角被压成椭圆。
	 */
	private float marginScale(int w, int h) {
		var scaleW = left + right > 0 ? w * MAX_CORNER * 2 / (left + right) : 1F;
		var scaleH = top + bottom > 0 ? h * MAX_CORNER * 2 / (top + bottom) : 1F;
		return Math.min(1F, Math.min(scaleW, scaleH));
	}
	/**
	 * 按指定比例铺满指定矩形。
	 * <p>源区域保持原尺寸、目标按 {@code scale} 缩小，等效于将整块纹理等比缩小后拼接：
	 * 边框与圆角一同变细而形状不变。同一纹理用于大框与小框需观感一致时，以此压住大框。
	 *
	 * @param scale 缩放系数，最大 1；纹理只缩不放，需更粗应改用更大的图。
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
		// 四角：目标缩小、源保持原边距，等效于压扁圆角而非裁切
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
	/** @return 九宫格边距在指定尺寸下的缩放系数，供在其它尺寸上复用同一粗细的调用方。 */
	public float scaleFor(int w, int h) {
		return marginScale(w, h);
	}
	/** 将纹理按九宫格铺满指定矩形，四角按目标尺寸自动收缩。 */
	public void render(GuiGraphics gui, int x, int y, int w, int h) {
		render(gui, x, y, w, h, marginScale(w, h));
	}
	/**
	 * 水平镜像绘制。目标端的跳转箭头需指向卡片，方向与源端节点图标相反。
	 * <p>镜像通过反转 U 轴纹理坐标实现，不可使用 {@code pose.scale(-1,1,1)}：
	 * 该方式会翻转顶点绕序并被背面剔除，导致图标完全不可见。
	 */
	public void renderTintedFlipped(GuiGraphics gui, int x, int y, int w, int h, int color) {
		gui.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
		gui.blit(location, x, y, w, h, width, 0F, -width, height, width, height);
		gui.setColor(1F, 1F, 1F, 1F);
	}
}
