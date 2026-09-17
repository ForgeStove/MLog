package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.LogicGuiTextures;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.*;
import io.github.forgestove.mlog.client.gui.logic.StatementCard.HeaderAction;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.LStatements.JumpStatement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.*;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 语句画布：垂直单列的卡片列表，右侧绘制 {@code jump} 的连线。
 * <p>负责布局、滚动、裁剪与事件分发。拖拽重排与连线过程分别委托给
 * {@link CardDragController}、{@link LinkDragController}、{@link OptionPopupScreen}。
 */
@OnlyIn(Dist.CLIENT)
public class LogicCanvas implements GuiEventListener, Renderable, NarratableEntry {
	/** 卡片之间的垂直间距。 */
	private static final int GAP = 4;
	/** 卡片列宽占画布宽度的比例，对齐 Mindustry 的 {@code LCanvas.targetWidth}。两侧余下的空间留给连线。 */
	private static final float COLUMN_RATIO = 0.7F;
	/** 滚动条宽度与滑块的最小高度。 */
	private static final int SCROLLBAR_W = 10;
	/** 拖拽时离画布上下边多近开始自动滚动，以及每帧滚多少。对齐 Mindustry 的 {@code scroll margin} 与 15f/帧。 */
	private static final float SCROLL_MARGIN = 100, SCROLL_SPEED = 15;
	public final List<StatementCard> cards = new ArrayList<>();
	private final List<JumpCurve> curves = new ArrayList<>();
	private final CardDragController drag = new CardDragController(cards);
	private final LinkDragController link = new LinkDragController(cards);
	/** 右侧的滚动条。滚动量、拖动状态与平滑都在它自己身上。 */
	private final ScrollBar scrollbar = new ScrollBar();
	/** 画布可绘制区域与内部内容高度。 */
	public int x, y, width, height;
	/** 占位面板的 y，由 {@link #layout()} 按插入点算好。 */
	private int placeholderY;
	/** 画布自身的焦点状态，MC 在焦点转移时会调 {@link #setFocused}。 */
	private boolean focused;
	private int contentHeight;
	/** 鼠标的纵坐标，{@link #render} 每帧记一次，给 {@link #update} 的拖拽自动滚动用。 */
	private double mouseY;
	/** 请求弹出语句表，参数是插入位置。界面在初始化时设置。 */
	private @Nullable IntConsumer addRequest;
	/** 请求弹出参数选项列表，参数是触发它的控件与选中后的回调。界面在初始化时设置。 */
	private @Nullable BiConsumer<Picker, Runnable> optionRequest;
	/** 按下的输入框。它不在 {@code Screen} 的控件表里，拖动得由这里转给它做文本选择。 */
	private @Nullable Field pressedField;
	/** 设置语句表的弹出回调，卡片头部与底部按钮共用。 */
	public void setAddRequest(IntConsumer handler) {
		addRequest = handler;
	}
	/** 设置参数选项列表的弹出回调。 */
	public void setOptionRequest(BiConsumer<Picker, Runnable> handler) {
		optionRequest = handler;
	}
	/** 用语句列表重建画布内容。 */
	public void setStatements(List<LStatement> statements) {
		cards.clear();
		drag.cancel();
		for (var statement : statements) cards.add(new StatementCard(statement));
		scrollbar.reset();
		refresh();
	}
	/** 重建序号、连线与整体布局。列表结构或参数变化后必须调用。 */
	public void refresh() {
		for (var i = 0; i < cards.size(); i++) cards.get(i).index = i;
		LAssembler.reindex(statements());
		rebuildCurves();
		layout();
	}
	/** @return 当前语句列表。 */
	public List<LStatement> statements() {
		return cards.stream().map(card -> card.statement).toList();
	}
	/** 按 {@code jump.dest} 重建连线，并分配 lane。 */
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
	/** 垂直排布所有卡片，算出内容高度。卡片列水平居中。 */
	public void layout() {
		// 滚动量四舍五入成一个整像素偏移，整列一起平移：所有卡片的间距保持恒定。
		// 不能让每张卡片各自取整——各自取整时相邻卡片的圆整时机不同，间隙会忽大忽小，看着是抖的
		var top = y - (int) Math.round(scrollbar.scroll());
		var cursor = top;
		var cardW = columnWidth();
		var cardX = x + (width - cardW) / 2;
		var dragging = drag.dragging();
		// 第一轮不含让位：先把位置和尺寸定下来，插入点要拿这一轮的结果算
		var placed = new ArrayList<StatementCard>();
		for (var card : cards) {
			// 被拖拽的卡片位置由鼠标决定，不参与排布；但内部元素得跟着它走，
			// 否则卡片背景在动、参数控件还停在原地
			if (card == dragging) {
				card.layout(cardW);
				continue;
			}
			card.x = cardX;
			card.y = cursor;
			card.layout(cardW);
			cursor += card.height + GAP;
			placed.add(card);
		}
		var shift = dragging == null ? 0 : dragging.height + GAP;
		var insert = dragging == null ? -1 : drag.insertPosition(placed);
		// 第二轮：插入点之后的整体下移一格让出空位。这里只动位置，数据等松手才真正重排
		for (var i = Math.max(0, insert); i < placed.size(); i++) {
			var card = placed.get(i);
			card.y += shift;
			card.layout(cardW);
		}
		contentHeight = placed.isEmpty() ? dragging == null ? 0 : dragging.height : cursor - GAP - top + shift;
		// 占位框跟在插入点上，没有插入点（没在拖）时用画布顶部占位
		placeholderY = insert <= 0 ? top : placed.get(insert - 1).y + placed.get(insert - 1).height + GAP;
	}
	private @Nullable StatementCard cardOf(LStatement statement) {
		for (var card : cards) if (card.statement == statement) return card;
		return null;
	}
	/**
	 * 复用两端都没变的旧曲线。
	 * <p>重建后曲线要接着原来的伸出距离继续平滑，一律新建的话每次刷新（拖完卡片、删语句…）
	 * 所有连线都会从零重新长一遍。
	 */
	private JumpCurve reuse(List<JumpCurve> old, StatementCard from, StatementCard to) {
		for (var it = old.iterator(); it.hasNext(); ) {
			var curve = it.next();
			if (curve.from != from || curve.to != to) continue;
			it.remove();
			return curve;
		}
		var curve = new JumpCurve(from, to);
		// 新连线从拖拽预览所在的位置起步，别从最内侧重来
		curve.reach = JumpCurveLayout.INITIAL;
		return curve;
	}
	/** @return 卡片列宽度。 */
	private int columnWidth() {
		return Math.round(width * COLUMN_RATIO);
	}
	/** 每帧推进：滚动插值、重新布局与连线平滑。渲染前调用。 */
	public void update() {
		// 拖拽时鼠标贴到画布上下边就把视口滚过去，否则目标卡片在屏幕外就够不着。
		// 照搬 Mindustry 的 LCanvas.act：离边不足 100 就滚，方向上正下负。
		// 它的 15f 是原始像素还乘了 Time.delta，这边直接按 tick 当量推，量级才和 GUI 坐标对得上
		var delta = mc.getTimer().getRealtimeDeltaTicks();
		if ((link.active() || drag.dragging() != null) && mouseY >= 0) {
			var dst = Math.min(mouseY - y, y + height - mouseY);
			// 鼠标在画布上半就往上滚，和 arc 的 setScrollY 一样，值越大内容越靠上
			if (dst < SCROLL_MARGIN) scrollbar.scrollBy(Math.signum(mouseY - (y + height / 2.0)) * SCROLL_SPEED * delta);
		}
		// 钳制与平滑都在滚动条里
		scrollbar.update(height, contentHeight);
		layout();
		var limit = curveLimit();
		// 连线伸出距离的平滑也照同一套走：每帧保留九成，对齐 Mindustry 的 uiHeight
		var keep = (float) Math.pow(0.9, delta * 3F);
		for (var curve : curves) curve.reach += (JumpCurveLayout.reach(curve.lane, limit) - curve.reach) * (1 - keep);
	}
	/** @return 连线能向右伸出多远。右侧余下的空间要避开滚动条，伸过头会钻到它下面。 */
	private int curveLimit() {
		return (width - columnWidth()) / 2 - SCROLLBAR_W;
	}
	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		layout();
	}
	/** 在指定位置插入语句并刷新。 */
	public void insert(int index, LStatement statement) {
		cards.add(Math.clamp(index, 0, cards.size()), new StatementCard(statement));
		refresh();
	}
	/** 每帧同步一次外部改动的值。 */
	public void sync() {
		for (var card : cards) card.sync();
	}
	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
	@Override
	public boolean isFocused() {
		return focused;
	}
	/**
	 * 只记录状态，<b>不能</b>顺带调 {@link #unfocus}。
	 * <p>{@code AbstractContainerEventHandler.setFocused} 即使焦点没变也会先对旧控件调 false 再调 true，
	 * 在这里收起输入框的话，点中输入框时刚建立的聚焦会被紧接着的这次调用取消掉。
	 */
	@Override
	public void setFocused(boolean focused) {
		this.focused = focused;
	}
	public void unfocus() {
		for (var card : cards) card.unfocus();
	}
	@Override
	public NarrationPriority narrationPriority() {
		return NarrationPriority.NONE;
	}
	/** 画布不参与无障碍朗读，和 Mindustry 一样只做视觉呈现。 */
	@Override
	public void updateNarration(NarrationElementOutput output) {}
	//region 渲染
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		// 子对话框会把父界面用 -1 重画一遍，那种坐标不能拿去算自动滚动
		if (mouseY >= 0) this.mouseY = mouseY;
		var dragging = drag.dragging();
		gui.enableScissor(x, y, x + width, y + height);
		renderPlaceholder(gui);
		for (var card : cards) {
			if (card == dragging) continue;
			if (card.y + card.height < y || card.y > y + height) continue;
			card.render(gui, mouseX, mouseY);
		}
		gui.disableScissor();
		// 连线画在裁剪区外：它向右伸出的部分会超出卡片列，裁掉就断头了。
		// 有卡片正在拖动时要挪到顶层去画，否则终点箭头会被那张卡片盖住
		if (dragging == null) renderCurves(gui, mouseX, mouseY);
		renderScrollbar(gui);
	}
	/**
	 * 在被拖卡片即将插入的位置铺一块占位面板。
	 * <p>位置由 {@link #layout()} 按插入点算好。整块铺 {@link LogicGuiTextures#PANE_SOLID}，
	 * 它的灰边宽度和占位框原来手画的那圈边框一样，一次画完。
	 */
	private void renderPlaceholder(GuiGraphics gui) {
		var card = drag.dragging();
		if (card == null) return;
		LogicGuiTextures.PANE_SOLID.render(gui, card.x, placeholderY, card.width, card.height);
	}
	private void renderCurves(GuiGraphics gui, int mouseX, int mouseY) {
		// 连线按画布矩形裁剪，端点用卡片的真实坐标：滚出去多少就是多少，
		// 出屏的部分自然被裁掉。Mindustry 的连线容器也是整个退出 culling，
		// 靠父级那层剪刀裁，端点一旦被夹到边缘，箭头就会离开卡片贴在画布边上。
		// 刀具开在方法内部而不是外面：拖动卡片那一层整个不裁剪，开在外面会跟着一起失效
		gui.enableScissor(x, y, x + width, y + height);
		// 清掉上一帧画过的线段记录，重合的线在这一帧里只画一次
		CurveRenderer.begin();
		// 先把这一帧要画的连线挑出来并算好端点，顺便记下哪条是高亮的。
		// 跳向同一个目标（向上跳则是同一个起点）的线共用一层、在目标附近重合成一条，
		// 高亮的那条得挪到最后画，否则会被后面画的同名线整个盖住
		var items = new ArrayList<Item>();
		Item hovered = null;
		for (var curve : curves) {
			// 起点是 jump 卡片自己的跳转节点，线从三角的尖端出发
			var fromNode = curve.from.node();
			if (fromNode == null) continue;
			// 整条路径都在屏幕外就不画：线是单调往右折的，两端都被同一侧挡在外面时
			// 中间不可能再冒出来。两端一上一下正好从画布中间穿过去，这种看得见
			var from = nodeTip(fromNode);
			var to = arrowCenter(curve.to);
			if (y > Math.max(from[1], to[1]) || y + height < Math.min(from[1], to[1])) continue;
			var item = new Item(curve, from, to, fromNode.isOver(mouseX, mouseY));
			if (item.hovered()) hovered = item;
			else items.add(item);
		}
		if (hovered != null) items.add(hovered);
		for (var item : items) {
			// 悬停在起点节点上时整条线一起高亮：两端箭头由各自的渲染负责，颜色跟着这里走
			var color = item.hovered() ? PLACE : item.curve().color();
			// 箭头永远贴在目标卡片上画，卡片出屏时跟着被裁掉一部分。先画箭头再画线，线压在箭头上
			renderJumpArrow(gui, (int) item.to()[0], (int) item.to()[1], color);
			CurveRenderer.curve(gui, item.from()[0], item.from()[1], item.to()[0], item.to()[1], color, item.curve().reach);
		}
		// 拖拽连线时画出预览：终点吸附到鼠标下的卡片，否则跟着鼠标走。
		// 吸附用的是含起点自身的查询，吸到自己卡片上也照样贴上去，只是松手不会连。
		// 这一段必须写在裁剪区之内：中途 return 会漏掉 disableScissor，剪刀栈越堆越深
		var node = link.active() ? link.node() : null;
		if (node != null) {
			var from = nodeTip(node);
			var hover = link.hoveredAt(link.mouseX(), link.mouseY());
			var to = hover == null ? new double[]{link.mouseX(), link.mouseY()} : arrowCenter(hover);
			renderJumpArrow(gui, (int) to[0], (int) to[1], TEXT);
			CurveRenderer.curve(gui, from[0], from[1], to[0], to[1], TEXT, JumpCurveLayout.INITIAL);
		}
		gui.disableScissor();
	}
	/** 内容超出一屏时在右侧画滚动条。 */
	private void renderScrollbar(GuiGraphics gui) {
		scrollbar.render(gui, scrollbarX(), y, height, contentHeight);
	}
	/**
	 * @return 节点三角尖端的屏幕坐标。
	 * 	<p>不做可视区判断：Mindustry 把整个连线容器 {@code cullable = false}，
	 * 	端点滚出屏幕时线照样从真实位置画出去，由外层剪刀裁掉。
	 */
	private double @NotNull [] nodeTip(Node node) {
		return new double[]{node.x + Node.ICON_X + Node.ICON * Node.TIP, node.y + ParamElement.SIZE / 2.0};
	}
	/** @return 目标端箭头中心的屏幕坐标，同样不做出屏裁剪。 */
	private double @NotNull [] arrowCenter(StatementCard card) {
		return new double[]{arrowX(card) + Node.ICON / 2.0, card.y + card.height / 2.0};
	}
	/**
	 * 目标端的跳转箭头：镜像的节点图标，箭头指向卡片。
	 * <p>左端要压进卡片一点才和曲线终点接得上。Mindustry 把整个图标悬在边缘外，
	 * 在这里会显得和连线脱开。
	 *
	 * @param centerX 箭头中心的 x，和曲线终点是同一个点。
	 */
	private void renderJumpArrow(GuiGraphics gui, int centerX, int centerY, int color) {
		LogicGuiTextures.LOGIC_NODE.renderTintedFlipped(gui, centerX - Node.ICON / 2, centerY - Node.ICON / 2, Node.ICON, Node.ICON,
			color);
	}
	/** @return 滚动条所在的右边缘竖条的左边。 */
	private int scrollbarX() {
		return x + width - SCROLLBAR_W;
	}
	/** @return 目标端箭头图标的左边缘。 */
	private static int arrowX(StatementCard card) {
		return card.x + card.width - Node.ICON / 4;
	}
	/**
	 * 画在按钮栏之上的一层：拖拽中的卡片。
	 * <p>它不受裁剪，否则拖到画布外会突然消失。必须由界面在 {@code super.render} 之后调用。
	 * 连线也跟着上来，好让拖拽中的卡片压在线上、又不会盖掉终点的箭头。
	 */
	public void renderTopLayer(GuiGraphics gui, int mouseX, int mouseY) {
		var dragging = drag.dragging();
		if (dragging == null) return;
		renderCurves(gui, mouseX, mouseY);
		dragging.render(gui, mouseX, mouseY);
	}
	/** @return 事件是否被消费。 */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
		// 先收起所有输入焦点，命中输入框时下面会重新聚焦。
		// 在开头统一做，画布内的空白处（卡片之间、卡片列两侧）才同样能取消焦点；
		// 不这样做的话两个输入框会同时吃键盘。
		unfocus();
		// 滚动条压在卡片列右侧的留白上，比卡片先判
		if (scrollbar.mousePressed(mouseX, mouseY, scrollbarX(), y, height, contentHeight)) return true;
		// 从上往下找，被拖拽的卡片优先
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
		return false;
	}
	/** @return 参数元素是否消费了这次点击。 */
	private boolean handleElement(StatementCard card, ParamElement element, double mouseX, double mouseY) {
		return switch (element) {
			case Field field -> {
				field.focusAt(mouseX, mouseY);
				pressedField = field;
				yield true;
			}
			case Select select -> {
				// 点右边的方形按钮才弹列表，点左边是正常输入
				if (select.isOnButton(mouseX, mouseY)) {
					if (optionRequest != null) optionRequest.accept(select, this::rebuildCards);
				} else {
					select.input.focusAt(mouseX, mouseY);
					pressedField = select.input;
				}
				yield true;
			}
			case Option option -> {
				// 整个控件就是按钮，点哪都弹列表
				if (optionRequest != null) optionRequest.accept(option, this::rebuildCards);
				yield true;
			}
			case Node node -> {
				link.begin(node, card, mouseX, mouseY);
				// 开始拖动就已经断开旧目标了，这里立刻重建，原来的那条连线才会马上消失
				refresh();
				yield true;
			}
			default -> false;
		};
	}
	//endregion
	//region 事件
	/** 换算子会改变参数个数，需要重建卡片控件。 */
	private void rebuildCards() {
		for (var card : cards) card.invalidate();
		refresh();
	}
	private void handleHeaderAction(StatementCard card, HeaderAction action) {
		switch (action) {
			case ADD -> {
				// 与底部「添加」一致：打开语句表挑一条，插到本卡片之后
				if (addRequest != null) addRequest.accept(card.index + 1);
			}
			case COPY -> {
				var copy = card.statement.copy();
				if (copy != null) {
					// jump 的目标是语句引用，复制走的是文本往返，带不过来。
					// 这里接回同一个目标，行号由 refresh 里的 reindex 重算
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
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (scrollbar.mouseDragged(mouseY, y, height, contentHeight)) return true;
		// 输入框按住后拖动是选文本，别让它变成卡片拖拽
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
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		pressedField = null;
		if (scrollbar.dragging()) {
			scrollbar.release();
			return true;
		}
		if (link.active()) {
			if (link.end(mouseX, mouseY)) refresh();
			return true;
		}
		if (drag.end()) refresh();
		return true;
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
		// 一格滚两行卡片：卡片高随参数个数变，得先把它算给滚动条
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
	/** 一帧里要画的一条连线：端点提前算好，{@code hovered} 决定它压在别的线上面画。 */
	private record Item(JumpCurve curve, double @NotNull [] from, double @NotNull [] to, boolean hovered) {}
	//endregion
}
