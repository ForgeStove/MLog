package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Field;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Label;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Node;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Option;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Select;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Spacer;
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
	/** 卡片长宽比，对齐 Mindustry 的语句框。 */
	private static final float ASPECT = 12.85F;
	/** 卡片内边距、参数间距、头部内容左边距。 */
	private static final int PAD = 4, GAP = 5, HEADER_X = 6;
	/** 参数整体比头部栏下沿抬高这么多，对齐 Mindustry 的观感。 */
	private static final int RAISE = 2;
	/** 头部小按钮的尺寸。按钮紧贴卡片右边缘，彼此也不留缝。 */
	private static final int BTN = 13;
	/** 头部栏高度与按钮一致，按钮正好占满整栏。 */
	public static final int HEADER_H = BTN;
	private final List<ParamElement> elements = new ArrayList<>();
	public LStatement statement;
	public int index;
	public int x, y, width = 100, height;
	/** 算子等参数变化会改变布局，置位后在下次布局时重建元素。 */
	private boolean dirty = true;
	public StatementCard(LStatement statement) {
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
		// 按 Mindustry 的长宽比撑开；参数行太多时以内容为准，免得被裁掉
		height = Math.max(contentH, Math.round(width / ASPECT));
	}
	private void rebuildElements() {
		elements.clear();
		statement.buildParams(new ElementBuilder(elements, statement.category().color));
		dirty = false;
	}
	public void render(GuiGraphics gui, int mouseX, int mouseY) {
		var color = statement.category().color;
		// 对齐 Mindustry：悬停在头部栏上就给手型——它整条都能按下拖动
		if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEADER_H) LogicCursor.setHand();
		// 顺序对齐 Mindustry 的 StatementElem.draw：投影 → 半透明黑底 → 头部实心条 → 类别色边框
		gui.fill(x + 2, y + 2, x + width + 2, y + height + 2, SHADOW);
		gui.fill(x, y, x + width, y + height, CARD_BG);
		gui.fill(x, y, x + width, y + HEADER_H, color);
		LogicGuiTextures.WHITE_PANE.renderTinted(gui, x, y, width, height, color);
		LogicFont.drawOutlined(gui, headerText(), x + HEADER_X, y + (HEADER_H - 8) / 2, color);
		var address = LogicFont.literal(String.valueOf(index));
		var addressX = buttonX(HeaderAction.ADD) - mc.font.width(address);
		LogicFont.drawOutlined(gui, address, addressX, y + (HEADER_H - 8) / 2, color);
		for (var action : HeaderAction.values()) renderHeaderButton(gui, action);
		for (var element : elements) element.render(gui, mouseX, mouseY);
	}
	/** {@code jump} 在标题后接上跳转目标，对应 Mindustry 的「跳转 -> N」。 */
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
	/** 把 {@link LayoutBuilder} 的声明收集成参数元素。 */
	private record ElementBuilder(List<ParamElement> target, int color) implements LayoutBuilder {
		@Override
		public void label(String text) {
			target.add(new Label(LogicFont.literal(text), TEXT));
		}
		@Override
		public void labelKey(String key) {
			target.add(new Label(LogicFont.text(key), TEXT));
		}
		@Override
		public void field(Supplier<String> get, Consumer<String> set, int width) {
			target.add(new Field(get, set, width, color));
		}
		@Override
		public void select(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			@Nullable Function<String, String> display,
			int width
		) {
			target.add(new Select(get, set, options, display, width, color));
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
		public void node(Supplier<LStatement> get, Consumer<LStatement> set) {
			target.add(new Node(get, set));
		}
		@Override
		public void spacer() {
			target.add(new Spacer());
		}
	}
}
