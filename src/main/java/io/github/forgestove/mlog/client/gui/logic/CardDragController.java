package io.github.forgestove.mlog.client.gui.logic;
import org.jetbrains.annotations.Nullable;

import java.util.List;
/** 卡片拖拽重排：被拖卡片脱离排布，松开时按落点定新位置；重排由 {@link #end()} 的返回值决定。 */
public class CardDragController {
	private final List<StatementCard> cards;
	private @Nullable StatementCard dragging;
	/** 按下点相对卡片左上角的偏移，拖动时保持不变。 */
	private int offsetX, offsetY;
	public CardDragController(List<StatementCard> cards) {
		this.cards = cards;
	}
	public @Nullable StatementCard dragging() {
		return dragging;
	}
	public void begin(StatementCard card, double mouseX, double mouseY) {
		dragging = card;
		offsetX = (int) mouseX - card.x;
		offsetY = (int) mouseY - card.y;
	}
	public void drag(double mouseX, double mouseY) {
		if (dragging == null) return;
		dragging.x = (int) mouseX - offsetX;
		dragging.y = (int) mouseY - offsetY;
	}
	/** @return 未处于拖拽时返回 {@code false}。 */
	public boolean end() {
		var moved = dragging;
		if (moved == null) return false;
		cards.remove(moved);
		// 与拖拽时的预览共用同一套判定，落点不会偏离占位框
		var index = insertPosition(cards);
		dragging = null;
		cards.add(index, moved);
		return true;
	}
	/** 按顶边比较：取中心会在原位卡临界值。 */
	public int insertPosition(List<StatementCard> placed) {
		if (dragging == null) return placed.size();
		var top = dragging.y;
		for (var i = 0; i < placed.size(); i++) {
			var card = placed.get(i);
			if (top < card.y + card.height / 2.0) return i;
		}
		return placed.size();
	}
	public void cancel() {
		dragging = null;
	}
}
