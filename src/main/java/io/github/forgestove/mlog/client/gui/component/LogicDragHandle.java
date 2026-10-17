package io.github.forgestove.mlog.client.gui.component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
/** 拖拽句柄：按下即开始，拖动与松开交给持有者；自身不绘制。 */
public class LogicDragHandle extends AbstractWidget {
	private final Handler handler;
	private boolean clipped;
	private int clipLeft, clipTop, clipRight, clipBottom;
	public LogicDragHandle(int width, int height, Handler handler) {
		super(0, 0, width, height, Component.empty());
		this.handler = handler;
	}
	public void setClip(int left, int top, int right, int bottom) {
		clipped = true;
		clipLeft = left;
		clipTop = top;
		clipRight = right;
		clipBottom = bottom;
	}
	@Override
	protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
		handler.press(mouseX, mouseY);
		return true;
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button != 0) return false;
		handler.release(mouseX, mouseY, button);
		return true;
	}
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button != 0) return false;
		handler.drag(mouseX, mouseY);
		return true;
	}
	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return super.isMouseOver(mouseX, mouseY) && (
			!clipped || mouseX >= clipLeft && mouseX < clipRight && mouseY >= clipTop && mouseY < clipBottom
		);
	}
	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {}
	/** 拖拽回调。 */
	public interface Handler {
		void press(double mouseX, double mouseY);
		void drag(double mouseX, double mouseY);
		void release(double mouseX, double mouseY, int button);
	}
}
