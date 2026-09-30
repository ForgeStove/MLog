package io.github.forgestove.mlog.client.gui;
import net.minecraft.Util;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.*;
import net.minecraft.util.*;
import net.minecraft.util.FastColor.ARGB32;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 界面输入框。
 * <p>原版 {@code EditBox} 的字体字段为 {@code private final} 无法替换；但其 {@code renderWidget}
 * 将文本经 {@code formatter} 转为 {@link FormattedCharSequence} 后绘制，故构造时注入界面字体即可。
 * <p>失焦无需全局鼠标监听：点击空白处、点击其它控件与焦点转移均会走到 {@code LogicCanvas#unfocus}。
 */
@OnlyIn(Dist.CLIENT)
public class LogicEditBox extends EditBox {
	/** 光标闪烁周期。 */
	private static final long BLINK_MS = 300L;
	/** 光标与选中底的底色，绘制时按主题色调制。 */
	private static final int CURSOR = 0xFFC1C1BD, SELECTION = 0xFF989AA4;
	/** 主题色，光标与选中底由它调制；默认白，即底色原样。 */
	private int accent = -1;
	public LogicEditBox(int x, int y, int width, int height, Component message) {
		super(metricsFont(), x, y, width, height, message);
		setHint(message);
		setup();
	}
	/**
	 * @return 默认字体为界面字体的 {@link Font}。
	 * 	<p>{@code EditBox} 定位字符依据其自身的 {@code font} 字段，渲染却依据 {@code formatter}
	 * 	中的样式字体；两者宽度不一致时点击定位会偏移。
	 * 	<p>此处将默认字体设为界面字体：渲染不受影响（文本自带样式，不使用默认字体），
	 * 	宽度测量则与绘制一致。
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
	/** 每段切分文本均需带界面字体样式，否则会退回默认字体。 */
	private static FormattedCharSequence format(String text, int offset) {
		return FormattedCharSequence.forward(text, Style.EMPTY.withFont(LogicFont.ID));
	}
	public LogicEditBox(int width, int height, Component message) {
		super(metricsFont(), width, height, message);
		setHint(message);
		setup();
	}
	/**
	 * @param color 主题色，光标与选中底由它调制；卡片传语句类别色，取色器传白。
	 */
	public void setAccent(int color) {
		accent = color | 0xFF000000;
	}
	/**
	 * 覆盖原版渲染，仅改一处：光标前后两段文本的接缝。
	 * <p>原版以 {@code drawString} 的返回值作为第二段起点，该值经截断取整后再减 1，
	 * 较真实位置偏左 1~2 像素；等宽位图字体下不可见，换为界面字体后表现为光标两侧文字被吸向光标。
	 * 此处按前缀宽度重算，误差不超过 1 像素，且不会使两段重叠。
	 */
	@Override
	public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		if (isMouseOver(mouseX, mouseY)) LogicCursor.setIBeam();
		clampScroll();
		var font = metricsFont();
		var value = getValue();
		// 文本恒为白色：原版选中时前景会被 guiTextHighlight 的 shader 一并反色
		var color = -1;
		var cursor = getCursorPosition() - displayPos;
		// 截断亦按界面字体计算，原版此处使用其自身宽度
		var text = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
		var valid = cursor >= 0 && cursor <= text.length();
		var x = getX();
		var y = getY();
		var highlight = Mth.clamp(highlightPos - displayPos, 0, text.length());
		// 光标所在 x：既是光标位置，也是选中区域的一端
		var cursorX = x + font.width(text.substring(0, Math.clamp(cursor, 0, text.length())));
		// 选中底先绘制：原版由 guiTextHighlight 渲染类型置于文字下层，
		// 改用普通 fill 后若后绘制将遮挡文字
		if (highlight != cursor) {
			var highlightX = x + font.width(text.substring(0, highlight));
			// 选中底不透明，文字绘制在其上
			gui.fill(
				Math.min(cursorX, highlightX),
				y - 1,
				Math.max(cursorX, highlightX),
				y + 10,
				ARGB32.multiply(SELECTION, accent)
			);
		}
		// 文本分两段绘制，接缝位于光标处
		if (!text.isEmpty()) {
			var head = valid ? text.substring(0, cursor) : text;
			gui.drawString(font, format(head, displayPos), x, y, color, getTextShadow());
		}
		if (!text.isEmpty() && valid && cursor < text.length())
			gui.drawString(font, format(text.substring(cursor), getCursorPosition()), cursorX, y, color, getTextShadow());
		if (hint != null && text.isEmpty() && !isFocused()) gui.drawString(font, hint, cursorX, y, color, getTextShadow());
		// 光标恒为竖线，末尾亦然；原版在末尾改绘下划线以表示可继续输入
		if (isFocused() && (Util.getMillis() - focusedTime) / BLINK_MS % 2L == 0L && valid)
			gui.fill(RenderType.guiOverlay(), cursorX, y - 1, cursorX + 1, y + 10, ARGB32.multiply(CURSOR, accent));
	}
	/**
	 * 校正文本滚动偏移，避免越界。
	 * <p>使光标可见之后还需将偏移拉回末尾刚好可见的位置，以防起点过于接近末尾，例如文本被删短后。
	 * <p>MC 的 {@code scrollTo} 只完成前一步，故光标停止后再修改内容时偏移会停留在旧位置，右侧出现留白。
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
	 * <p>不调用 {@code super}：其内部字符定位使用自身宽度，与界面字体不一致，点击会偏移。
	 * 同时将选中区收拢至该点，等价于原版的点击取消选中。
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
	 * 失焦时将选中区收回至光标处。
	 * <p>原版仅在获得焦点时重置闪烁计时，选中区会保留，导致输入框已失焦而高亮仍在。
	 */
	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (!focused) setHighlightPos(getCursorPosition());
	}
	/**
	 * @return {@code relativeX} 对应的字符下标，超过字符中点方计入下一个。
	 * 	<p>每步测量整段前缀而非逐字累加：{@code Font.width} 内部为 {@code Mth.ceil}，
	 * 	累加取整后的宽度会使误差随文本长度增长。
	 */
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
	/**
	 * 拖动框选：仅移动光标一端，选中区另一端保持按下时的位置。
	 * <p>原版 {@code EditBox} 无框选，界面输入框经此接入。
	 */
	@Override
	protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
		dragSelectTo(mouseX);
	}
	/** 拖选：仅移动光标一端，选中区另一端保持按下时的位置。 */
	public void dragSelectTo(double mouseX) {
		setCursorPosition(cursorIndexAt(mouseX - getX()));
	}
}
