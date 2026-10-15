package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.*;
import io.github.forgestove.mlog.client.gui.logic.StatementCard.HeaderAction;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.LStatements.JumpStatement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.*;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 语句画布：垂直单列的卡片列表，右侧绘制 {@code jump} 的连线；拖拽重排与连线委托给 {@link CardDragController}、{@link LinkDragController}。 */
public class LogicCanvas implements GuiEventListener, Renderable, NarratableEntry {
	private static final int GAP = 4;
	/** 两侧余下的空间留给连线。 */
	private static final float COLUMN_RATIO = 0.7F;
	/** 触发区比例与速度（像素/秒，每帧 15 像素按 60 帧折算）；按比例算可避免矮画布上整块成为触发区。 */
	private static final float SCROLL_MARGIN_RATIO = 0.25F, SCROLL_SPEED = 15 * 60;
	public final List<StatementCard> cards = new ArrayList<>();
	private final List<JumpCurve> curves = new ArrayList<>();
	private final CardDragController drag = new CardDragController(cards);
	private final LinkDragController link = new LinkDragController(cards);
	private final ScrollBar scrollbar = new ScrollBar();
	public int x, y, width, height;
	private int placeholderY;
	/** 鼠标按在画布空白处（卡片、控件与滚动条之外）；按住时靠近边缘即滚动列表。 */
	private boolean blankPress;
	private boolean focused;
	private int contentHeight;
	private double mouseY;
	private @Nullable IntConsumer addRequest;
	private @Nullable BiConsumer<Picker, Runnable> optionRequest;
	private @Nullable BiConsumer<Color, Runnable> colorRequest;
	private @Nullable Field pressedField;
	/** 界面开着时只能查 GLFW：{@code MouseHandler} 只在无界面时跟踪按键。 */
	private static boolean leftDown() {
		return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
	}
	private static int arrowX(StatementCard card) {
		return card.x + card.width - Node.ICON / 4;
	}
	public void setAddRequest(IntConsumer handler) {
		addRequest = handler;
	}
	public void setOptionRequest(BiConsumer<Picker, Runnable> handler) {
		optionRequest = handler;
	}
	public void setColorRequest(BiConsumer<Color, Runnable> handler) {
		colorRequest = handler;
	}
	public void setStatements(List<MLogStatement> statements) {
		cards.clear();
		drag.cancel();
		for (var statement : statements) cards.add(new StatementCard(statement));
		scrollbar.reset();
		refresh();
	}
	public void refresh() {
		for (var i = 0; i < cards.size(); i++) cards.get(i).index = i;
		LAssembler.reindex(statements());
		// 序号与跳转目标此时均已确定，头部标题一并重算
		for (var card : cards) card.refreshHeader();
		rebuildCurves();
		layout();
	}
	public List<MLogStatement> statements() {
		return cards.stream().map(card -> card.statement).toList();
	}
	private void rebuildCurves() {
		var old = new ArrayList<>(curves);
		curves.clear();
		for (var card : cards) {
			if (!(card.statement instanceof JumpStatement jump)) continue;
			var target = jump.dest == null ? null : cardOf(jump.dest);
			if (target != null) curves.add(reuse(old, card, target));
		}
		JumpCurveLayout.assignLanes(curves);
	}
	public void layout() {
		// 滚动量整体取整后统一平移整列；各卡片分别取整会产生间隙抖动
		var top = y - (int) Math.round(scrollbar.scroll());
		var cursor = top;
		var cardW = columnWidth();
		var cardX = x + (width - cardW) / 2;
		var dragging = drag.dragging();
		var placed = new ArrayList<StatementCard>();
		for (var card : cards) {
			if (card == dragging) {
				card.measure(cardW);
				card.place();
				continue;
			}
			card.x = cardX;
			card.y = cursor;
			card.measure(cardW);
			if (needsPlace(card)) card.place();
			cursor += card.height + GAP;
			placed.add(card);
		}
		var shift = dragging == null ? 0 : dragging.height + GAP;
		var insert = dragging == null ? -1 : drag.insertPosition(placed);
		// 插入点之后的卡片下移一格让位；只动位置，数据待松手后重排
		for (var i = Math.max(0, insert); i < placed.size(); i++) {
			var card = placed.get(i);
			card.y += shift;
			if (needsPlace(card)) card.place();
		}
		contentHeight = placed.isEmpty() ? dragging == null ? 0 : dragging.height : cursor - GAP - top + shift;
		placeholderY = insert <= 0 ? top : placed.get(insert - 1).y + placed.get(insert - 1).height + GAP;
	}
	private @Nullable StatementCard cardOf(MLogStatement statement) {
		for (var card : cards) if (card.statement == statement) return card;
		return null;
	}
	/** 复用两端均未变化的旧连线；全新建会使连线每次刷新都从初始伸出距离重新展开。 */
	private JumpCurve reuse(List<JumpCurve> old, StatementCard from, StatementCard to) {
		for (var it = old.iterator(); it.hasNext(); ) {
			var curve = it.next();
			if (curve.from != from || curve.to != to) continue;
			it.remove();
			return curve;
		}
		var curve = new JumpCurve(from, to);
		curve.reach = JumpCurveLayout.INITIAL;
		return curve;
	}
	private int columnWidth() {
		return Math.round(width * COLUMN_RATIO);
	}
	/** 跳转卡片即使滚出视野也须摆，连线起点取自其节点。 */
	private boolean needsPlace(StatementCard card) {
		return visible(card) || card.node() != null;
	}
	private boolean visible(StatementCard card) {
		return card.y + card.height >= y && card.y <= y + height;
	}
	public void update() {
		var delta = mc.getTimer().getRealtimeDeltaTicks();
		var scrolling = drag.dragging() != null || link.active() || blankPress && leftDown();
		if (scrolling && mouseY >= 0) {
			var dst = Math.min(mouseY - y, y + height - mouseY);
			if (dst < height * SCROLL_MARGIN_RATIO)
				scrollbar.scrollBy(Math.signum(mouseY - (y + height / 2.0)) * SCROLL_SPEED * (delta / 20.0));
		}
		scrollbar.area(scrollbarX(), y, height, contentHeight);
		// 钳制与平滑均在滚动条内完成
		scrollbar.update();
		layout();
		var limit = curveLimit();
		// 伸出距离同样平滑：每帧保留九成
		var keep = (float) Math.pow(0.9, delta * 3F);
		for (var curve : curves) curve.reach += (JumpCurveLayout.reach(curve.lane, limit) - curve.reach) * (1 - keep);
	}
	/** 右侧余下的空间须避开滚动条。 */
	private int curveLimit() {
		return (width - columnWidth()) / 2 - ScrollBar.WIDTH;
	}
	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		layout();
	}
	public void insert(int index, MLogStatement statement) {
		cards.add(Math.clamp(index, 0, cards.size()), new StatementCard(statement));
		refresh();
	}
	public void sync() {
		for (var card : cards) card.sync();
	}	@Override
	public boolean isFocused() {
		return focused;
	}
	public void unfocus() {
		for (var card : cards) card.unfocus();
	}	/** 焦点未变时也会先调 false 再调 true，故该处不得调用 {@link #unfocus}。 */
	@Override
	public void setFocused(boolean focused) {
		this.focused = focused;
	}
	@Override
	public NarrationPriority narrationPriority() {
		return NarrationPriority.NONE;
	}
	@Override
	public void updateNarration(NarrationElementOutput output) {}
	//region 渲染
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		// 子对话框以 -1 重画父界面，该坐标不用于自动滚动
		if (mouseY >= 0) this.mouseY = mouseY;
		var dragging = drag.dragging();
		var clip = LogicClip.begin(gui, x, y, x + width, y + height);
		// 被裁剪的区域（标题栏、按钮栏）不响应事件
		var mx = clip.mouseX(mouseX);
		var my = clip.mouseY(mouseY);
		renderPlaceholder(gui);
		for (var card : cards) {
			if (card == dragging) continue;
			if (card.y + card.height < y || card.y > y + height) continue;
			card.render(gui, mx, my);
		}
		clip.end();
		// 连线画在裁剪区外，否则右伸部分会被截断；拖动卡片时移到顶层，否则终点箭头被遮挡
		if (dragging == null) renderCurves(gui, mouseX, mouseY);
		renderScrollbar(gui);
		renderParamTip(gui, mouseX, mouseY);
	}
	private void renderPlaceholder(GuiGraphics gui) {
		var card = drag.dragging();
		if (card == null) return;
		LogicGuiTextures.PANE_SOLID.render(gui, card.x, placeholderY, card.width, card.height);
	}
	private void renderCurves(GuiGraphics gui, int mouseX, int mouseY) {
		// 端点用卡片的真实坐标，出屏部分交给裁剪；夹到边缘会让箭头脱离卡片
		// 裁剪区开在方法内部：拖动卡片所在的图层完全不裁剪，开在外部会一并失效
		var clip = LogicClip.begin(gui, x, y, x + width, y + height);
		var mx = clip.mouseX(mouseX);
		var my = clip.mouseY(mouseY);
		// 跳向同一目标的连线在目标附近重合，高亮的那条须最后画
		var items = new ArrayList<Item>();
		Item hovered = null;
		for (var curve : curves) {
			var fromNode = curve.from.node();
			if (fromNode == null) continue;
			// 两端都在同一侧屏幕外时才可跳过；一上一下时仍会从画布中间穿过
			var from = nodeTip(fromNode);
			var to = arrowCenter(curve.to);
			if (y > Math.max(from[1], to[1]) || y + height < Math.min(from[1], to[1])) continue;
			var item = new Item(curve, from, to, fromNode.isOver(mx, my));
			if (item.hovered()) hovered = item;
			else items.add(item);
		}
		if (hovered != null) items.add(hovered);
		for (var item : items) {
			var color = item.hovered() ? PLACE : item.curve().color();
			renderJumpArrow(gui, (int) item.to()[0], (int) item.to()[1], color);
			CurveRenderer.curve(gui, item.from()[0], item.from()[1], item.to()[0], item.to()[1], color, item.curve().reach);
		}
		// 拖拽预览：终点吸附到鼠标下的卡片；该段须在裁剪区之内，中途 return 会漏掉 clip.end()
		var node = link.active() ? link.node() : null;
		if (node != null) {
			var from = nodeTip(node);
			var hover = link.hoveredAt(link.mouseX(), link.mouseY());
			var to = hover == null ? new double[]{link.mouseX(), link.mouseY()} : arrowCenter(hover);
			renderJumpArrow(gui, (int) to[0], (int) to[1], TEXT);
			CurveRenderer.curve(gui, from[0], from[1], to[0], to[1], TEXT, JumpCurveLayout.INITIAL);
		}
		// 顶点须在裁剪区关闭前 flush：普通界面下 applyScissor 不代为 flush
		gui.flush();
		clip.end();
	}
	private void renderScrollbar(GuiGraphics gui) {
		scrollbar.render(gui);
	}
	private void renderParamTip(GuiGraphics gui, int mouseX, int mouseY) {
		// 同一时刻只允许一个界面显示提示；上层重画父界面时会临时换回 mc.screen，故还须查 LogicTooltip
		if (!LogicTooltip.available() || !(mc.screen instanceof ProcessorScreen)) return;
		LogicTooltip.render(gui, hoveredTip(mouseX, mouseY), mouseX, mouseY, width, height);
	}
	/** 端点滚出屏幕时仍从真实位置画出，交由外层裁剪。 */
	private double @NotNull [] nodeTip(Node node) {
		return new double[]{node.x + Node.ICON_X + Node.ICON * Node.TIP, node.y + ParamElement.SIZE / 2.0};
	}
	private double @NotNull [] arrowCenter(StatementCard card) {
		return new double[]{arrowX(card) + Node.ICON / 2.0, card.y + card.height / 2.0};
	}
	/** 左端须压入卡片少许才能与曲线终点衔接。 */
	private void renderJumpArrow(GuiGraphics gui, int centerX, int centerY, int color) {
		LogicGuiTextures.LOGIC_NODE.renderTintedFlipped(gui, centerX - Node.ICON / 2, centerY - Node.ICON / 2, Node.ICON, Node.ICON,
			color);
	}
	private int scrollbarX() {
		return x + width - ScrollBar.WIDTH;
	}
	private @Nullable Component hoveredTip(int mouseX, int mouseY) {
		if (drag.dragging() != null || !isMouseOver(mouseX, mouseY)) return null;
		for (var card : cards) {
			if (!card.isOver(mouseX, mouseY)) continue;
			var element = card.elementAt(mouseX, mouseY);
			var key = element == null ? null : element.tipKey();
			return key != null && Language.getInstance().has(key) ? LogicFont.text(key) : null;
		}
		return null;
	}
	/** 拖拽中的卡片与连线不受裁剪；须由界面在 {@code super.render} 之后调用。 */
	public void renderTopLayer(GuiGraphics gui, int mouseX, int mouseY) {
		var dragging = drag.dragging();
		if (dragging == null) return;
		renderCurves(gui, mouseX, mouseY);
		dragging.render(gui, mouseX, mouseY);
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		blankPress = false;
		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
		// 在开头统一收起焦点（命中输入框时下方会重新聚焦），否则两个输入框会同时接收键盘
		unfocus();
		// 滚动条位于卡片列右侧的留白上，判定先于卡片
		if (scrollbar.mousePressed(mouseX, mouseY)) return true;
		for (var i = cards.size() - 1; i >= 0; i--) {
			var card = cards.get(i);
			var action = card.headerActionAt(mouseX, mouseY);
			if (action != null) {
				handleHeaderAction(card, action);
				return true;
			}
			if (!card.isOver(mouseX, mouseY)) continue;
			var element = card.elementAt(mouseX, mouseY);
			if (element != null && handleElement(card, element, mouseX, mouseY)) return true;
			if (button != 0) return false;
			drag.begin(card, mouseY);
			return true;
		}
		// 空白处按住也参与边缘滚动
		blankPress = button == 0;
		return false;
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		blankPress = false;
		pressedField = null;
		if (scrollbar.dragging()) {
			scrollbar.release();
			return true;
		}
		if (link.active()) {
			if (link.end(mouseX, mouseY)) refresh();
			return true;
		}
		if (drag.end()) {
			LogicSounds.button();
			refresh();
		}
		return true;
	}
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (scrollbar.mouseDragged(mouseY)) return true;
		// 输入框按住后拖动是选择文本，不得触发卡片拖拽
		if (pressedField != null) {
			pressedField.dragTo(mouseX);
			return true;
		}
		if (link.active()) {
			link.drag(mouseX, mouseY);
			return true;
		}
		drag.drag(mouseY);
		return true;
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
		// 一格滚动两行卡片：高度随参数个数变化，须先告知滚动条
		if (!cards.isEmpty()) scrollbar.step((cards.getFirst().height + GAP) * 2);
		scrollbar.wheel(-scrollY);
		return true;
	}
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		for (var card : cards) if (card.keyPressed(keyCode, scanCode, modifiers)) return true;
		return false;
	}
	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		for (var card : cards) if (card.charTyped(codePoint, modifiers)) return true;
		return false;
	}
	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
	private boolean handleElement(StatementCard card, ParamElement element, double mouseX, double mouseY) {
		return switch (element) {
			case Field field -> {
				field.focusAt(mouseX, mouseY);
				pressedField = field;
				yield true;
			}
			case Select select -> {
				if (select.isOnButton(mouseX, mouseY)) {
					LogicSounds.button();
					if (optionRequest != null) optionRequest.accept(select, this::rebuildCards);
				} else {
					select.input.focusAt(mouseX, mouseY);
					pressedField = select.input;
				}
				yield true;
			}
			case Color color -> {
				if (color.isOnButton(mouseX, mouseY)) {
					LogicSounds.button();
					if (colorRequest != null) colorRequest.accept(color, this::rebuildCards);
				} else {
					color.input.focusAt(mouseX, mouseY);
					pressedField = color.input;
				}
				yield true;
			}
			case Option option -> {
				LogicSounds.button();
				if (optionRequest != null) optionRequest.accept(option, this::rebuildCards);
				yield true;
			}
			case Node node -> {
				link.begin(node, card, mouseX, mouseY);
				// 开始拖动时已断开旧目标，立即重建才会让原连线消失
				refresh();
				yield true;
			}
			default -> false;
		};
	}
	//endregion
	//region 事件
	private void rebuildCards() {
		for (var card : cards) card.invalidate();
		refresh();
	}
	private void handleHeaderAction(StatementCard card, HeaderAction action) {
		LogicSounds.button();
		switch (action) {
			case ADD -> {
				if (addRequest != null) addRequest.accept(card.index + 1);
			}
			case COPY -> {
				var copy = card.statement.copy();
				if (copy != null) {
					// jump 的目标是语句引用，复制往返文本时带不过来；行号由 refresh 重算
					if (card.statement instanceof JumpStatement from && copy instanceof JumpStatement to) to.dest = from.dest;
					insert(card.index + 1, copy);
				}
			}
			case DELETE -> {
				cards.remove(card);
				refresh();
			}
		}
	}
	private record Item(JumpCurve curve, double @NotNull [] from, double @NotNull [] to, boolean hovered) {}


	//endregion
}
