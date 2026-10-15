package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.*;
import io.github.forgestove.mlog.logic.LStatements.JumpStatement;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 语句卡片：类别配色的边框与头部栏、参数区。 */
public class StatementCard {
	private static final float ASPECT = 12.85F;
	/** 卡片内边距、参数间距、头部内容左边距。 */
	private static final int PAD = 4, GAP = 5, HEADER_X = 2;
	/** 参数整体比头部栏下沿抬高这么多。 */
	private static final int RAISE = 2;
	/** 头部小按钮尺寸；紧贴卡片右边缘且彼此不留缝。 */
	private static final int BTN = 13;
	/** 头部栏高度与按钮一致，按钮正好占满整栏。 */
	public static final int HEADER_H = BTN;
	/** {@code end}/{@code stop} 无参数，压到该高度后黑区仅余 2 像素：头部栏13+底边4+2=19。 */
	private static final int THIN_H = 17;
	private final List<ParamElement> elements = new ArrayList<>();
	public MLogStatement statement;
	public int index;
	public int x, y, width = 100, height;
	/** 未压缩高度，仅用于算九宫格边框粗细：压缩后的卡片边框不随之变细。 */
	private int fullH;
	/** 算子等参数变化会改变布局，置位后在下次布局时重建元素。 */
	private boolean dirty = true;
	/** 参数元素断成的行，宽度与元素均未变化时复用，见 {@link #measure(int)}。 */
	private List<List<ParamElement>> rows = List.of();
	private int rowsWidth = -1;
	/** 头部标题与地址序号；两者仅在卡片重建或重排后变化，由 {@link #refreshHeader()} 重算。 */
	private Component header = Component.empty(), address = Component.empty();
	private int addressWidth;
	public StatementCard(MLogStatement statement) {
		this.statement = statement;
	}
	private static String tokenOf(String key) {
		return key.substring(key.lastIndexOf('.') + 1);
	}
	/** 语句不变但参数个数可能变化，下次布局时重建控件。 */
	public void invalidate() {
		dirty = true;
	}
	/** 不是 {@code jump} 语句时返回 {@code null}。 */
	public @Nullable Node node() {
		for (var element : elements) if (element instanceof Node node) return node;
		return null;
	}
	/** 序号与跳转目标定下后由画布调用。 */
	public void refreshHeader() {
		var name = LogicFont.text(statement.nameKey());
		// jump 在标题后接上跳转目标
		header = statement instanceof JumpStatement jump && jump.dest != null ? name.copy().append(" -> " + jump.destIndex) : name;
		address = LogicFont.literal(String.valueOf(index));
		addressWidth = mc.font.width(address);
	}
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
	/** 按宽度断行并算卡片高度；断行仅在宽度或元素变化时重算，位置由 {@link #place()} 布置。 */
	public void measure(int width) {
		if (dirty) rebuildElements();
		this.width = width;
		if (rowsWidth != width) {
			rows = split(width);
			rowsWidth = width;
		}
		var contentH = HEADER_H + PAD * 2 + rows.size() * ParamElement.SIZE + Math.max(0, rows.size() - 1) * GAP;
		// 按长宽比撑开；参数行过多时以内容为准，避免被裁掉
		fullH = Math.max(contentH, Math.round(width / ASPECT));
		// 参数区为空的语句（{@code end} / {@code stop} / 无法解析的占位）使用瘦卡片，不按长宽比撑开
		height = elements.isEmpty() ? THIN_H : fullH;
	}
	private void rebuildElements() {
		var old = elements.stream().filter(Picker.class::isInstance).toList();
		elements.clear();
		statement.build(new ElementBuilder(elements, statement.category().color, statement));
		// 参数个数未变时接回上次弹窗的浏览状态；不接回则每次选完再打开都回到第一组、滚回顶部
		var now = elements.stream().filter(Picker.class::isInstance).toList();
		if (old.size() == now.size()) for (var i = 0; i < now.size(); i++) ((Picker) now.get(i)).adopt((Picker) old.get(i));
		dirty = false;
		rowsWidth = -1;
	}
	private List<List<ParamElement>> split(int width) {
		var left = PAD;
		var right = width - PAD;
		var rows = new ArrayList<List<ParamElement>>();
		List<ParamElement> current = new ArrayList<>();
		var cursor = left;
		for (var element : elements) {
			// 弹性元素的宽度取决于本行的剩余空间，断行时先按 0 计算，避免其占满整行
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
		return rows;
	}
	public void place() {
		var left = x + PAD;
		var right = x + width - PAD;
		var cursor = left;
		// 含 spacer 的行将其后的元素右对齐
		for (var r = 0; r < rows.size(); r++) {
			var items = rows.get(r);
			var rowY = y + HEADER_H + PAD - RAISE + r * (ParamElement.SIZE + GAP);
			// 弹性元素占据本行的剩余空间，其余元素仍按自身宽度排列
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
	}
	public void render(GuiGraphics gui, int mouseX, int mouseY) {
		var color = statement.category().color;
		// 悬停在头部栏上时显示手型，它整条都能按下拖动
		if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEADER_H) LogicCursor.setHand();
		// 绘制顺序：投影 → 半透明黑底 → 头部实心条 → 类别色边框
		gui.fill(x + 2, y + 2, x + width + 2, y + height + 2, SHADOW);
		gui.fill(x, y, x + width, y + height, CARD_BG);
		gui.fill(x, y, x + width, y + HEADER_H, color);
		LogicGuiTextures.WHITE_PANE.renderTinted(gui, x, y, width, height, color, LogicGuiTextures.WHITE_PANE.scaleFor(width, fullH));
		LogicFont.drawOutlined(gui, header, x + HEADER_X, y + (HEADER_H - 8) / 2, color);
		LogicFont.drawOutlined(gui, address, buttonX(HeaderAction.ADD) - addressWidth, y + (HEADER_H - 8) / 2, color);
		for (var action : HeaderAction.values()) renderHeaderButton(gui, action);
		for (var element : elements) element.render(gui, mouseX, mouseY);
	}
	private void renderHeaderButton(GuiGraphics gui, HeaderAction action) {
		var bx = buttonX(action);
		var by = y;
		// 不做悬停高亮；光标由头部栏统一处理
		// 图标宽度与按钮宽度之差可能为奇数，直接使用整数除法会整体左偏半像素
		action.icon.render(gui, bx + Math.round((BTN - action.icon.width()) / 2F), LogicIcons.centerY(by, BTN), HEADER_TEXT);
	}
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
		public void color(Supplier<String> get, Consumer<String> set, int width) {
			target.add(new Color(get, set, width, color));
		}
		@Override
		public void option(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			@Nullable Function<String, String> display,
			@Nullable Function<String, String> tipKey,
			int width,
			int cols,
			boolean search
		) {
			target.add(new Option(get, set, options, display, tipKey, width, color, cols, search));
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
}
