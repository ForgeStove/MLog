package io.github.forgestove.mlog.client.gui.component;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/** 自绘控件容器：维护子控件、焦点与拖动标志，并把键盘与松开/拖动事件转发给子控件。 */
public abstract class WidgetContainer extends AbstractWidget implements ContainerEventHandler {
	protected final List<GuiEventListener> children = new ArrayList<>();
	private @Nullable GuiEventListener focusedChild;
	private boolean containerDragging;
	protected WidgetContainer() {
		super(0, 0, 0, 0, Component.empty());
	}
	@Override
	public List<? extends GuiEventListener> children() {
		return children;
	}
	@Override
	public boolean isDragging() {
		return containerDragging;
	}
	@Override
	public void setDragging(boolean dragging) {
		containerDragging = dragging;
	}
	@Override
	public @Nullable GuiEventListener getFocused() {
		return focusedChild;
	}
	@Override
	public void setFocused(@Nullable GuiEventListener focused) {
		if (focusedChild != null) focusedChild.setFocused(false);
		if (focused != null) focused.setFocused(true);
		focusedChild = focused;
	}
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return ContainerEventHandler.super.keyPressed(keyCode, scanCode, modifiers);
	}
	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		return ContainerEventHandler.super.charTyped(codePoint, modifiers);
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return ContainerEventHandler.super.mouseReleased(mouseX, mouseY, button);
	}
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		return ContainerEventHandler.super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}
	@Override
	public NarrationPriority narrationPriority() {
		return NarrationPriority.NONE;
	}
	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {}
}
