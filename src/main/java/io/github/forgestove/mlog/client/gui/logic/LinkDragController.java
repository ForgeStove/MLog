package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Node;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;
/**
 * 从跳转节点拖出连线的过程。
 * <p>按下时先断开旧目标，松开时把鼠标下的卡片设成新目标；落在空白处就是没有目标。
 * <p>{@code jump} 不能指向自身，所以找目标时一律排除起点卡片。
 */
@OnlyIn(Dist.CLIENT)
public class LinkDragController {
	private final List<StatementCard> cards;
	private @Nullable Node node;
	private @Nullable StatementCard source;
	private double mouseX, mouseY;
	public LinkDragController(List<StatementCard> cards) {
		this.cards = cards;
	}
	public boolean active() {
		return node != null;
	}
	/** @return 正在拖拽的节点，用于画跟随鼠标的那条线。 */
	public @Nullable Node node() {
		return node;
	}
	public double mouseX() {
		return mouseX;
	}
	public double mouseY() {
		return mouseY;
	}
	public void begin(Node node, StatementCard source, double mouseX, double mouseY) {
		this.node = node;
		this.source = source;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		// 开始拖拽就先断开旧目标，给玩家"重新连线"的反馈
		node.set.accept(null);
	}
	public void drag(double mouseX, double mouseY) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
	}
	/**
	 * @return 鼠标下的卡片，<b>含</b>起点自身。拖动时的吸附用它。
	 * 	<p>吸到自己的卡片上只给位置反馈，松手仍然连不上——判定走 {@link #cardAt}。
	 */
	public @Nullable StatementCard hoveredAt(double mouseX, double mouseY) {
		for (var card : cards) if (card.isOver(mouseX, mouseY)) return card;
		return null;
	}
	/** @return 是否改动过连线，调用方据此决定要不要刷新。 */
	public boolean end(double mouseX, double mouseY) {
		var target = cardAt(mouseX, mouseY);
		var current = node;
		node = null;
		source = null;
		if (current == null) return false;
		current.set.accept(target == null ? null : target.statement);
		return true;
	}
	/**
	 * @return 鼠标下的卡片，排除起点自身；不在任何卡片上则返回 {@code null}。
	 * 	<p>{@code jump} 不能指向自己，这是松手时的判定，所以起点卡片不算数。
	 */
	public @Nullable StatementCard cardAt(double mouseX, double mouseY) {
		for (var card : cards) if (card != source && card.isOver(mouseX, mouseY)) return card;
		return null;
	}
}
