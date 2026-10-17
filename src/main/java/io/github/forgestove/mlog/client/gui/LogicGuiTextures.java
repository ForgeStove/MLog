package io.github.forgestove.mlog.client.gui;
import io.github.forgestove.mlog.core.util.MLogUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.NineSlice;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.NineSlice.Border;
import net.minecraft.resources.ResourceLocation;
public enum LogicGuiTextures {
	PANE_SOLID("pane_solid"),
	BUTTON("button"),
	BUTTON_OVER("button_over"),
	BUTTON_DOWN("button_down"),
	BUTTON_DISABLED("button_disabled"),
	WHITE_PANE("white_pane"),
	UNDERLINE("underline"),
	/** 不进图集：缩放依赖 mcmeta 的 {@code texture.blur}，而图集的过滤是全局的，烘进图集后 GUI scale ≥ 2 会被最近邻放大成色块。 */
	LOGIC_NODE("logic_node", 32, 32),
	SCROLL("scroll"),
	SCROLL_KNOB("scroll_knob"),
	SLIDER_KNOB("slider_knob"),
	SLIDER_KNOB_OVER("slider_knob_over"),
	SLIDER_KNOB_DOWN("slider_knob_down"),
	SLIDER_BACK("slider_back"),
	;
	public static final int UNDERLINE_H = 2;
	public static final int CORNER = 12;
	/** 限制四角占比：圆角按原尺寸画在矮按钮上会占满整条边、呈现锯齿，取 0.2 使 24 高的按钮落在 0.4 的缩放上（{@code 缩放 = 2 × MAX_CORNER}）。 */
	private static final float MAX_CORNER = 0.2F;
	/** UV 分母：取 2¹⁶ 可使图集 UV 落入 {@code blit} 的 {@code int uWidth} 可表示范围。 */
	private static final float UV = 1 << 16;
	private static final Border NO_BORDER = new Border(0, 0, 0, 0);
	private final boolean standalone;
	private final ResourceLocation id;
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
	private static Border border(GuiSpriteManager atlas, TextureAtlasSprite sprite) {
		return atlas.getSpriteScaling(sprite) instanceof NineSlice nine ? nine.border() : NO_BORDER;
	}
	private static float marginScale(Border border, int w, int h) {
		var scaleW = border.left() + border.right() > 0 ? w * MAX_CORNER * 2 / (border.left() + border.right()) : 1F;
		var scaleH = border.top() + border.bottom() > 0 ? h * MAX_CORNER * 2 / (border.top() + border.bottom()) : 1F;
		return Math.min(1F, Math.min(scaleW, scaleH));
	}
	private static int spriteWidth(TextureAtlasSprite sprite) {
		return getContents(sprite).width();
	}
	private static int spriteHeight(TextureAtlasSprite sprite) {
		return getContents(sprite).height();
	}
	/** {@code blitSprite} 的取子区重载只能 1:1 绘制，可换目标尺寸的重载又要图集 UV，故用 {@code getU}/{@code getV} 换算并以 {@link #UV} 为分母。 */
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
	private static SpriteContents getContents(TextureAtlasSprite sprite) {
		return sprite.contents();
	}
	public int margin() {
		var border = border();
		return Math.max(Math.max(border.left(), border.right()), Math.max(border.top(), border.bottom()));
	}
	private Border border() {
		if (standalone) return NO_BORDER;
		var atlas = Minecraft.getInstance().getGuiSprites();
		return border(atlas, atlas.getSprite(id));
	}
	public void renderTinted(GuiGraphics gui, int x, int y, int w, int h, int color) {
		renderTinted(gui, x, y, w, h, color, marginScale(w, h));
	}
	/** 高度压缩的框（如 {@code end}/{@code stop} 的薄卡）须保持边框与原尺寸等粗时，传入参考尺寸的 {@link #scaleFor}。 */
	public void renderTinted(GuiGraphics gui, int x, int y, int w, int h, int color, float scale) {
		gui.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
		render(gui, x, y, w, h, scale);
		gui.setColor(1F, 1F, 1F, 1F);
	}
	/** 以 {@link #MAX_CORNER} 限制四角占比，两轴取同一系数，避免圆角变为椭圆。 */
	private float marginScale(int w, int h) {
		return marginScale(border(), w, h);
	}
	/** 源区域保持原尺寸、目标按 {@code scale} 缩小，边框与圆角一同变细而形状不变；同一纹理用于大框与小框需观感一致时以此限制大框。 */
	public void render(GuiGraphics gui, int x, int y, int w, int h, float scale) {
		if (standalone) {
			renderStandalone(gui, x, y, w, h);
			return;
		}
		var atlas = Minecraft.getInstance().getGuiSprites();
		var sprite = atlas.getSprite(id);
		render(gui, sprite, border(atlas, sprite), x, y, w, h, scale);
	}
	/** 独立纹理无九宫格，整张拉伸一次；用 {@code blit} 而非图集精灵，纹理自身的 {@code texture.blur} 才会生效。 */
	private void renderStandalone(GuiGraphics gui, int x, int y, int w, int h) {
		if (w <= 0 || h <= 0) return;
		gui.blit(id, x, y, w, h, 0, 0, width, height, width, height);
	}
	private void render(GuiGraphics gui, TextureAtlasSprite sprite, Border border, int x, int y, int w, int h, float scale) {
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
		// 四角：目标缩小、源保持原边距，即压缩圆角而非裁切
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
	public float scaleFor(int w, int h) {
		return marginScale(w, h);
	}
	/** 镜像用 U 轴跨度取负实现；不应改用 {@code pose.scale(-1, 1, 1)}，该方式会翻转顶点绕序并被背面剔除，导致图标不可见。 */
	public void renderTintedFlipped(GuiGraphics gui, int x, int y, int w, int h, int color) {
		gui.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
		blitFlipped(gui, x, y, w, h);
		gui.setColor(1F, 1F, 1F, 1F);
	}
	private void blitFlipped(GuiGraphics gui, int x, int y, int w, int h) {
		if (standalone) {
			gui.blit(id, x, y, w, h, width, 0F, -width, height, width, height);
			return;
		}
		var sprite = Minecraft.getInstance().getGuiSprites().getSprite(id);
		var uWidth = Math.round((sprite.getU1() - sprite.getU0()) * UV);
		var vHeight = Math.round((sprite.getV1() - sprite.getV0()) * UV);
		gui.blit(sprite.atlasLocation(), x, y, w, h, sprite.getU1() * UV, sprite.getV0() * UV, -uWidth, vHeight, (int) UV, (int) UV);
	}
}
