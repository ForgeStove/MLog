package io.github.forgestove.mlog.client.event;
import io.github.forgestove.mlog.client.gui.component.LogicEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre;
/** 原版容器不会在点击外部时清除输入框焦点，这里在鼠标按下时补上这个行为。 */
public class EditBoxFocus {
	public static void onMousePressed(Pre event) {
		// 输入框可能位于画布等容器的内部，所以要逐层向下找到最里层的控件
		var focused = event.getScreen().getFocused();
		while (focused instanceof ContainerEventHandler container) {
			var child = container.getFocused();
			if (child == null) break;
			focused = child;
		}
		if (!(focused instanceof LogicEditBox box)) return;
		if (!box.isMouseOver(event.getMouseX(), event.getMouseY())) box.setFocused(false);
	}
}
