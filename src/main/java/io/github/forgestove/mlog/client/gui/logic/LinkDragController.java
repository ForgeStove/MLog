package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Node;
import org.jetbrains.annotations.Nullable;

import java.util.List;
/** 从跳转节点拖出连线：按下时断开旧目标，松开时把鼠标下的卡片设为新目标；{@code jump} 不能指向自身。 */
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
		// 开始拖拽即断开旧目标，向玩家提示正在重新连线
		node.set.accept(null);
	}
	public void drag(double mouseX, double mouseY) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
	}
	/** 拖动时的吸附用，返回值含起点自身。 */
	public @Nullable StatementCard hoveredAt(double mouseX, double mouseY) {
		for (var card : cards) if (card.isOver(mouseX, mouseY)) return card;
		return null;
	}
	/** 本次松手未改动连线时返回 {@code false}，调用方据此决定是否刷新。 */
	public boolean end(double mouseX, double mouseY) {
		var target = cardAt(mouseX, mouseY);
		var current = node;
		node = null;
		source = null;
		if (current == null) return false;
		current.set.accept(target == null ? null : target.statement);
		return true;
	}
	/** 松手时的判定，起点卡片不算数。 */
	public @Nullable StatementCard cardAt(double mouseX, double mouseY) {
		for (var card : cards) if (card != source && card.isOver(mouseX, mouseY)) return card;
		return null;
	}
}
