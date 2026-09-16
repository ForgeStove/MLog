package io.github.forgestove.mlog.client.gui.logic;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/**
 * 卡片的拖拽重排。
 * <p>被拖拽的卡片暂时脱离排布，位置由鼠标决定；松开时按它的中心落在哪两张卡片之间来定新位置。
 * <p>它不负责刷新：调用方在 {@link #end()} 返回 {@code true} 后重新布局。
 */
@OnlyIn(Dist.CLIENT)
public class CardDragController {
	private final List<StatementCard> cards;
	private @Nullable StatementCard dragging;
	/** 按下点相对卡片顶边的偏移，拖动时保持不变。 */
	private int offsetY;
	public CardDragController(List<StatementCard> cards) {
		this.cards = cards;
	}
	/** @return 正在拖拽的卡片，没有则返回 {@code null}。布局时要跳过它。 */
	public @Nullable StatementCard dragging() {
		return dragging;
	}
	public void begin(StatementCard card, double mouseY) {
		dragging = card;
		offsetY = (int) mouseY - card.y;
	}
	/**
	 * @param placed 当前除被拖卡片之外的顺序（从上到下），位置还没做让位处理。
	 * @return 被拖卡片应该插到第几个。拖拽过程中只算不改，真正的插入等到 {@link #end()}。
	 * <p>被拖卡片的<b>顶边</b>越过某张卡片的<b>中点</b>，才排到它后面。
	 * <p>基准取顶边而不是中心：卡片停在原位时，它的中心正好等于它原本占用那格的中心，
	 * 拿中心比中点会卡在临界值上，动一点点就翻面。
	 */
	public int insertPosition(List<StatementCard> placed) {
		if (dragging == null) return placed.size();
		var top = dragging.y;
		for (var i = 0; i < placed.size(); i++) {
			var card = placed.get(i);
			if (top < card.y + card.height / 2.0) return i;
		}
		return placed.size();
	}
	public void drag(double mouseY) {
		if (dragging != null) dragging.y = (int) mouseY - offsetY;
	}
	/** @return 是否发生了重排，调用方据此决定要不要刷新。 */
	public boolean end() {
		var moved = dragging;
		if (moved == null) return false;
		cards.remove(moved);
		// 和拖拽时的预览共用同一套判定，落点才不会跟占位框对不上
		var index = insertPosition(cards);
		dragging = null;
		cards.add(index, moved);
		return true;
	}
	/** 丢掉拖拽状态（列表被整体替换时用）。 */
	public void cancel() {
		dragging = null;
	}
}
