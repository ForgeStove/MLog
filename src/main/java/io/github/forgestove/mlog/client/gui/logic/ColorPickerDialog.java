package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.component.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
/** 拖滑条只改预览，确定时才写回卡片。 */
public class ColorPickerDialog extends LogicDialogScreen {
	/** 均按纹理原始尺寸的 0.4 折算；轨道与把手同为 16 高。 */
	private static final int PREVIEW = 80, TRACK_W = 148, TRACK_H = 16, TRACK_GAP = 2, FIELD_W = 52;
	private static final int GAP = 2;
	/** 面板纹自带的边框厚度即 2 像素。 */
	private static final int PREVIEW_FRAME = 2;
	/** 色块到外框纹理外缘的距离，为边框厚度的三倍。 */
	private static final int PANE_PAD = PREVIEW_FRAME * 3;
	private static final int PREVIEW_LIFT = 4;
	/** 色相六段，末段接回红。 */
	private static final int[] HUE_STOPS = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
	private final Consumer<String> onPick;
	private final LogicEditBox field;
	private final LogicSlider[] sliders = new LogicSlider[4];
	private float h, s, v, a;
	private int previewX, previewY, previewSize;
	/** 滑条同步输入框时屏蔽回调，避免自触发。 */
	private boolean updating;
	public ColorPickerDialog(ProcessorScreen parent, @Nullable String initial, Consumer<String> onPick) {
		super(parent, LogicFont.text("mlog.gui.pickcolor"));
		this.onPick = onPick;
		var rgba = initial == null ? null : ColorHex.parse(initial);
		// 字段非颜色字面量时以界面强调色初始化
		if (rgba == null) rgba = new int[]{ARGB32.red(ACCENT), ARGB32.green(ACCENT), ARGB32.blue(ACCENT), 0xFF};
		var hsv = ColorHex.toHsv(rgba[0], rgba[1], rgba[2]);
		h = hsv[0];
		s = hsv[1];
		v = hsv[2];
		a = rgba[3] / 255F;
		// 框比输入格窄 PAD*2；原版边框关闭，下划线另行绘制
		field = new LogicEditBox(0, 0, FIELD_W, LogicFont.LINE_H, Component.literal("rrggbb"));
		field.setBordered(false);
		field.setMaxLength(64);
		// 主题色传白，光标与选中底按 accent 调制后保持底色灰
		field.setAccent(TEXT);
		field.setUnderlineColor(FIELD_LINE);
		field.setUnderlined(true);
		field.setResponder(this::onFieldChanged);
		// 色相 0~360，其余三档 0~1
		sliders[0] = new LogicSlider(0, 0, TRACK_W, TRACK_H, 360F, () -> h, value -> setChannel(0, value), this::hueTrack);
		sliders[1] = new LogicSlider(0, 0, TRACK_W, TRACK_H, 1F, () -> s, value -> setChannel(1, value), this::saturationTrack);
		sliders[2] = new LogicSlider(0, 0, TRACK_W, TRACK_H, 1F, () -> v, value -> setChannel(2, value), this::brightnessTrack);
		sliders[3] = new LogicSlider(0, 0, TRACK_W, TRACK_H, 1F, () -> a, value -> setChannel(3, value), this::alphaTrack);
	}
	/** {@code GuiGraphics.fillGradient} 仅支持纵向渐变，横向按列插值绘制。 */
	private static void horizontal(GuiGraphics gui, int x, int y, int w, int h, int from, int to) {
		// 仅一格宽时无从插值，取起点色
		for (var i = 0; i < w; i++) gui.fill(x + i, y, x + i + 1, y + h, ARGB32.lerp(w == 1 ? 0F : i / (w - 1F), from, to));
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		renderPanel(gui);
		LogicGuiTextures.PANE_SOLID.render(
			gui,
			previewX - PANE_PAD,
			previewY - PANE_PAD,
			previewSize + PANE_PAD * 2,
			previewSize + PANE_PAD * 2
		);
		LogicGuiTextures.ALPHA_BG.render(gui, previewX, previewY, previewSize, previewSize);
		gui.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, argb(s, v, a));
		// 值不是颜色字面量时下划线转红，空值同样视为非法；光标与选中底仍按 accent 保持灰/红
		var valid = ColorHex.parse(field.getValue()) != null;
		field.setAccent(valid ? TEXT : FIELD_LINE_INVALID);
		field.setUnderlineColor(valid ? FIELD_LINE : FIELD_LINE_INVALID);
		super.render(gui, mouseX, mouseY, partialTick);
	}
	@Override
	protected void init() {
		super.init();
		addBottomButtons(
			new BottomButton("mlog.gui.back", LogicIcons.BACK, b -> onClose()), new BottomButton(
				"mlog.gui.ok", LogicIcons.SAVE, b -> {
				onPick.accept(picked());
				onClose();
			}
			)
		);
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
		var fieldX = (width - FIELD_W) / 2;
		var fieldY = firstRowY + (TRACK_H + TRACK_GAP) * 4;
		field.setX(fieldX);
		field.setY(fieldY);
		field.setHeight(ParamElement.SIZE);
		addRenderableWidget(field);
		syncField();
	}
	@Override
	public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {}
	/** 色相轨道：{@link #HUE_STOPS} 的末段接回红，故只铺前六段。 */
	private void hueTrack(GuiGraphics gui, int x, int y, int w, int h) {
		for (var seg = 0; seg < HUE_STOPS.length - 1; seg++) {
			var from = x + w * seg / 6;
			horizontal(gui, from, y, x + w * (seg + 1) / 6 - from, h, HUE_STOPS[seg], HUE_STOPS[seg + 1]);
		}
	}
	private void saturationTrack(GuiGraphics gui, int x, int y, int w, int h) {
		horizontal(gui, x, y, w, h, argb(0F, v, 1F), argb(1F, v, 1F));
	}
	private void brightnessTrack(GuiGraphics gui, int x, int y, int w, int h) {
		horizontal(gui, x, y, w, h, argb(s, 0F, 1F), argb(s, 1F, 1F));
	}
	private void alphaTrack(GuiGraphics gui, int x, int y, int w, int h) {
		LogicGuiTextures.ALPHA_LINE.render(gui, x, y, w, h);
		horizontal(gui, x, y, w, h, argb(s, v, 0F), argb(s, v, 1F));
	}
	private void setChannel(int index, float value) {
		switch (index) {
			case 0 -> h = value;
			case 1 -> s = value;
			case 2 -> v = value;
			default -> a = value;
		}
		syncField();
	}
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
	private void syncField() {
		var rgb = ColorHex.fromHsv(h, s, v);
		updating = true;
		field.setValue(ColorHex.formatShort(rgb[0], rgb[1], rgb[2], Math.round(a * 255F)));
		updating = false;
	}
	/** 写回格式为 {@code %} 加八位十六进制，alpha 为满时也保留。 */
	private String picked() {
		var rgb = ColorHex.fromHsv(h, s, v);
		return "%" + ColorHex.format(rgb[0], rgb[1], rgb[2], Math.round(a * 255F));
	}
	private int argb(float sat, float value, float alpha) {
		var rgb = ColorHex.fromHsv(h, sat, value);
		return (Math.round(alpha * 255F) & 0xFF) << 24 | rgb[0] << 16 | rgb[1] << 8 | rgb[2];
	}
}
