package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Node;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Picker;
import io.github.forgestove.mlog.client.gui.logic.StatementCard.HeaderAction;
/** 画布提供给卡片与参数控件的操作。 */
public interface CanvasActions {
	/** 当前处理器是否为世界处理器。 */
	boolean privileged();
	void openPicker(Picker picker);
	void handleHeaderAction(StatementCard card, HeaderAction action);
	void beginCardDrag(StatementCard card, double mouseX, double mouseY);
	void dragCard(double mouseX, double mouseY);
	void endCardDrag();
	void beginLink(Node node, StatementCard source, double mouseX, double mouseY);
	void dragLink(double mouseX, double mouseY);
	void endLink(double mouseX, double mouseY);
}
