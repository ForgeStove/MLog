package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.component.*;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.*;
import io.github.forgestove.mlog.client.gui.logic.StatementCard.HeaderAction;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.LStatements.JumpStatement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 语句画布：垂直单列的卡片列表，右侧绘制 {@code jump} 的连线；拖拽重排与连线委托给 {@link CardDragController}、{@link LinkDragController}。 */
public class LogicCanvas extends AbstractWidget implements ContainerEventHandler {
	private static final int GAP = 4;
	/** 两侧余下的空间留给连线。 */
	private static final float COLUMN_RATIO = 0.7F;
	/** 边缘自动滚动的触发距离（像素，按 MDT 的 100 单位折算）与速度（像素/秒）。 */
	private static final float SCROLL_MARGIN = 40, SCROLL_SPEED = 15 * 60;
	public final List<StatementCard> cards = new ArrayList<>();
	private final List<JumpCurve> curves = new ArrayList<>();
	private final CardDragController drag = new CardDragController(cards);
	private final LinkDragController link = new LinkDragController(cards);
	private final ScrollBar scrollbar = new ScrollBar();
	/** 参与事件分发的子控件，每帧按可见卡片重建。 */
	private final List<GuiEventListener> children = new ArrayList<>();
	private int placeholderY;
	/** 鼠标按在画布空白处（卡片、控件与滚动条之外）；按住时靠近边缘即滚动列表。 */
	private boolean blankPress;
	private int contentHeight;
	private double mouseY;
	private @Nullable IntConsumer addRequest;
	private @Nullable BiConsumer<Picker, Runnable> optionRequest;
	private @Nullable BiConsumer<Color, Runnable> colorRequest;
	private @Nullable GuiEventListener focusedChild;
	private boolean containerDragging;
	public LogicCanvas() {
		super(0, 0, 0, 0, Component.empty());
	}
	/** 界面开着时只能查 GLFW：{@code MouseHandler} 只在无界面时跟踪按键。 */
	private static boolean leftDown() {
		return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
	}
	private static int arrowX(StatementCard card) {
		return card.x + card.width - Node.ICON / 4;
	}
	private static void applyClip(GuiEventListener child, int left, int top, int right, int bottom) {
		switch (child) {
			case LogicCellButton button -> button.setClip(left, top, right, bottom);
			case LogicEditBox box -> box.setClip(left, top, right, bottom);
			case LogicDragHandle handle -> handle.setClip(left, top, right, bottom);
			default -> {}
		}
	}
	@Override
	public List<? extends GuiEventListener> children() {
		return children;
	}
	@Override
	public boolean isDragging() {
		return containerDragging;
	}
	@Override
	public void setDragging(boolean dragging) {
		containerDragging = dragging;
	}
	@Override
	public @Nullable GuiEventListener getFocused() {
		return focusedChild;
	}
	@Override
	public void setFocused(@Nullable GuiEventListener focused) {
		if (focusedChild != null) focusedChild.setFocused(false);
		if (focused != null) focused.setFocused(true);
		focusedChild = focused;
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
		for (var statement : statements) cards.add(new StatementCard(statement, this));
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
		var cardW = columnWidth();
		var cardX = getX() + (getWidth() - cardW) / 2;
		var dragging = drag.dragging();
		// 先量出总高：内容不足一屏时整列垂直居中
		var total = 0;
		for (var card : cards) {
			card.measure(cardW);
			total += card.height + GAP;
		}
		contentHeight = Math.max(0, total - GAP);
		// 滚动量整体取整后统一平移整列；各卡片分别取整会产生间隙抖动
		var top = getY() + Math.max(0, (getHeight() - contentHeight) / 2) - (int) Math.round(scrollbar.scroll());
		var cursor = top;
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
		placeholderY = insert <= 0 ? top : placed.get(insert - 1).y + placed.get(insert - 1).height + GAP;
		rebuildChildren();
	}
	/** 只登记可见卡片的控件；位置与裁剪区每帧同步，滚出视野的控件不参与命中。 */
	private void rebuildChildren() {
		children.clear();
		children.add(scrollbar);
		var dragging = drag.dragging();
		for (var card : cards) {
			if (!visible(card) && card != dragging) continue;
			card.collectWidgets(children);
		}
		var left = getX();
		var top = getY();
		var right = getX() + getWidth();
		var bottom = getY() + getHeight();
		for (var child : children) applyClip(child, left, top, right, bottom);
		// 连线拖动中的控件属于旧卡片，保留焦点以继续接收拖动与松开
		if (focusedChild != null && !children.contains(focusedChild) && !link.active()) setFocused(null);
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
		return Math.round(getWidth() * COLUMN_RATIO);
	}
	/** 跳转卡片即使滚出视野也须摆，连线起点取自其节点。 */
	private boolean needsPlace(StatementCard card) {
		return visible(card) || card.node() != null;
	}
	private boolean visible(StatementCard card) {
		return card.y + card.height >= getY() && card.y <= getY() + getHeight();
	}
	public void update() {
		var delta = mc.getTimer().getRealtimeDeltaTicks();
		var scrolling = drag.dragging() != null || link.active() || blankPress && leftDown();
		if (scrolling && mouseY >= 0) {
			var dst = Math.min(mouseY - getY(), getY() + getHeight() - mouseY);
			if (dst < SCROLL_MARGIN)
				scrollbar.scrollBy(Math.signum(mouseY - (getY() + getHeight() / 2.0)) * SCROLL_SPEED * (delta / 20.0));
		}
		scrollbar.area(scrollbarX(), getY(), getHeight(), contentHeight);
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
		return (getWidth() - columnWidth()) / 2 - ScrollBar.WIDTH;
	}
	public void setBounds(int x, int y, int width, int height) {
		setX(x);
		setY(y);
		setWidth(width);
		setHeight(height);
		layout();
	}
	public void insert(int index, MLogStatement statement) {
		cards.add(Math.clamp(index, 0, cards.size()), new StatementCard(statement, this));
		refresh();
	}
	public void sync() {
		for (var card : cards) card.sync();
	}
	public void unfocus() {
		setFocused(null);
		for (var card : cards) card.unfocus();
	}
	//region 渲染
	@Override
	protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		// 子对话框以 -1 重画父界面，该坐标不用于自动滚动
		if (mouseY >= 0) this.mouseY = mouseY;
		var dragging = drag.dragging();
		var clip = LogicClip.begin(gui, getX(), getY(), getX() + getWidth(), getY() + getHeight());
		// 被裁剪的区域（标题栏、按钮栏）不响应事件
		var mx = clip.mouseX(mouseX);
		var my = clip.mouseY(mouseY);
		renderPlaceholder(gui);
		for (var card : cards) {
			if (card == dragging) continue;
			if (card.y + card.height < getY() || card.y > getY() + getHeight()) continue;
			card.render(gui, mx, my);
		}
		clip.end();
		// 连线画在裁剪区外，否则右伸部分会被截断；拖动卡片时移到顶层，否则终点箭头被遮挡
		if (dragging == null) renderCurves(gui, mouseX, mouseY);
		renderScrollbar(gui, mouseX, mouseY, partialTick);
		renderParamTip(gui, mouseX, mouseY);
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		blankPress = false;
		if (!isMouseOver(mouseX, mouseY)) return false;
		// 按下即收起输入框焦点，命中输入框时由它重新聚焦
		setFocused(null);
		// 快照遍历：节点按下会触发重建，避免边遍历边改 children
		for (var child : List.copyOf(children)) {
			if (!child.mouseClicked(mouseX, mouseY, button)) continue;
			setFocused(child);
			if (button == 0) setDragging(true);
			return true;
		}
		// 空白处按住也参与边缘滚动；卡片范围内的按下由卡片的拖拽控件处理
		blankPress = button == 0;
		return false;
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		blankPress = false;
		if (ContainerEventHandler.super.mouseReleased(mouseX, mouseY, button)) return true;
		if (link.active()) {
			// 未登记控件的兜底：结束连线并刷新
			if (link.end(mouseX, mouseY)) refresh();
			return true;
		}
		return true;
	}
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (ContainerEventHandler.super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
		if (link.active()) {
			link.drag(mouseX, mouseY);
			return true;
		}
		return true;
	}
	@Override
	public NarrationPriority narrationPriority() {
		return NarrationPriority.NONE;
	}
	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {}
	private void renderPlaceholder(GuiGraphics gui) {
		var card = drag.dragging();
		if (card == null) return;
		// 占位框始终在列内，只跟随纵向插入位置
		var cardX = getX() + (getWidth() - columnWidth()) / 2;
		LogicGuiTextures.PANE_SOLID.render(gui, cardX, placeholderY, card.width, card.height);
	}
	private void renderCurves(GuiGraphics gui, int mouseX, int mouseY) {
		// 端点用卡片的真实坐标，出屏部分交给裁剪；夹到边缘会让箭头脱离卡片
		// 裁剪区开在方法内部：拖动卡片所在的图层完全不裁剪，开在外部会一并失效
		var clip = LogicClip.begin(gui, getX(), getY(), getX() + getWidth(), getY() + getHeight());
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
			if (getY() > Math.max(from[1], to[1]) || getY() + getHeight() < Math.min(from[1], to[1])) continue;
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
			// 预览线与源节点同色：悬停时为放置色
			var color = node.isOver(mx, my) ? PLACE : TEXT;
			renderJumpArrow(gui, (int) to[0], (int) to[1], color);
			CurveRenderer.curve(gui, from[0], from[1], to[0], to[1], color, JumpCurveLayout.INITIAL);
		}
		// 顶点须在裁剪区关闭前 flush：普通界面下 applyScissor 不代为 flush
		gui.flush();
		clip.end();
	}
	private void renderScrollbar(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		scrollbar.render(gui, mouseX, mouseY, partialTick);
	}
	private void renderParamTip(GuiGraphics gui, int mouseX, int mouseY) {
		// 同一时刻只允许一个界面显示提示；上层重画父界面时会临时换回 mc.screen，故还须查 LogicTooltip
		if (!LogicTooltip.available() || !(mc.screen instanceof ProcessorScreen)) return;
		LogicTooltip.render(gui, hoveredTip(mouseX, mouseY), mouseX, mouseY, getWidth(), getHeight());
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
		return getX() + getWidth() - ScrollBar.WIDTH;
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
		// 先画被拖卡片，再画连线，连线与跳转起点保持在最上层
		dragging.render(gui, mouseX, mouseY);
		renderCurves(gui, mouseX, mouseY);
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!isMouseOver(mouseX, mouseY)) return false;
		// 一格滚动两行卡片：高度随参数个数变化，须先告知滚动条
		if (!cards.isEmpty()) scrollbar.step((cards.getFirst().height + GAP) * 2);
		scrollbar.wheel(scrollY);
		return true;
	}
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return ContainerEventHandler.super.keyPressed(keyCode, scanCode, modifiers);
	}
	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		return ContainerEventHandler.super.charTyped(codePoint, modifiers);
	}
	//endregion
	//region 事件
	private void rebuildCards() {
		for (var card : cards) card.invalidate();
		refresh();
	}
	/** 参数按钮打开选择列表或取色器。 */
	void openPicker(Picker picker) {
		LogicSounds.button();
		if (picker instanceof Color color) {
			if (colorRequest != null) colorRequest.accept(color, this::rebuildCards);
		} else if (optionRequest != null) optionRequest.accept(picker, this::rebuildCards);
	}
	void beginCardDrag(StatementCard card, double mouseX, double mouseY) {
		drag.begin(card, mouseX, mouseY);
	}
	void dragCard(double mouseX, double mouseY) {
		drag.drag(mouseX, mouseY);
	}
	void endCardDrag() {
		if (!drag.end()) return;
		LogicSounds.button();
		refresh();
	}
	void beginLink(Node node, StatementCard source, double mouseX, double mouseY) {
		link.begin(node, source, mouseX, mouseY);
		// 开始拖动时已断开旧目标，立即重建才会让原连线消失
		refresh();
	}
	void dragLink(double mouseX, double mouseY) {
		link.drag(mouseX, mouseY);
	}
	void endLink(double mouseX, double mouseY) {
		// 拖出连线与直接点击节点都在松开时发声
		LogicSounds.button();
		if (link.end(mouseX, mouseY)) refresh();
	}
	void handleHeaderAction(StatementCard card, HeaderAction action) {
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
