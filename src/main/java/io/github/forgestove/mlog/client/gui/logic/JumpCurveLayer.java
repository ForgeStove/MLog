package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.LogicClip;
import io.github.forgestove.mlog.client.gui.LogicGuiTextures;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Node;
import io.github.forgestove.mlog.logic.LStatements.JumpStatement;
import io.github.forgestove.mlog.logic.MLogStatement;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
/** 跳转连线的数据与绘制：重建、复用、避让层、伸出平滑、悬停与拖拽预览。 */
public class JumpCurveLayer {
	private final List<StatementCard> cards;
	private final List<JumpCurve> curves = new ArrayList<>();
	public JumpCurveLayer(List<StatementCard> cards) {
		this.cards = cards;
	}
	/** 按卡片列表重建连线，复用两端未变的旧曲线。 */
	public void rebuild() {
		var old = new ArrayList<>(curves);
		curves.clear();
		for (var card : cards) {
			if (!(card.statement instanceof JumpStatement jump)) continue;
			var target = jump.dest == null ? null : cardOf(jump.dest);
			if (target != null) curves.add(reuse(old, card, target));
		}
		JumpCurveLayout.assignLanes(curves);
	}
	private @Nullable StatementCard cardOf(MLogStatement statement) {
		for (var card : cards) if (card.statement == statement) return card;
		return null;
	}
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
	/** 每帧向目标伸出距离平滑逼近。 */
	public void tick(double delta, int limit) {
		var keep = (float) Math.pow(0.9, delta * 3F);
		for (var curve : curves) curve.reach += (JumpCurveLayout.reach(curve.lane, limit) - curve.reach) * (1 - keep);
	}
	/** 在画布区域内绘制全部连线与拖拽预览。 */
	public void render(
		GuiGraphics gui,
		LinkDragController link,
		int x,
		int y,
		int width,
		int height,
		int mouseX,
		int mouseY
	) {
		// 端点用卡片的真实坐标，出屏部分交给裁剪；夹到边缘会让箭头脱离卡片
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
		renderPreview(gui, link, mx, my);
		// 顶点须在裁剪区关闭前 flush：普通界面下 applyScissor 不代为 flush
		gui.flush();
		clip.end();
	}
	/** 拖拽预览：终点吸附到鼠标下的卡片；该段须在裁剪区之内，中途 return 会漏掉 clip.end()。 */
	private void renderPreview(GuiGraphics gui, LinkDragController link, int mx, int my) {
		var node = link.active() ? link.node() : null;
		if (node == null) return;
		var from = nodeTip(node);
		var hover = link.hoveredAt(link.mouseX(), link.mouseY());
		var to = hover == null ? new double[]{link.mouseX(), link.mouseY()} : arrowCenter(hover);
		// 预览线与源节点同色：悬停时为放置色
		var color = node.isOver(mx, my) ? PLACE : TEXT;
		renderJumpArrow(gui, (int) to[0], (int) to[1], color);
		CurveRenderer.curve(gui, from[0], from[1], to[0], to[1], color, JumpCurveLayout.INITIAL);
	}
	private static int arrowX(StatementCard card) {
		return card.x + card.width - Node.ICON / 4;
	}
	/** 端点滚出屏幕时仍从真实位置画出，交由外层裁剪。 */
	private static double @NotNull [] nodeTip(Node node) {
		return new double[]{node.x + Node.ICON_X + Node.ICON * Node.TIP, node.y + ParamElement.SIZE / 2.0};
	}
	private static double @NotNull [] arrowCenter(StatementCard card) {
		return new double[]{arrowX(card) + Node.ICON / 2.0, card.y + card.height / 2.0};
	}
	/** 左端须压入卡片少许才能与曲线终点衔接。 */
	private static void renderJumpArrow(GuiGraphics gui, int centerX, int centerY, int color) {
		LogicGuiTextures.LOGIC_NODE.renderTintedFlipped(
			gui,
			centerX - Node.ICON / 2,
			centerY - Node.ICON / 2,
			Node.ICON,
			Node.ICON,
			color
		);
	}
	private record Item(JumpCurve curve, double @NotNull [] from, double @NotNull [] to, boolean hovered) {}
}
