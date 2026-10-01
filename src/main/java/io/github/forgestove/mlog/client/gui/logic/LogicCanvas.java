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
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.*;
import org.lwjgl.glfw.GLFW;

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
	/** 卡片列宽占画布宽度的比例。两侧余下的空间留给连线。 */
	private static final float COLUMN_RATIO = 0.7F;
	/** 拖拽时离画布上下边多近开始自动滚动，以及自动滚动的速度（像素/秒，由每帧 15 像素按 60 帧折算）。 */
	private static final float SCROLL_MARGIN = 100, SCROLL_SPEED = 15 * 60;
	public final List<StatementCard> cards = new ArrayList<>();
	private final List<JumpCurve> curves = new ArrayList<>();
	private final CardDragController drag = new CardDragController(cards);
	private final LinkDragController link = new LinkDragController(cards);
	/** 右侧的滚动条。滚动量、拖动状态与平滑均由它维护。 */
	private final ScrollBar scrollbar = new ScrollBar();
	/** 画布可绘制区域与内部内容高度。 */
	public int x, y, width, height;
	/** 占位面板的 y，由 {@link #layout()} 按插入点算好。 */
	private int placeholderY;
	/** 鼠标是否按在画布空白处（未落在卡片、控件或滚动条上）：按住时靠近边缘也会滚动列表。 */
	private boolean blankPress;
	/** 画布自身的焦点状态，MC 在焦点转移时会调 {@link #setFocused}。 */
	private boolean focused;
	private int contentHeight;
	/** 鼠标的纵坐标，{@link #render} 每帧记一次，给 {@link #update} 的拖拽自动滚动用。 */
	private double mouseY;
	/** 请求弹出语句表，参数是插入位置。界面在初始化时设置。 */
	private @Nullable IntConsumer addRequest;
	/** 请求弹出参数选项列表，参数是触发它的控件与选中后的回调。界面在初始化时设置。 */
	private @Nullable BiConsumer<Picker, Runnable> optionRequest;
	/** 请求弹出取色器，参数是触发它的颜色控件与取完色后的回调。界面在初始化时设置。 */
	private @Nullable BiConsumer<Color, Runnable> colorRequest;
	/** 按下的输入框。它不在 {@code Screen} 的控件表中，拖动须由这里转交以执行文本选择。 */
	private @Nullable Field pressedField;
	/** 设置语句表的弹出回调，卡片头部与底部按钮共用。 */
	public void setAddRequest(IntConsumer handler) {
		addRequest = handler;
	}
	/** 设置参数选项列表的弹出回调。 */
	public void setOptionRequest(BiConsumer<Picker, Runnable> handler) {
		optionRequest = handler;
	}
	/** 设置取色器的弹出回调。 */
	public void setColorRequest(BiConsumer<Color, Runnable> handler) {
		colorRequest = handler;
	}
	/** 用语句列表重建画布内容。 */
	public void setStatements(List<MLogStatement> statements) {
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
		// 序号与跳转目标此时均已确定，头部标题与地址一并重算
		for (var card : cards) card.refreshHeader();
		rebuildCurves();
		layout();
	}
	/** @return 当前语句列表。 */
	public List<MLogStatement> statements() {
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
		// 滚动量整体取整后统一平移整列，卡片间距保持恒定。
		// 若各卡片分别取整，相邻卡片的取整时机不同，间隙会时大时小，产生抖动
		var top = y - (int) Math.round(scrollbar.scroll());
		var cursor = top;
		var cardW = columnWidth();
		var cardX = x + (width - cardW) / 2;
		var dragging = drag.dragging();
		// 第一轮不含让位：先把位置和尺寸定下来，插入点要拿这一轮的结果算
		var placed = new ArrayList<StatementCard>();
		for (var card : cards) {
			// 被拖拽的卡片位置由鼠标决定，不参与排布；但内部元素需跟随移动，
			// 否则卡片背景移动而参数控件留在原地
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
		// 第二轮：插入点之后的卡片整体下移一格让出空位。此处只动位置，数据待松手后重排
		for (var i = Math.max(0, insert); i < placed.size(); i++) {
			var card = placed.get(i);
			card.y += shift;
			if (needsPlace(card)) card.place();
		}
		contentHeight = placed.isEmpty() ? dragging == null ? 0 : dragging.height : cursor - GAP - top + shift;
		// 占位框跟随插入点，无插入点（未拖拽）时使用画布顶部
		placeholderY = insert <= 0 ? top : placed.get(insert - 1).y + placed.get(insert - 1).height + GAP;
	}
	private @Nullable StatementCard cardOf(MLogStatement statement) {
		for (var card : cards) if (card.statement == statement) return card;
		return null;
	}
	/**
	 * 复用两端均未变化的旧连线。
	 * <p>重建后连线应沿用原伸出距离继续平滑；若一律新建，每次刷新（拖完卡片、删除语句等）
	 * 所有连线都会从初始伸出距离重新展开。
	 */
	private JumpCurve reuse(List<JumpCurve> old, StatementCard from, StatementCard to) {
		for (var it = old.iterator(); it.hasNext(); ) {
			var curve = it.next();
			if (curve.from != from || curve.to != to) continue;
			it.remove();
			return curve;
		}
		var curve = new JumpCurve(from, to);
		// 新连线从初始伸出距离起步
		curve.reach = JumpCurveLayout.INITIAL;
		return curve;
	}
	/** @return 卡片列宽度。 */
	private int columnWidth() {
		return Math.round(width * COLUMN_RATIO);
	}
	/** @return 卡片是否要摆元素位置；跳转卡片即使滚出视野也须摆，连线起点取自其节点。 */
	private boolean needsPlace(StatementCard card) {
		return visible(card) || card.node() != null;
	}
	/** @return 卡片是否落在画布的可见范围内。 */
	private boolean visible(StatementCard card) {
		return card.y + card.height >= y && card.y <= y + height;
	}
	/** 每帧推进：滚动插值、重新布局与连线平滑。渲染前调用。 */
	public void update() {
		var delta = mc.getTimer().getRealtimeDeltaTicks();
		var scrolling = drag.dragging() != null || link.active() || blankPress && leftDown();
		if (scrolling && mouseY >= 0) {
			var dst = Math.min(mouseY - y, y + height - mouseY);
			// 鼠标位于画布上半部时向上滚动，值越大内容越靠上
			if (dst < SCROLL_MARGIN) scrollbar.scrollBy(Math.signum(mouseY - (y + height / 2.0)) * SCROLL_SPEED * (delta / 20.0));
		}
		scrollbar.area(scrollbarX(), y, height, contentHeight);
		// 钳制与平滑均在滚动条内完成
		scrollbar.update();
		layout();
		var limit = curveLimit();
		// 连线伸出距离采用同一套平滑：每帧保留九成
		var keep = (float) Math.pow(0.9, delta * 3F);
		for (var curve : curves) curve.reach += (JumpCurveLayout.reach(curve.lane, limit) - curve.reach) * (1 - keep);
	}
	/** @return 鼠标左键是否按下。{@code MouseHandler} 只在没有界面时跟踪按键，界面开着时问它永远是 false。 */
	private static boolean leftDown() {
		return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
	}
	/** @return 连线能向右伸出多远。右侧余下的空间要避开滚动条，伸过头会钻到它下面。 */
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
	/** 在指定位置插入语句并刷新。 */
	public void insert(int index, MLogStatement statement) {
		cards.add(Math.clamp(index, 0, cards.size()), new StatementCard(statement));
		refresh();
	}
	/** 每帧同步一次外部改动的值。 */
	public void sync() {
		for (var card : cards) card.sync();
	}
	@Override
	public boolean isFocused() {
		return focused;
	}
	/**
	 * 只记录状态，不可在此调用 {@link #unfocus}。
	 * <p>{@code AbstractContainerEventHandler.setFocused} 即使焦点没变也会先对旧控件调 false 再调 true，
	 * 若在此收起输入框，点中输入框时刚建立的聚焦会被紧接着的这次调用取消。
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
	/** 画布不参与无障碍朗读，只做视觉呈现。 */
	@Override
	public void updateNarration(NarrationElementOutput output) {}
	//region 渲染
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		// 子对话框会以 -1 重画父界面，该坐标不能用于计算自动滚动
		if (mouseY >= 0) this.mouseY = mouseY;
		var dragging = drag.dragging();
		var clip = LogicClip.begin(gui, x, y, x + width, y + height);
		// 画布外（标题栏、按钮栏）划过卡片被裁掉的那部分不该有反应
		var mx = clip.mouseX(mouseX);
		var my = clip.mouseY(mouseY);
		renderPlaceholder(gui);
		for (var card : cards) {
			if (card == dragging) continue;
			if (card.y + card.height < y || card.y > y + height) continue;
			card.render(gui, mx, my);
		}
		clip.end();
		// 连线画在裁剪区外：它向右伸出的部分会超出卡片列，裁剪后会被截断。
		// 有卡片正在拖动时需移到顶层绘制，否则终点箭头会被该卡片遮挡
		if (dragging == null) renderCurves(gui, mouseX, mouseY);
		renderScrollbar(gui);
		renderParamTip(gui, mouseX, mouseY);
	}
	/**
	 * 在被拖卡片即将插入的位置铺一块占位面板。
	 * <p>位置由 {@link #layout()} 按插入点算好。整块铺 {@link LogicGuiTextures#PANE_SOLID}，
	 * 其灰边宽度与原有手绘边框一致，一次绘制完成。
	 */
	private void renderPlaceholder(GuiGraphics gui) {
		var card = drag.dragging();
		if (card == null) return;
		LogicGuiTextures.PANE_SOLID.render(gui, card.x, placeholderY, card.width, card.height);
	}
	private void renderCurves(GuiGraphics gui, int mouseX, int mouseY) {
		// 连线按画布矩形裁剪，端点使用卡片的真实坐标：滚出多少即多少，
		// 出屏部分会被裁掉。端点若被夹到边缘，
		// 箭头会脱离卡片贴在画布边上。
		// 裁剪区开在方法内部而非外部：拖动卡片所在的图层完全不裁剪，开在外部会一并失效
		var clip = LogicClip.begin(gui, x, y, x + width, y + height);
		var mx = clip.mouseX(mouseX);
		var my = clip.mouseY(mouseY);
		// 先挑出这一帧要画的连线并算好端点，同时记下高亮的那条。
		// 跳向同一个目标（向上跳则是同一个起点）的连线共用一层、在目标附近重合成一条，
		// 高亮的那条须移到最后绘制，否则会被后绘制的同名线完全遮盖
		var items = new ArrayList<Item>();
		Item hovered = null;
		for (var curve : curves) {
			// 起点是 jump 卡片自身的跳转节点，连线从三角尖端出发
			var fromNode = curve.from.node();
			if (fromNode == null) continue;
			// 整条路径都在屏幕外则跳过：连线单调向右折，两端被同一侧挡在外面时
			// 中间不会再进入视野；两端一上一下时从画布中间穿过，仍然可见
			var from = nodeTip(fromNode);
			var to = arrowCenter(curve.to);
			if (y > Math.max(from[1], to[1]) || y + height < Math.min(from[1], to[1])) continue;
			var item = new Item(curve, from, to, fromNode.isOver(mx, my));
			if (item.hovered()) hovered = item;
			else items.add(item);
		}
		if (hovered != null) items.add(hovered);
		for (var item : items) {
			// 悬停在起点节点上时整条线一起高亮：两端箭头由各自的渲染负责，颜色由这里统一决定
			var color = item.hovered() ? PLACE : item.curve().color();
			// 箭头始终贴在目标卡片上绘制，卡片出屏时随之被裁掉一部分。先画箭头再画连线，连线压在箭头上
			renderJumpArrow(gui, (int) item.to()[0], (int) item.to()[1], color);
			CurveRenderer.curve(gui, item.from()[0], item.from()[1], item.to()[0], item.to()[1], color, item.curve().reach);
		}
		// 拖拽连线时绘制预览：终点吸附到鼠标下的卡片，否则跟随鼠标。
		// 吸附使用包含起点自身的查询，吸到自身卡片上同样会贴合，只是松手后不会连接。
		// 这一段必须位于裁剪区之内：中途 return 会漏掉 clip.end()，使裁剪栈持续堆积
		var node = link.active() ? link.node() : null;
		if (node != null) {
			var from = nodeTip(node);
			var hover = link.hoveredAt(link.mouseX(), link.mouseY());
			var to = hover == null ? new double[]{link.mouseX(), link.mouseY()} : arrowCenter(hover);
			renderJumpArrow(gui, (int) to[0], (int) to[1], TEXT);
			CurveRenderer.curve(gui, from[0], from[1], to[0], to[1], TEXT, JumpCurveLayout.INITIAL);
		}
		// 连线的顶点是手动提交到批次的，不会立即绘制，须在裁剪区关闭前完成。
		// applyScissor 仅在 managed 模式下代为 flush，普通界面中不做任何处理，
		// 该批顶点会延迟到裁剪区之外才绘制，整条线因此超出画布
		gui.flush();
		clip.end();
	}
	/** 内容超出一屏时在右侧画滚动条。 */
	private void renderScrollbar(GuiGraphics gui) {
		scrollbar.render(gui);
	}
	/** 参数区小词的悬停提示，画在卡片、连线与滚动条之后。 */
	private void renderParamTip(GuiGraphics gui, int mouseX, int mouseY) {
		// 对话框打开时画布也会被重画，该情形下不显示提示：同一时刻只允许一个界面显示，否则淡入会被反复打断。
		// 上层界面重画父界面时会把 mc.screen 临时换回父界面，仅判断它无法拦住，还须查询 LogicTooltip 是否已被屏蔽
		if (!LogicTooltip.available() || !(mc.screen instanceof ProcessorScreen)) return;
		LogicTooltip.render(gui, hoveredTip(mouseX, mouseY), mouseX, mouseY, width, height);
	}
	/**
	 * @return 节点三角尖端的屏幕坐标。
	 * 	<p>不做可视区判断：端点滚出屏幕时连线仍从真实位置画出，交由外层裁剪处理。
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
	 * <p>左端须压入卡片少许才能与曲线终点衔接；图标若整个悬在边缘之外，会与连线脱开。
	 *
	 * @param centerX 箭头中心的 x，与曲线终点为同一点。
	 */
	private void renderJumpArrow(GuiGraphics gui, int centerX, int centerY, int color) {
		LogicGuiTextures.LOGIC_NODE.renderTintedFlipped(gui, centerX - Node.ICON / 2, centerY - Node.ICON / 2, Node.ICON, Node.ICON,
			color);
	}
	/** @return 滚动条所在的右边缘竖条的左边。 */
	private int scrollbarX() {
		return x + width - ScrollBar.WIDTH;
	}
	/** @return 鼠标所指参数元素的提示文本，无则返回 {@code null}。 */
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
	/** @return 目标端箭头图标的左边缘。 */
	private static int arrowX(StatementCard card) {
		return card.x + card.width - Node.ICON / 4;
	}
	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
	/**
	 * 画在按钮栏之上的一层：拖拽中的卡片。
	 * <p>它不受裁剪，否则拖到画布外会突然消失。必须由界面在 {@code super.render} 之后调用。
	 * 连线一并上移至此层，使拖拽中的卡片压在连线上，且不遮挡终点箭头。
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
		blankPress = false;
		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
		// 先收起所有输入焦点，命中输入框时下方会重新聚焦。
		// 在开头统一执行，画布内的空白处（卡片之间、卡片列两侧）才能同样取消焦点；
		// 否则两个输入框会同时接收键盘输入。
		unfocus();
		// 滚动条位于卡片列右侧的留白上，判定先于卡片
		if (scrollbar.mousePressed(mouseX, mouseY)) return true;
		// 从列表末尾向前查找，被拖拽的卡片优先
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
		// 位于画布内但未落在卡片、控件或滚动条上：按住此处靠近边缘同样能滚动列表
		blankPress = button == 0;
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
				// 点击右侧方形按钮才弹出列表，点击左侧为正常输入
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
				// 同理：点击铅笔才打开取色器，点击左侧照常输入
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
				// 整个控件即按钮，点击任意位置都弹出列表
				LogicSounds.button();
				if (optionRequest != null) optionRequest.accept(option, this::rebuildCards);
				yield true;
			}
			case Node node -> {
				link.begin(node, card, mouseX, mouseY);
				// 开始拖动时已断开旧目标，此处立即重建，原连线才会随之消失
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
		LogicSounds.button();
		switch (action) {
			case ADD -> {
				// 与底部「添加」一致：打开语句表选择一条，插入到本卡片之后
				if (addRequest != null) addRequest.accept(card.index + 1);
			}
			case COPY -> {
				var copy = card.statement.copy();
				if (copy != null) {
					// jump 的目标是语句引用，复制经由文本往返，无法带过来。
					// 这里接回同一目标，行号由 refresh 中的 reindex 重算
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
		if (scrollbar.mouseDragged(mouseY)) return true;
		// 输入框按住后拖动是选择文本，不应触发卡片拖拽
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
		if (drag.end()) refresh();
		return true;
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
		// 一格滚动两行卡片：卡片高度随参数个数变化，须先将其告知滚动条
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
	/** 一帧里要画的一条连线：端点提前算好，{@code hovered} 决定其是否压在其余连线之上绘制。 */
	private record Item(JumpCurve curve, double @NotNull [] from, double @NotNull [] to, boolean hovered) {}
	//endregion
}
