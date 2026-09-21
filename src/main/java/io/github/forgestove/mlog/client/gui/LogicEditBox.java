package io.github.forgestove.mlog.client.gui;
import net.minecraft.Util;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.*;
import net.minecraft.util.*;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 界面用的输入框。
 * <p>原版 {@code EditBox} 的字体字段是 {@code private final}，换不掉；但它的 {@code renderWidget}
 * 会把文本交给 {@code formatter} 转成 {@link FormattedCharSequence} 再画，所以构造时把界面字体注进去即可。
 * <p>失焦不用挂全局鼠标监听：编辑器里点空白处、点其它控件、
 * 焦点转移都会走到 {@code LogicCanvas#unfocus}。
 */
@OnlyIn(Dist.CLIENT)
public class LogicEditBox extends EditBox {
	/** 光标闪烁周期。 */
	private static final long BLINK_MS = 300L;
	/** 选中底的 alpha：取类别色的 RGB，再压到这个透明度，看着比色块本身淡。 */
	private static final int HIGHLIGHT_ALPHA = 0x60;
	/** 光标颜色，默认白；所在的语句会把它设成类别色。 */
	private int accent = -1;
	public LogicEditBox(int x, int y, int width, int height, Component message) {
		super(metricsFont(), x, y, width, height, message);
		setup();
	}
	/**
	 * @return 一个"默认字体就是界面字体"的 {@link Font}。
	 * 	<p>{@code EditBox} 定位字符靠的是它自己那个 {@code font} 字段，渲染走的却是 {@code formatter}
	 * 	里的样式字体。两者宽度不是一套的话，点下去光标会落偏。
	 * 	<p>这里把默认字体换成界面字体：渲染不受影响（那些文本自带样式，用不到默认字体），
	 * 	但测宽的路子就对上了。
	 */
	private static Font metricsFont() {
		var set = mc.font.getFontSet(LogicFont.ID);
		return new Font(id -> set, false);
	}
	private void setup() {
		setMaxLength(16384);
		setFormatter(LogicEditBox::format);
		setTextShadow(false);
	}
	/** 切出来的每段文本都得带上界面字体样式，否则会退回默认字体。 */
	private static FormattedCharSequence format(String text, int offset) {
		return FormattedCharSequence.forward(text, Style.EMPTY.withFont(LogicFont.ID));
	}
	public LogicEditBox(int width, int height, Component message) {
		super(metricsFont(), width, height, message);
		setup();
	}
	/** @param color 语句类别色，用于光标与选中底。 */
	public void setAccent(int color) {
		accent = color | 0xFF000000;
	}
	/**
	 * 覆盖原版渲染，只改一处：光标前后两段文本的接缝。
	 * <p>原版拿 {@code drawString} 的返回值当第二段起点，而那个值是截断过的整数，再减 1 之后
	 * 比真实位置<b>偏左 1~2 像素</b>——用等宽位图字体看不出来，换成界面字体就成了
	 * "光标两侧的字被吸过去"。这里按前缀宽度重算，误差不超过 1 像素、方向也不会让两段重叠。
	 */
	@Override
	public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		if (isMouseOver(mouseX, mouseY)) LogicCursor.setIBeam();
		clampScroll();
		var font = metricsFont();
		var value = getValue();
		// 文案恒白：原版选中时前景会一起反色，那是 guiTextHighlight 的 shader 干的
		var color = -1;
		var cursor = getCursorPosition() - displayPos;
		// 截断也要按界面字体算，原版这里用的是它自己那套宽度
		var text = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
		var valid = cursor >= 0 && cursor <= text.length();
		var x = getX();
		var y = getY();
		var highlight = Mth.clamp(highlightPos - displayPos, 0, text.length());
		// 光标所在的 x：既是光标本身的位置，也是选中区域的一端
		var cursorX = x + font.width(text.substring(0, Math.clamp(cursor, 0, text.length())));
		// 选中底先铺——它是背景。原版靠 guiTextHighlight 那个渲染类型待在文字下层，
		// 换成普通 fill 之后画在文字后面就会把字盖住
		if (highlight != cursor) {
			var highlightX = x + font.width(text.substring(0, highlight));
			var rgb = accent & 0xFFFFFF;
			gui.fill(Math.min(cursorX, highlightX), y - 1, Math.max(cursorX, highlightX), y + 10, rgb | HIGHLIGHT_ALPHA << 24);
		}
		// 文本分两段画，接缝在光标处
		if (!text.isEmpty()) {
			var head = valid ? text.substring(0, cursor) : text;
			gui.drawString(font, format(head, displayPos), x, y, color, getTextShadow());
		}
		if (!text.isEmpty() && valid && cursor < text.length())
			gui.drawString(font, format(text.substring(cursor), getCursorPosition()), cursorX, y, color, getTextShadow());
		if (hint != null && text.isEmpty() && !isFocused()) gui.drawString(font, hint, cursorX, y, color, getTextShadow());
		// 光标恒为竖线，末尾也一样；原版在末尾会改画一个下划线表示还能输入
		if (isFocused() && (Util.getMillis() - focusedTime) / BLINK_MS % 2L == 0L && valid)
			gui.fill(RenderType.guiOverlay(), cursorX, y - 1, cursorX + 1, y + 10, accent);
	}
	/**
	 * 校正文本滚动偏移，别让它滚过头。
	 * <p>"让光标可见"之后还有一步——把偏移拉回到
	 * "末尾刚好可见"的位置，防止起点太靠近末尾，比如文本被删短之后。
	 * <p>MC 的 {@code scrollTo} 只做前一半，所以光标停下再改内容，偏移会留在旧位置、右边留白。
	 */
	private void clampScroll() {
		var font = metricsFont();
		var value = getValue();
		if (displayPos >= value.length()) {
			displayPos = 0;
			return;
		}
		var width = getInnerWidth();
		// 最大的合法偏移：从这儿开始，剩下的文本刚好放得下
		var max = 0;
		while (max < value.length() && font.width(value.substring(max)) > width) max++;
		displayPos = Math.min(displayPos, max);
	}
	/**
	 * 按下时按点击位置定位光标。
	 * <p>不调 {@code super}：它内部的字符定位用的是自己那套宽度，和界面字体对不上，点下去会偏。
	 * 顺便把选中区收拢到该点，等价于原版的"点一下取消选中"。
	 */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !isMouseOver(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
		setFocused(true);
		var index = cursorIndexAt(mouseX - getX());
		setCursorPosition(index);
		setHighlightPos(index);
		return true;
	}
	/**
	 * 失焦时把选中区收回到光标处。
	 * <p>原版只在获得焦点时重置闪烁计时，选中区会留着，于是输入框已经失去焦点了，
	 * 那段高亮还挂在那儿。
	 */
	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (!focused) setHighlightPos(getCursorPosition());
	}
	/**
	 * @return {@code relativeX} 处对应的字符下标，落在字符中点之后才算下一个。
	 * 	<p>每步都量整段前缀，而不是逐字累加：{@code Font.width} 内部是 {@code Mth.ceil}，
	 * 	把取过整的宽度一段段加起来，误差会随文本长度越滚越大。
	 */
	private int cursorIndexAt(double relativeX) {
		var font = metricsFont();
		var value = getValue();
		// displayPos 经 AT 暴露：文本超出宽度时它会滚到光标附近，定位要把它算进去
		var start = displayPos;
		for (var i = start; i < value.length(); i++) {
			var left = font.width(value.substring(start, i));
			var right = font.width(value.substring(start, i + 1));
			if (relativeX < (left + right) / 2.0) return i;
		}
		return value.length();
	}
	/** 拖选：只挪光标那头，选中区另一端保持按下时的位置。 */
	public void dragSelectTo(double mouseX) {
		setCursorPosition(cursorIndexAt(mouseX - getX()));
	}
}
