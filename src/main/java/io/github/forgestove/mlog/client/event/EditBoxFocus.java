package io.github.forgestove.mlog.client.event;
import io.github.forgestove.mlog.client.gui.component.LogicEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre;

/** 点击输入框以外时清除其焦点；原版容器不会自行处理。 */
public class EditBoxFocus {
	public static void onMousePressed(Pre event) {
		// 焦点可能落在画布等容器内部的输入框上，逐层向下取叶子控件
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
