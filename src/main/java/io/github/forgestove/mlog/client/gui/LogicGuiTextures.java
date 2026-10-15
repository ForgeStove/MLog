package io.github.forgestove.mlog.client.gui;
import io.github.forgestove.mlog.core.util.MLogUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiSpriteManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.NineSlice;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.NineSlice.Border;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.*;
/**
 * 界面纹理：登记精灵名，<b>贴图与九宫格边距都在资源文件里</b>。
 * <p>
 * 贴图放 {@code textures/gui/sprites/**}（{@code minecraft:atlases/gui} 的 directory 源自动收录），
 * 边距声明在同名 {@code .png.mcmeta} 的 {@code gui.scaling.nine_slice} 里、由 {@link #border} 读回来，
 * 所以"切法"的真源在资源，改边距不用动 Java。
 * <p>
 * <b>为什么不用引擎的九宫格</b>：{@code GuiGraphics#blitNineSlicedSprite} 只会按原边距 1:1 画角，
 * 目标变小时顶多裁切，<b>没有"边距随目标等比缩小"的能力</b>（即 {@link #marginScale} 的语义）。
 * 所以这里只把图集当素材库，自己按缩放后的矩形做九宫格，见 {@link #blit}。
 * <p>
 * 例外的只有 {@link #LOGIC_NODE}：图集的过滤是全局的、没有逐精灵的 {@code texture.blur}，详见它自己的说明。
 * <p>图标不在此处，图标为字体字形，见 {@link LogicIcons}。
 */
@OnlyIn(Dist.CLIENT)
public enum LogicGuiTextures {
	/** 实心不透明面板，用于对话框与弹出列表。 */
	PANE_SOLID("pane_solid"),
	/** 按钮底纹。 */
	BUTTON("button"),
	BUTTON_OVER("button_over"),
	/** 卡片边框：白色边 + 透明中心，染色后作为类别色边框。 */
	WHITE_PANE("white_pane"),
	/** 界面所有横条：参数框底线、标题横条、列表分隔线，染色后使用。边距为 0，整张拉伸。 */
	UNDERLINE("underline"),
	/**
	 * 节点图标。
	 * <p>
	 * ⚠️ <b>唯一不进图集的一张</b>：它靠 {@code logic_node.png.mcmeta} 的 {@code texture.blur}
	 * 在运行期把 32×32 线性缩到 12×12（GUI scale ≥ 2 时还要放大回去），而图集的过滤是全局的、
	 * 没有逐精灵的 blur（{@code GuiSpriteManager} 只解析 animation 与 gui 两段元数据）。
	 * 烘成 12×12 进图集后，GUI scale ≥ 2 时会被最近邻放大成色块，与原观感对不上。
	 */
	LOGIC_NODE("logic_node", 32, 32),
	/** 滚动条槽。 */
	SCROLL("scroll"),
	/** 滚动条滑块，整张拉伸。 */
	SCROLL_KNOB("scroll_knob"),
	/** 取色器滑块把手的三态，整张拉伸。 */
	SLIDER_KNOB("slider_knob"),
	SLIDER_KNOB_OVER("slider_knob_over"),
	SLIDER_KNOB_DOWN("slider_knob_down"),
	/** 取色器滑块轨道的深色边框。 */
	SLIDER_BACK("slider_back"),
	;
	/** {@link #UNDERLINE} 的绘制高度；所有横条依此绘制以保证粗细一致。 */
	public static final int UNDERLINE_H = 2;
	/** {@link #BUTTON} 的圆角半径（源图像素）；界面据此与绘制缩放计算内容内缩量。 */
	public static final int CORNER = 12;
	/**
	 * 四角各边占目标尺寸的最大比例。
	 * <p>纹理圆角为 12px，按原尺寸绘制在 26px 高的按钮上会占满整条边，长弧线呈现明显锯齿；
	 * 限制占比后圆角随之缩小，形状不变。
	 * <p>取 0.2 使 24 高的按钮恰好落在 0.4 的缩放上：纹理按 40 的行高绘制，此处行高为 16。
	 * 调整按钮边框粗细即修改此值：{@code 缩放 = 2 × MAX_CORNER}。
	 */
	private static final float MAX_CORNER = 0.2F;
	/**
	 * UV 分母：见 {@link #blit}。
	 * <p>取 2¹⁶ 让"图集 UV"能塞进 {@code blit} 那个 {@code int uWidth} 参数里；
	 * 由此引入的取整误差不超过 {@code 0.5 / 2¹⁶}，约千分之一纹素。
	 */
	private static final float UV = 1 << 16;
	/** 非九宫格精灵的边距；常量，避免每次查询都分配。 */
	private static final Border NO_BORDER = new Border(0, 0, 0, 0);
	/** 是否走独立纹理（不进图集）；只有 {@link #LOGIC_NODE} 为真，理由见它自己的说明。 */
	private final boolean standalone;
	/** {@code standalone} 时是独立纹理路径，否则是图集精灵名。 */
	private final ResourceLocation id;
	/** 独立纹理的源图尺寸；图集精灵的尺寸从精灵本身读，故为 0。 */
	private final int width, height;
	LogicGuiTextures(String name) {
		standalone = false;
		id = MLogUtil.getMLogRes(name);
		width = height = 0;
	}
	LogicGuiTextures(String name, int width, int height) {
		standalone = true;
		id = MLogUtil.getMLogRes("textures/gui/" + name + ".png");
		this.width = width;
		this.height = height;
	}
	/** @return 九宫格边距中的较大值，界面据此为框内内容留边。 */
	public int margin() {
		var border = border();
		return Math.max(Math.max(border.left(), border.right()), Math.max(border.top(), border.bottom()));
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
	/** 将纹理按九宫格铺满指定矩形，四角按目标尺寸自动收缩。 */
	public void render(GuiGraphics gui, int x, int y, int w, int h) {
		if (standalone) {
			renderStandalone(gui, x, y, w, h);
			return;
		}
		var atlas = Minecraft.getInstance().getGuiSprites();
		var sprite = atlas.getSprite(id);
		var border = border(atlas, sprite);
		render(gui, sprite, border, x, y, w, h, marginScale(border, w, h));
	}
	/**
	 * 按指定比例铺满指定矩形。
	 * <p>源区域保持原尺寸、目标按 {@code scale} 缩小，等效于将整块纹理等比缩小后拼接：
	 * 边框与圆角一同变细而形状不变。同一纹理用于大框与小框需观感一致时，以此压住大框。
	 *
	 * @param scale 缩放系数，最大 1；纹理只缩不放，需更粗应改用更大的图。
	 */
	public void render(GuiGraphics gui, int x, int y, int w, int h, float scale) {
		if (standalone) {
			renderStandalone(gui, x, y, w, h);
			return;
		}
		var atlas = Minecraft.getInstance().getGuiSprites();
		var sprite = atlas.getSprite(id);
		render(gui, sprite, border(atlas, sprite), x, y, w, h, scale);
	}
	/**
	 * 独立纹理没有九宫格，整张拉伸一次即可 —— 与九宫格路径在边距全 0 时的唯一一次中心绘制等价。
	 * <p>走 {@code blit} 而不是图集精灵，是为了让纹理自己的 {@code texture.blur} 生效。
	 * 边距恒为 0，因此缩放系数恒为 1，这里不收 {@code scale}。
	 */
	private void renderStandalone(GuiGraphics gui, int x, int y, int w, int h) {
		if (w <= 0 || h <= 0) return;
		gui.blit(id, x, y, w, h, 0, 0, width, height, width, height);
	}
	private void render(
		GuiGraphics gui, TextureAtlasSprite sprite, Border border, int x, int y, int w, int h, float scale
	) {
		var sw = spriteWidth(sprite);
		var sh = spriteHeight(sprite);
		var left = border.left();
		var right = border.right();
		var top = border.top();
		var bottom = border.bottom();
		var s = Math.clamp(scale, 0F, 1F);
		var l = Math.round(left * s);
		var r = Math.round(right * s);
		var t = Math.round(top * s);
		var b = Math.round(bottom * s);
		var midW = Math.max(0, w - l - r);
		var midH = Math.max(0, h - t - b);
		var srcMidW = Math.max(0, sw - left - right);
		var srcMidH = Math.max(0, sh - top - bottom);
		// 四角：目标缩小、源保持原边距，等效于压扁圆角而非裁切
		blit(gui, sprite, sw, sh, x, y, l, t, 0, 0, left, top);
		blit(gui, sprite, sw, sh, x + w - r, y, r, t, sw - right, 0, right, top);
		blit(gui, sprite, sw, sh, x, y + h - b, l, b, 0, sh - bottom, left, bottom);
		blit(gui, sprite, sw, sh, x + w - r, y + h - b, r, b, sw - right, sh - bottom, right, bottom);
		// 四边
		blit(gui, sprite, sw, sh, x + l, y, midW, t, left, 0, srcMidW, top);
		blit(gui, sprite, sw, sh, x + l, y + h - b, midW, b, left, sh - bottom, srcMidW, bottom);
		blit(gui, sprite, sw, sh, x, y + t, l, midH, 0, top, left, srcMidH);
		blit(gui, sprite, sw, sh, x + w - r, y + t, r, midH, sw - right, top, right, srcMidH);
		// 中心
		blit(gui, sprite, sw, sh, x + l, y + t, midW, midH, left, top, srcMidW, srcMidH);
	}
	/**
	 * 把精灵内的 {@code (u, v, uWidth, vHeight)}（源图像素）铺到任意 {@code (x, y, w, h)}。
	 * <p>
	 * {@code GuiGraphics#blitSprite} 的取子区重载只能 1:1 画，能换目标尺寸的那个重载又要图集 UV。
	 * 这里用 {@link TextureAtlasSprite#getU}/{@link TextureAtlasSprite#getV} 把"精灵内像素坐标"
	 * 换成图集 UV，再用 {@link #UV} 作分母还原成 {@code uOffset / textureWidth} 的形式
	 * —— 该重载的 {@code uWidth} 是 int，只能这么给。
	 */
	private static void blit(
		GuiGraphics gui,
		TextureAtlasSprite sprite,
		int sw,
		int sh,
		int x,
		int y,
		int w,
		int h,
		int u,
		int v,
		int uWidth,
		int vHeight
	) {
		if (w <= 0 || h <= 0 || uWidth <= 0 || vHeight <= 0) return;
		var minU = sprite.getU((float) u / sw);
		var maxU = sprite.getU((float) (u + uWidth) / sw);
		var minV = sprite.getV((float) v / sh);
		var maxV = sprite.getV((float) (v + vHeight) / sh);
		gui.blit(
			sprite.atlasLocation(),
			x,
			y,
			w,
			h,
			minU * UV,
			minV * UV,
			Math.round((maxU - minU) * UV),
			Math.round((maxV - minV) * UV),
			(int) UV,
			(int) UV
		);
	}
	/**
	 * @return 边距的缩放系数，最大为 1（不放大）。
	 * 	<p>以 {@link #MAX_CORNER} 限制四角占比，同时保证目标尺寸可容纳四边边距。
	 * 	水平与垂直取同一系数，避免圆角被压成椭圆。
	 */
	private float marginScale(int w, int h) {
		return marginScale(border(), w, h);
	}
	private static float marginScale(Border border, int w, int h) {
		var scaleW = border.left() + border.right() > 0 ? w * MAX_CORNER * 2 / (border.left() + border.right()) : 1F;
		var scaleH = border.top() + border.bottom() > 0 ? h * MAX_CORNER * 2 / (border.top() + border.bottom()) : 1F;
		return Math.min(1F, Math.min(scaleW, scaleH));
	}
	/** @return 九宫格边距在指定尺寸下的缩放系数，供在其它尺寸上复用同一粗细的调用方。 */
	public float scaleFor(int w, int h) {
		return marginScale(w, h);
	}
	/**
	 * 水平镜像绘制。目标端的跳转箭头需指向卡片，方向与源端节点图标相反。
	 * <p>镜像靠把 U 轴跨度取负实现，不可使用 {@code pose.scale(-1,1,1)}：
	 * 该方式会翻转顶点绕序并被背面剔除，导致图标完全不可见。
	 */
	public void renderTintedFlipped(GuiGraphics gui, int x, int y, int w, int h, int color) {
		gui.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
		blitFlipped(gui, x, y, w, h);
		gui.setColor(1F, 1F, 1F, 1F);
	}
	/** 整张水平镜像：无论独立纹理还是图集精灵，都是把 U 轴跨度取负。 */
	private void blitFlipped(GuiGraphics gui, int x, int y, int w, int h) {
		if (standalone) {
			gui.blit(id, x, y, w, h, width, 0F, -width, height, width, height);
			return;
		}
		var sprite = Minecraft.getInstance().getGuiSprites().getSprite(id);
		var uWidth = Math.round((sprite.getU1() - sprite.getU0()) * UV);
		var vHeight = Math.round((sprite.getV1() - sprite.getV0()) * UV);
		gui.blit(
			sprite.atlasLocation(),
			x,
			y,
			w,
			h,
			sprite.getU1() * UV,
			sprite.getV0() * UV,
			-uWidth,
			vHeight,
			(int) UV,
			(int) UV
		);
	}
	/** @return mcmeta 里声明的九宫格边距；非九宫格精灵与独立纹理为 {@link #NO_BORDER}。 */
	private Border border() {
		if (standalone) return NO_BORDER;
		var atlas = Minecraft.getInstance().getGuiSprites();
		return border(atlas, atlas.getSprite(id));
	}
	private static Border border(GuiSpriteManager atlas, TextureAtlasSprite sprite) {
		return atlas.getSpriteScaling(sprite) instanceof NineSlice nine ? nine.border() : NO_BORDER;
	}
	@SuppressWarnings("resource")
	private static int spriteWidth(TextureAtlasSprite sprite) {
		return sprite.contents().width();
	}
	@SuppressWarnings("resource")
	private static int spriteHeight(TextureAtlasSprite sprite) {
		return sprite.contents().height();
	}
}
