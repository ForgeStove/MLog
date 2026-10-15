package io.github.forgestove.mlog.client.gui;
import net.minecraft.Util;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.*;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 原版 {@code font} 为 {@code private final} 无法替换，故构造时注入界面字体；失焦由 {@code LogicCanvas#unfocus} 覆盖，{@code message} 同时用作提示文本。 */
public class LogicEditBox extends EditBox {
	/** 闪烁半周期，除以它再对 2 取模决定亮灭。 */
	private static final long BLINK_MS = 300L;
	/** 光标与选中底的底色，绘制时按主题色调制。 */
	private static final int CURSOR = 0xFFC1C1BD, SELECTION = 0xFF989AA4;
	/** 主题色，光标与选中底由它调制；默认白即底色原样，卡片传语句类别色，取色器传白。 */
	private int accent = -1;
	public LogicEditBox(int x, int y, int width, int height, Component message) {
		super(metricsFont(), x, y, width, height, message);
		setHint(message);
		setup();
	}
	public LogicEditBox(int width, int height, Component message) {
		super(metricsFont(), width, height, message);
		setHint(message);
		setup();
	}
	/** {@code EditBox} 定位字符用自身 {@code font}、渲染用 {@code formatter} 中的样式字体，故默认字体取界面字体以保证宽度测量与绘制一致。 */
	private static Font metricsFont() {
		var set = mc.font.getFontSet(LogicFont.ID);
		return new Font(id -> set, false);
	}
	// 每段切分文本均需带界面字体样式，否则会退回默认字体
	private static FormattedCharSequence format(String text, int offset) {
		return FormattedCharSequence.forward(text, Style.EMPTY.withFont(LogicFont.ID));
	}
	private void setup() {
		setMaxLength(16384);
		setFormatter(LogicEditBox::format);
		setTextShadow(false);
	}
	public void setAccent(int color) {
		accent = color | 0xFF000000;
	}
	@Override
	public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		if (isMouseOver(mouseX, mouseY)) LogicCursor.setIBeam();
		clampScroll();
		var font = metricsFont();
		var value = getValue();
		// 文本恒为白色：原版选中时前景会被 guiTextHighlight 的 shader 一并反色
		var color = -1;
		var cursor = getCursorPosition() - displayPos;
		// 截断亦按界面字体计算，原版该处使用其自身宽度
		var text = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
		var valid = cursor >= 0 && cursor <= text.length();
		var x = getX();
		var y = getY();
		var highlight = Mth.clamp(highlightPos - displayPos, 0, text.length());
		var cursorX = x + font.width(text.substring(0, Math.clamp(cursor, 0, text.length())));
		// 选中底须先绘制，否则 fill 会遮挡文字
		if (highlight != cursor) {
			var highlightX = x + font.width(text.substring(0, highlight));
			gui.fill(Math.min(cursorX, highlightX), y - 1, Math.max(cursorX, highlightX), y + 10, ARGB32.multiply(SELECTION, accent));
		}
		// 文本分两段绘制，接缝位于光标处
		if (!text.isEmpty()) {
			var head = valid ? text.substring(0, cursor) : text;
			gui.drawString(font, format(head, displayPos), x, y, color, getTextShadow());
		}
		// 原版以 drawString 的返回值作第二段起点，该值截断取整后再减 1，偏左 1~2 像素，故按前缀宽度重算
		if (!text.isEmpty() && valid && cursor < text.length())
			gui.drawString(font, format(text.substring(cursor), getCursorPosition()), cursorX, y, color, getTextShadow());
		if (hint != null && text.isEmpty() && !isFocused()) gui.drawString(font, hint, cursorX, y, color, getTextShadow());
		// 光标恒为竖线，末尾亦然；原版在末尾改绘下划线以表示可继续输入
		if (isFocused() && (Util.getMillis() - focusedTime) / BLINK_MS % 2L == 0L && valid)
			gui.fill(RenderType.guiOverlay(), cursorX, y - 1, cursorX + 1, y + 10, ARGB32.multiply(CURSOR, accent));
	}
	/** 失焦时收回选中区至光标处；原版不重置选中区，会致失焦后高亮仍在。 */
	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (!focused) setHighlightPos(getCursorPosition());
	}
	/** 原版 {@code scrollTo} 只保证光标可见，光标停止后再改内容会使偏移停在旧位置，故须拉回末尾刚好可见处。 */
	private void clampScroll() {
		var font = metricsFont();
		var value = getValue();
		if (displayPos >= value.length()) {
			displayPos = 0;
			return;
		}
		var width = getInnerWidth();
		// 最大的合法偏移：该位置起的剩余文本刚好放得下
		var max = 0;
		while (max < value.length() && font.width(value.substring(max)) > width) max++;
		displayPos = Math.min(displayPos, max);
	}
	/** 整体测量前缀而非逐字累加：{@code Font.width} 内部为 {@code Mth.ceil}，累加取整后的宽度会使误差随文本长度增长；长度经 AT 暴露。 */
	private int cursorIndexAt(double relativeX) {
		var font = metricsFont();
		var value = getValue();
		// displayPos 经 AT 暴露：文本超出宽度时会滚动至光标附近，定位需计入
		var start = displayPos;
		for (var i = start; i < value.length(); i++) {
			var left = font.width(value.substring(start, i));
			var right = font.width(value.substring(start, i + 1));
			if (relativeX < (left + right) / 2.0) return i;
		}
		return value.length();
	}
	@Override
	protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
		dragSelectTo(mouseX);
	}
	/** 不调用 {@code super}：其字符定位按自身宽度，与界面字体不一致会致点击偏移；点击音在 {@code playDownSound} 的空实现处播放。 */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !isMouseOver(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
		LogicSounds.button();
		setFocused(true);
		var index = cursorIndexAt(mouseX - getX());
		setCursorPosition(index);
		setHighlightPos(index);
		return true;
	}
	public void dragSelectTo(double mouseX) {
		setCursorPosition(cursorIndexAt(mouseX - getX()));
	}
}
