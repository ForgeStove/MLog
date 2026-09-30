package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
/**
 * 取色器：预览块 + 色相/饱和度/明度/透明度四条滑条 + 十六进制输入框。
 * <p>尺寸按 0.4 折算（预览 200→80、滑条 370×44→148×18）；输入格与卡片参数格同尺寸
 * （{@link ParamElement} 的 {@code SIZE} 与 {@code PAD}），其下划线按值合法与否取灰或红。
 * <p>拖滑条只改预览，点「确定」写回卡片。
 */
@OnlyIn(Dist.CLIENT)
public class ColorPickerDialog extends LogicDialogScreen {
	/** 预览块边长、滑条宽高与行距、输入格的宽度；由纹理原始尺寸按 0.4 折算。 */
	private static final int PREVIEW = 80, TRACK_W = 148, TRACK_H = 18, TRACK_GAP = 2, FIELD_W = 52;
	/** 各段之间的留白与棋盘格边长。 */
	private static final int GAP = 2, CHECKER = 4;
	/** 预览外框的边框厚度，面板纹中即 2 像素。 */
	private static final int PREVIEW_FRAME = 2;
	/** 色块边到外框纹理外缘的距离：边框，加上两倍于边框的内边距。 */
	private static final int PANE_PAD = PREVIEW_FRAME * 3;
	/** 预览整体在居中位置上再上移的像素数。 */
	private static final int PREVIEW_LIFT = 4;
	/** 色相的六段：红、黄、绿、青、蓝、品红，末段接回红。 */
	private static final int[] HUE_STOPS = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
	/** 确定时把字面量交回卡片。 */
	private final Consumer<String> onPick;
	/** HSV 三分量与透明度，滑条直接改它们。 */
	private float h, s, v, a;
	private final LogicEditBox field;
	private final LogicSlider[] sliders = new LogicSlider[4];
	/** 预览块与输入格的位置，在 {@link #init()} 里算。 */
	private int previewX, previewY, previewSize, fieldX, fieldY;
	/** 滑条改文字时挡住回调，避免自触发。 */
	private boolean updating;
	public ColorPickerDialog(MicroProcessorScreen parent, @Nullable String initial, Consumer<String> onPick) {
		super(parent, LogicFont.text("gui.mlog.pickcolor"));
		this.onPick = onPick;
		var rgba = initial == null ? null : ColorHex.parse(initial);
		// 字段里不是颜色字面量时从界面强调色起步
		if (rgba == null) rgba = new int[]{ARGB32.red(ACCENT), ARGB32.green(ACCENT), ARGB32.blue(ACCENT), 0xFF};
		var hsv = ColorHex.toHsv(rgba[0], rgba[1], rgba[2]);
		h = hsv[0];
		s = hsv[1];
		v = hsv[2];
		a = rgba[3] / 255F;
		// 框比格子窄 PAD*2（同卡片的参数格）；不画原版的方框，下划线在 render 中绘制
		field = new LogicEditBox(0, 0, FIELD_W - ParamElement.PAD * 2, ParamElement.SIZE, Component.literal("rrggbb"));
		field.setBordered(false);
		field.setMaxLength(64);
		// 主题色传白，光标与选中底沿用两个底色灰
		field.setAccent(TEXT);
		field.setResponder(this::onFieldChanged);
		// 色相 0~360，其余三档 0~1
		sliders[0] = new LogicSlider(0, 0, TRACK_W, TRACK_H, 360F, () -> h, value -> setChannel(0, value), this::hueTrack);
		sliders[1] = new LogicSlider(0, 0, TRACK_W, TRACK_H, 1F, () -> s, value -> setChannel(1, value), this::saturationTrack);
		sliders[2] = new LogicSlider(0, 0, TRACK_W, TRACK_H, 1F, () -> v, value -> setChannel(2, value), this::brightnessTrack);
		sliders[3] = new LogicSlider(0, 0, TRACK_W, TRACK_H, 1F, () -> a, value -> setChannel(3, value), this::alphaTrack);
	}
	@Override
	protected void init() {
		super.init();
		fillScreen();
		addBottomButtons(
			new BottomButton("gui.mlog.back", LogicIcons.BACK, b -> onClose()),
			new BottomButton("gui.mlog.ok", LogicIcons.SAVE, b -> {
				onPick.accept(picked());
				onClose();
			})
		);
		// 自上而下：预览（含外框）、四条滑条、输入格。整列居中，空间不足时缩小预览
		var below = GAP + (TRACK_H + TRACK_GAP) * 4 + ParamElement.SIZE;
		previewSize = Mth.clamp(contentBottom() - contentTop() - below - PANE_PAD * 2, 24, PREVIEW);
		var pane = previewSize + PANE_PAD * 2;
		var top = contentTop() + Math.max(0, (contentBottom() - contentTop() - pane - below) / 2 - PREVIEW_LIFT);
		var columnX = (width - TRACK_W) / 2;
		previewX = (width - previewSize) / 2;
		previewY = top + PANE_PAD;
		// 首个滑条在外框底再往下 GAP：PANE_PAD 大于 GAP，紧接排列会覆盖框底
		var firstRowY = top + pane + GAP;
		for (var i = 0; i < sliders.length; i++) {
			sliders[i].setX(columnX);
			sliders[i].setY(firstRowY + i * (TRACK_H + TRACK_GAP));
			addRenderableWidget(sliders[i]);
		}
		// 输入格位于滑条下方并水平居中；框在格内缩进 PAD，文字竖直位置同卡片
		fieldX = (width - FIELD_W) / 2;
		fieldY = firstRowY + (TRACK_H + TRACK_GAP) * 4;
		field.setX(fieldX + ParamElement.PAD);
		field.setY(fieldY + ParamElement.SIZE / 2 - 4);
		addRenderableWidget(field);
		syncField();
	}
	@Override
	public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		renderPanel(gui);
		// 预览块：外框面板纹，内衬棋盘格与当前颜色
		LogicGuiTextures.PANE_SOLID.render(
			gui,
			previewX - PANE_PAD,
			previewY - PANE_PAD,
			previewSize + PANE_PAD * 2,
			previewSize + PANE_PAD * 2
		);
		checker(gui, previewX, previewY, previewSize, previewSize);
		gui.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, argb(s, v, a));
		// 输入框只绘制下划线，框本身由 super.render 绘制
		LogicGuiTextures.UNDERLINE.renderTinted(
			gui,
			fieldX,
			fieldY + ParamElement.SIZE - LogicGuiTextures.UNDERLINE_H,
			FIELD_W,
			LogicGuiTextures.UNDERLINE_H,
			// 值不是颜色字面量时下划线转红，空值同样视为非法
			ColorHex.parse(field.getValue()) == null ? FIELD_LINE_INVALID : FIELD_LINE
		);
		super.render(gui, mouseX, mouseY, partialTick);
	}
	/** 色相轨道：六段彩虹，末段接回红。 */
	private void hueTrack(GuiGraphics gui, int x, int y, int w, int h) {
		for (var seg = 0; seg < HUE_STOPS.length - 1; seg++) {
			var from = x + w * seg / 6;
			horizontal(gui, from, y, x + w * (seg + 1) / 6 - from, h, HUE_STOPS[seg], HUE_STOPS[seg + 1]);
		}
	}
	/** 饱和度轨道：从同明度的灰到全饱和。 */
	private void saturationTrack(GuiGraphics gui, int x, int y, int w, int h) {
		horizontal(gui, x, y, w, h, argb(0F, v, 1F), argb(1F, v, 1F));
	}
	/** 明度轨道：从黑到当前色。 */
	private void brightnessTrack(GuiGraphics gui, int x, int y, int w, int h) {
		horizontal(gui, x, y, w, h, argb(s, 0F, 1F), argb(s, 1F, 1F));
	}
	/** 透明度轨道：棋盘格上从全透明到不透明。 */
	private void alphaTrack(GuiGraphics gui, int x, int y, int w, int h) {
		checker(gui, x, y, w, h);
		horizontal(gui, x, y, w, h, argb(s, v, 0F), argb(s, v, 1F));
	}
	/**
	 * 横向铺渐变。
	 * <p>{@code GuiGraphics.fillGradient} 只能纵向铺，横向按列插值绘制，无须旋转 pose。
	 */
	private static void horizontal(GuiGraphics gui, int x, int y, int w, int h, int from, int to) {
		// 只有一格宽时没有插值可言，直接取起点色
		for (var i = 0; i < w; i++) gui.fill(x + i, y, x + i + 1, y + h, ARGB32.lerp(w == 1 ? 0F : i / (w - 1F), from, to));
	}
	/** 滑条写入某个分量后同步输入格。 */
	private void setChannel(int index, float value) {
		switch (index) {
			case 0 -> h = value;
			case 1 -> s = value;
			case 2 -> v = value;
			default -> a = value;
		}
		syncField();
	}
	/** 输入框改动时反解回滑条；解析不出来就原样留着，不动别的。 */
	private void onFieldChanged(String text) {
		if (updating) return;
		var rgba = ColorHex.parse(text);
		if (rgba == null) return;
		var hsv = ColorHex.toHsv(rgba[0], rgba[1], rgba[2]);
		h = hsv[0];
		s = hsv[1];
		v = hsv[2];
		a = rgba[3] / 255F;
	}
	/** 滑条改动后把输入框的文字跟上，改的时候挡住它自己的回调。 */
	private void syncField() {
		var rgb = ColorHex.fromHsv(h, s, v);
		updating = true;
		field.setValue(ColorHex.formatShort(rgb[0], rgb[1], rgb[2], Math.round(a * 255F)));
		updating = false;
	}
	/** @return 写回卡片的字面量：{@code %} 加八位十六进制，alpha 满时也带着。 */
	private String picked() {
		var rgb = ColorHex.fromHsv(h, s, v);
		return "%" + ColorHex.format(rgb[0], rgb[1], rgb[2], Math.round(a * 255F));
	}
	/** @return 当前色相下、给定饱和度/明度/透明度的 ARGB。 */
	private int argb(float sat, float value, float alpha) {
		var rgb = ColorHex.fromHsv(h, sat, value);
		return (Math.round(alpha * 255F) & 0xFF) << 24 | rgb[0] << 16 | rgb[1] << 8 | rgb[2];
	}
	/** 棋盘格，用于衬托透明度：白与浅灰。 */
	private static void checker(GuiGraphics gui, int x, int y, int w, int h) {
		for (var cy = 0; cy < h; cy += CHECKER)
			for (var cx = 0; cx < w; cx += CHECKER)
				gui.fill(
					x + cx,
					y + cy,
					Math.min(x + cx + CHECKER, x + w),
					Math.min(y + cy + CHECKER, y + h),
					(cx / CHECKER + cy / CHECKER) % 2 == 0 ? 0xFFFFFFFF : 0xFFC0C0C0
				);
	}
}
