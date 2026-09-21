package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.*;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.LStatements.JumpStatement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 一张语句卡片：类别配色的边框与头部栏 + 参数区。 */
@OnlyIn(Dist.CLIENT)
public class StatementCard {
	/** 卡片长宽比。 */
	private static final float ASPECT = 12.85F;
	/** 卡片内边距、参数间距、头部内容左边距。 */
	private static final int PAD = 4, GAP = 5, HEADER_X = 2;
	/** 参数整体比头部栏下沿抬高这么多。 */
	private static final int RAISE = 2;
	/** 头部小按钮的尺寸。按钮紧贴卡片右边缘，彼此也不留缝。 */
	private static final int BTN = 13;
	/** 头部栏高度与按钮一致，按钮正好占满整栏。 */
	public static final int HEADER_H = BTN;
	/**
	 * {@code end} / {@code stop} 没有参数，按长宽比撑开后参数区会剩一大块黑底。
	 * <p>压到这个高度后黑区正好是一条 2 像素的细边：头部栏 13，九宫格底边缩到 4，13 + 2 + 4 = 19。
	 */
	private static final int THIN_H = 17;
	private final List<ParamElement> elements = new ArrayList<>();
	public MLogStatement statement;
	public int index;
	public int x, y, width = 100, height;
	/** 没被压扁时的高度，只拿来算九宫格边框的粗细——压扁的卡片边框不该跟着变细。 */
	private int fullH;
	/** 算子等参数变化会改变布局，置位后在下次布局时重建元素。 */
	private boolean dirty = true;
	public StatementCard(MLogStatement statement) {
		this.statement = statement;
	}
	/** 语句还是原来那条，但参数个数可能变了（换算子等），下次布局时重建控件。 */
	public void invalidate() {
		dirty = true;
	}
	/** @return 卡片上的跳转节点，不是 {@code jump} 语句则返回 {@code null}。 */
	public @Nullable Node node() {
		for (var element : elements) if (element instanceof Node node) return node;
		return null;
	}
	/** 外部改动了参数（如从剪贴板载入）后调用，让输入框重新取值。 */
	public void sync() {
		for (var element : elements) element.sync();
	}
	public void unfocus() {
		for (var element : elements) element.unfocus();
	}
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		for (var element : elements) if (element.keyPressed(keyCode, scanCode, modifiers)) return true;
		return false;
	}
	public boolean charTyped(char codePoint, int modifiers) {
		for (var element : elements) if (element.charTyped(codePoint, modifiers)) return true;
		return false;
	}
	public @Nullable ParamElement elementAt(double mouseX, double mouseY) {
		for (var element : elements) if (element.isOver(mouseX, mouseY)) return element;
		return null;
	}
	/** @return 命中的头部按钮，没有则返回 {@code null}。 */
	public @Nullable HeaderAction headerActionAt(double mouseX, double mouseY) {
		var by = y;
		if (mouseY < by || mouseY >= by + BTN) return null;
		for (var action : HeaderAction.values()) {
			var bx = buttonX(action);
			if (mouseX >= bx && mouseX < bx + BTN) return action;
		}
		return null;
	}
	private int buttonX(HeaderAction action) {
		var delete = x + width - BTN;
		return switch (action) {
			case DELETE -> delete;
			case COPY -> delete - BTN;
			case ADD -> delete - BTN * 2;
		};
	}
	public boolean isOver(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
	/** 按可用宽度排列参数并算出卡片高度。 */
	public void layout(int width) {
		if (dirty || elements.isEmpty()) rebuildElements();
		this.width = width;
		var left = x + PAD;
		var right = x + width - PAD;
		// 先断行
		var rows = new ArrayList<List<ParamElement>>();
		List<ParamElement> current = new ArrayList<>();
		var cursor = left;
		for (var element : elements) {
			// 弹性元素的宽度要看本行剩下多少，断行时先按 0 算，免得它把整行顶满
			var w = element.stretch() ? 0 : element.width();
			if (!current.isEmpty() && cursor + w > right) {
				rows.add(current);
				current = new ArrayList<>();
				cursor = left;
			}
			current.add(element);
			cursor += w + GAP;
		}
		if (!current.isEmpty()) rows.add(current);
		// 再定位：含 spacer 的行把其后的元素右对齐
		for (var r = 0; r < rows.size(); r++) {
			var items = rows.get(r);
			var rowY = y + HEADER_H + PAD - RAISE + r * (ParamElement.SIZE + GAP);
			// 弹性元素吃掉本行的剩余空间，其余元素仍按自身宽度排
			for (var element : items) {
				if (!element.stretch()) continue;
				var used = 0;
				for (var other : items) if (other != element) used += other.width() + GAP;
				element.setWidth(Math.max(0, right - left - used));
				break;
			}
			var spacerAt = -1;
			for (var i = 0; i < items.size(); i++)
				if (items.get(i) instanceof Spacer) {
					spacerAt = i;
					break;
				}
			var head = spacerAt < 0 ? items : items.subList(0, spacerAt);
			cursor = left;
			for (var element : head) {
				element.setPosition(cursor, rowY);
				cursor += element.width() + GAP;
			}
			if (spacerAt < 0) continue;
			var tail = items.subList(spacerAt + 1, items.size());
			var tailWidth = 0;
			for (var element : tail) tailWidth += element.width() + GAP;
			cursor = Math.max(left, right - tailWidth + GAP);
			for (var element : tail) {
				element.setPosition(cursor, rowY);
				cursor += element.width() + GAP;
			}
		}
		var contentH = HEADER_H + PAD * 2 + rows.size() * ParamElement.SIZE + Math.max(0, rows.size() - 1) * GAP;
		// 按长宽比撑开；参数行太多时以内容为准，免得被裁掉
		fullH = Math.max(contentH, Math.round(width / ASPECT));
		// 参数区空的语句（`end` / `stop` / 解析不出来的占位）用瘦卡片，不按长宽比撑开
		height = elements.isEmpty() ? THIN_H : fullH;
	}
	private void rebuildElements() {
		var old = elements.stream().filter(Picker.class::isInstance).toList();
		elements.clear();
		statement.build(new ElementBuilder(elements, statement.category().color, statement));
		// 参数个数没变的话，把上次弹窗看到哪儿接回去。选中一个值就会走到这儿重建控件，
		// 不接的话每次选完再打开都会回到第一组、滚回顶部
		var now = elements.stream().filter(Picker.class::isInstance).toList();
		if (old.size() == now.size()) for (var i = 0; i < now.size(); i++) ((Picker) now.get(i)).adopt((Picker) old.get(i));
		dirty = false;
	}
	public void render(GuiGraphics gui, int mouseX, int mouseY) {
		var color = statement.category().color;
		// 悬停在头部栏上就给手型——它整条都能按下拖动
		if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEADER_H) LogicCursor.setHand();
		// 绘制顺序：投影 → 半透明黑底 → 头部实心条 → 类别色边框
		gui.fill(x + 2, y + 2, x + width + 2, y + height + 2, SHADOW);
		gui.fill(x, y, x + width, y + height, CARD_BG);
		gui.fill(x, y, x + width, y + HEADER_H, color);
		LogicGuiTextures.WHITE_PANE.renderTinted(gui, x, y, width, height, color, LogicGuiTextures.WHITE_PANE.scaleFor(width, fullH));
		LogicFont.drawOutlined(gui, headerText(), x + HEADER_X, y + (HEADER_H - 8) / 2, color);
		var address = LogicFont.literal(String.valueOf(index));
		var addressX = buttonX(HeaderAction.ADD) - mc.font.width(address);
		LogicFont.drawOutlined(gui, address, addressX, y + (HEADER_H - 8) / 2, color);
		for (var action : HeaderAction.values()) renderHeaderButton(gui, action);
		for (var element : elements) element.render(gui, mouseX, mouseY);
	}
	/** {@code jump} 在标题后接上跳转目标。 */
	private Component headerText() {
		var name = LogicFont.text(statement.nameKey());
		if (statement instanceof JumpStatement jump && jump.dest != null) return name.copy().append(" -> " + jump.destIndex);
		return name;
	}
	private void renderHeaderButton(GuiGraphics gui, HeaderAction action) {
		var bx = buttonX(action);
		var by = y;
		// 不做悬停高亮；光标由头部栏那处统一处理
		// 图标宽度和按钮宽度的差可能是奇数，直接用整数除法会整体左偏半像素
		action.icon.render(gui, bx + Math.round((BTN - action.icon.width()) / 2F), LogicIcons.centerY(by, BTN), HEADER_TEXT);
	}
	/** 头部按钮。 */
	public enum HeaderAction {
		ADD(LogicIcons.ADD),
		COPY(LogicIcons.COPY),
		DELETE(LogicIcons.CANCEL),
		;
		final LogicIcons icon;
		HeaderAction(LogicIcons icon) {
			this.icon = icon;
		}
	}
	/** 把 {@link Table} 的声明收集成参数元素。 */
	private record ElementBuilder(List<ParamElement> target, int color, MLogStatement statement) implements Table {
		@Override
		public void label(String text) {
			var label = new ParamElement.Label(LogicFont.literal(text), TEXT, text);
			statement.param(label);
			target.add(label);
		}
		@Override
		public void labelKey(String key) {
			var label = new ParamElement.Label(LogicFont.text(key), TEXT, tokenOf(key));
			if (key.startsWith(MLogStatement.TOKEN_KEY_PREFIX)) statement.param(label);
			target.add(label);
		}
		@Override
		public void field(Supplier<String> get, Consumer<String> set, int width) {
			target.add(new Field(get, set, width, color));
		}
		@Override
		public void option(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			@Nullable Function<String, String> display,
			int width,
			int cols
		) {
			target.add(new Option(get, set, options, display, width, color, cols));
		}
		@Override
		public void grouped(
			Supplier<String> get,
			Consumer<String> set,
			List<OptionGroup> groups,
			@Nullable Function<String, String> display,
			int width
		) {
			target.add(new Select(get, set, groups, display, width, color));
		}
		@Override
		public void node(Supplier<MLogStatement> get, Consumer<MLogStatement> set) {
			target.add(new Node(get, set));
		}
		@Override
		public void spacer() {
			target.add(new Spacer());
		}
	}
	/** @return label key 里最后一个点后面的那段，就是词表里的 token。 */
	private static String tokenOf(String key) {
		return key.substring(key.lastIndexOf('.') + 1);
	}
}
