package io.github.forgestove.mlog.client.gui.component;
import io.github.forgestove.mlog.client.gui.*;
import net.minecraft.Util;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.*;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
public class LogicEditBox extends EditBox implements Clippable {
	/** 光标闪烁周期的一半与连击判定窗口，单位毫秒。 */
	private static final long BLINK_MS = 300L, TAP_MS = 300;
	/** 光标与选中底的底色，绘制时按主题色调制。 */
	private static final int CURSOR = 0xFFC1C1BD, SELECTION = 0xFF989AA4;
	private static final int INSET = 4;
	private static final int MAX_HISTORY = 64;
	/** {@code EditBox} 定位字符用自身 {@code font}、渲染用 {@code formatter} 的字体，故统一取界面字体；查找延迟，资源重载后仍取到新的 FontSet。 */
	private static final Font METRICS_FONT = new Font(id -> mc.font.getFontSet(LogicFont.ID), false);
	private final Deque<String> undoStack = new ArrayDeque<>(), redoStack = new ArrayDeque<>();
	private long lastTap;
	private int accent = -1;
	private int tapCount;
	/** 按下后尚未松开；松开时仍在输入框上才播放点击音。 */
	private boolean armed;
	private String lastValue = "";
	/** 原版没有 {@code getHighlightPos}，在 setter 里自己留一份，供替换选区时用。 */
	private int highlightPosition;
	private int maxLengthValue = 16384;
	private boolean programmatic;
	private boolean underlined;
	private boolean collapseHighlight;
	private int clipLeft, clipTop, clipRight, clipBottom;
	private boolean clipped;
	public LogicEditBox(int width, int height, Component message) {
		super(metricsFont(), width, height, message);
		setHint(message);
		setup();
	}
	public LogicEditBox(int x, int y, int width, int height, Component message) {
		super(metricsFont(), x, y, width, height, message);
		setHint(message);
		setup();
	}
	private static Font metricsFont() {
		return METRICS_FONT;
	}
	/** 每段文本都要带界面字体样式，否则会退回默认字体。 */
	private static FormattedCharSequence format(String text, int offset) {
		return FormattedCharSequence.forward(text, Style.EMPTY.withFont(LogicFont.ID));
	}
	/** 逐字绘制的精确推进量；{@code Font.width} 向上取整，光标会偏右。 */
	private static float advance(Font font, String text) {
		return font.getSplitter().stringWidth(format(text, 0));
	}
	private void setup() {
		setMaxLength(16384);
		setFormatter(LogicEditBox::format);
		setTextShadow(false);
	}
	/** 主题色同时调制光标、选中底与下划线。 */
	public void setAccent(int color) {
		accent = color | 0xFF000000;
	}
	public void setUnderlined(boolean enabled) {
		underlined = enabled;
	}
	/** responder 是输入、删除、粘贴等文本变化的共同出口，历史在此收集。 */
	@Override
	public void setResponder(Consumer<String> responder) {
		super.setResponder(value -> {
			if (!programmatic) record(value);
			responder.accept(value);
		});
	}
	/** 程序化写入与撤销/重做不记录历史。 */
	@Override
	public void setValue(String text) {
		programmatic = true;
		try {
			super.setValue(text);
		} finally {
			programmatic = false;
		}
		lastValue = getValue();
	}
	/**
	 * 原版插入时按聊天字符过滤，{@code §} 会被丢弃；这里的文本不是聊天消息，除控制字符外一律保留。
	 * 同时不走原版过滤路径，历史记录在此补写。
	 */
	@Override
	public void insertText(String text) {
		if (!canConsumeInput()) return;
		var value = getValue();
		var start = Math.min(getCursorPosition(), highlightPosition);
		var end = Math.max(getCursorPosition(), highlightPosition);
		var clean = new StringBuilder(text.length());
		text.codePoints().filter(cp -> cp >= ' ' && cp != 127).forEach(clean::appendCodePoint);
		var inserted = clean.toString();
		// 空串用于删除选区，只有既无选区又无可插入内容时才无事可做
		if (inserted.isEmpty() && start == end) return;
		var room = maxLengthValue - (value.length() - (end - start));
		if (room <= 0) return;
		if (inserted.length() > room) {
			inserted = inserted.substring(0, room);
			// 截断点落在代理对中间时整体丢弃该字符
			if (Character.isHighSurrogate(inserted.charAt(inserted.length() - 1))) inserted = inserted.substring(0, inserted.length() - 1);
			if (inserted.isEmpty()) return;
		}
		var next = new StringBuilder(value).replace(start, end, inserted).toString();
		setValue(next);
		if (!value.equals(next)) {
			undoStack.push(value);
			if (undoStack.size() > MAX_HISTORY) undoStack.removeLast();
			redoStack.clear();
		}
		setCursorPosition(start + inserted.length());
		setHighlightPos(getCursorPosition());
	}
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (isFocused() && Screen.hasControlDown() && !Screen.hasShiftDown()) {
			if (keyCode == GLFW.GLFW_KEY_Z) {
				undo();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_Y) {
				redo();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (!canConsumeInput() || codePoint < ' ' || codePoint == 127) return false;
		insertText(Character.toString(codePoint));
		return true;
	}
	@Override
	public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		if (underlined) LogicGuiTextures.UNDERLINE.renderTinted(
			gui,
			getX(),
			getY() + getHeight() - LogicGuiTextures.UNDERLINE_H,
			getWidth(),
			LogicGuiTextures.UNDERLINE_H,
			accent
		);
		// 真正失焦后再收回选中区；先失焦后聚焦的点击已在 setFocused 里取消
		if (collapseHighlight && !isFocused()) {
			collapseHighlight = false;
			setHighlightPos(getCursorPosition());
		}
		if (isMouseOver(mouseX, mouseY)) LogicCursor.setIBeam();
		clampScroll();
		var font = metricsFont();
		var value = getValue();
		// 文本恒为白色：原版选中时前景会被 guiTextHighlight 的 shader 一并反色
		var color = -1;
		var cursor = getCursorPosition() - displayPos;
		var text = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
		var valid = cursor >= 0 && cursor <= text.length();
		var x = getX() + INSET;
		// 文字在控件高度内垂直居中，控件可占满整行以便点击
		var y = getY() + (getHeight() - LogicFont.LINE_H) / 2;
		var highlight = Mth.clamp(highlightPos - displayPos, 0, text.length());
		// 光标画在字符边界左侧一列，与 MC 的编辑框一致
		var cursorX = x + (int) advance(font, text.substring(0, Math.clamp(cursor, 0, text.length())));
		// 选中底须先绘制，否则会遮挡文字
		if (highlight != cursor) {
			var highlightX = x + (int) advance(font, text.substring(0, highlight));
			gui.fill(
				Math.min(cursorX, highlightX),
				y,
				Math.max(cursorX, highlightX),
				y + LogicFont.LINE_H,
				ARGB32.multiply(SELECTION, accent)
			);
		}
		// 整段一次绘制：按光标切成两段时，前缀宽度与逐字推进的取整不一致，后半段会右移
		if (!text.isEmpty()) gui.drawString(font, format(text, displayPos), x, y, color, getTextShadow());
		if (hint != null && text.isEmpty() && !isFocused()) gui.drawString(font, hint, cursorX, y, LogicColors.HINT, getTextShadow());
		if (isFocused() && (Util.getMillis() - focusedTime) / BLINK_MS % 2L == 0L && valid)
			gui.fill(RenderType.guiOverlay(), cursorX, y, cursorX + 1, y + LogicFont.LINE_H, ARGB32.multiply(CURSOR, accent));
	}
	@Override
	public void setMaxLength(int length) {
		super.setMaxLength(length);
		maxLengthValue = length;
	}
	@Override
	public void setHighlightPos(int position) {
		super.setHighlightPos(position);
		highlightPosition = Math.clamp(position, 0, getValue().length());
	}
	/** 失焦时延迟收回选中区，避免抹掉同一次点击里的双击选词。 */
	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		collapseHighlight = !focused;
	}
	@Override
	public int getInnerWidth() {
		return Math.max(0, getWidth() - INSET * 2);
	}
	private void record(String value) {
		if (value.equals(lastValue)) return;
		undoStack.push(lastValue);
		if (undoStack.size() > MAX_HISTORY) undoStack.removeLast();
		redoStack.clear();
		lastValue = value;
	}
	@Override
	public void setClip(int left, int top, int right, int bottom) {
		clipped = true;
		clipLeft = left;
		clipTop = top;
		clipRight = right;
		clipBottom = bottom;
	}
	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return super.isMouseOver(mouseX, mouseY) && (
			!clipped
				|| mouseX >= clipLeft && mouseX < clipRight && mouseY >= clipTop && mouseY < clipBottom
		);
	}
	/** 不调用 {@code super}：其字符定位按自身宽度，与界面字体不一致会致点击偏移。 */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		armed = button == 0 && isMouseOver(mouseX, mouseY);
		if (!armed) return super.mouseClicked(mouseX, mouseY, button);
		setFocused(true);
		var index = cursorIndexAt(mouseX - getX() - INSET);
		var now = Util.getMillis();
		if (now - lastTap > TAP_MS) tapCount = 0;
		tapCount = (tapCount + 1) % 4;
		lastTap = now;
		switch (tapCount) {
			// 双击选词、三击全选，第四次点击恢复为普通定位
			case 2 -> {
				var word = wordAt(index);
				setCursorPosition(word[1]);
				setHighlightPos(word[0]);
			}
			case 3 -> {
				setCursorPosition(getValue().length());
				setHighlightPos(0);
			}
			default -> {
				setCursorPosition(index);
				setHighlightPos(index);
			}
		}
		return true;
	}
	private void undo() {
		if (undoStack.isEmpty()) return;
		redoStack.push(lastValue);
		setValue(undoStack.pop());
	}
	public void dragSelectTo(double mouseX) {
		setCursorPosition(cursorIndexAt(mouseX - getX() - INSET));
	}
	private void redo() {
		if (redoStack.isEmpty()) return;
		undoStack.push(lastValue);
		setValue(redoStack.pop());
	}
	@Override
	protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
		dragSelectTo(mouseX);
	}
	/** 点击音在松开时播放，与其余按钮一致。 */
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		var trigger = armed && button == 0 && isMouseOver(mouseX, mouseY);
		armed = false;
		if (trigger) LogicSounds.button();
		return super.mouseReleased(mouseX, mouseY, button);
	}
	/** 原版 {@code scrollTo} 只保证光标可见，光标停止后再改内容会使偏移停在旧位置。 */
	private void clampScroll() {
		var font = metricsFont();
		var value = getValue();
		if (displayPos >= value.length()) {
			displayPos = 0;
			return;
		}
		var width = getInnerWidth();
		var max = 0;
		while (max < value.length() && font.width(value.substring(max)) > width) max++;
		displayPos = Math.min(displayPos, max);
	}
	/** 整体测量前缀而非逐字累加，避免取整误差随文本增长。 */
	private int cursorIndexAt(double relativeX) {
		var font = metricsFont();
		var value = getValue();
		// 文本超出宽度时会滚动，定位需计入 displayPos
		var start = displayPos;
		for (var i = start; i < value.length(); i++) {
			var left = advance(font, value.substring(start, i));
			var right = advance(font, value.substring(start, i + 1));
			if (relativeX < (left + right) / 2F) return i;
		}
		return value.length();
	}
	/** 光标下的单词范围；文本右侧空白处为全文。 */
	private int[] wordAt(int index) {
		var value = getValue();
		var at = Math.clamp(index, 0, value.length());
		if (at >= value.length()) return new int[]{0, value.length()};
		var left = at;
		var right = at;
		while (left > 0 && Character.isLetterOrDigit(value.charAt(left - 1))) left--;
		while (right < value.length() && Character.isLetterOrDigit(value.charAt(right))) right++;
		return new int[]{left, right};
	}
}
