package io.github.forgestove.mlog.client.gui.component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
/** 自绘内容的按钮；可由 {@link #setClip} 把命中限制在滚动列表的可视区内。 */
public class LogicCellButton extends ReleaseButton implements Clippable {
	private final Renderer renderer;
	private @Nullable Runnable action;
	private boolean clipped;
	private int clipLeft, clipTop, clipRight, clipBottom;
	public LogicCellButton(int width, int height, Renderer renderer) {
		super(0, 0, width, height, Component.empty(), b -> {});
		this.renderer = renderer;
	}
	public void setAction(@Nullable Runnable action) {
		this.action = action;
	}
	@Override
	public void setClip(int left, int top, int right, int bottom) {
		clipped = true;
		clipLeft = left;
		clipTop = top;
		clipRight = right;
		clipBottom = bottom;
	}
	@Override
	public void onPress() {
		if (action != null) action.run();
	}
	@Override
	protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		renderer.render(this, gui, mouseX, mouseY, isHovered(), isPressed());
	}
	/** 命中判定同样受裁剪区限制，否则列表外的格子会被当成本次点击的目标。 */
	@Override
	protected boolean clicked(double mouseX, double mouseY) {
		return active && visible && isMouseOver(mouseX, mouseY);
	}
	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return super.isMouseOver(mouseX, mouseY) && (
			!clipped
				|| mouseX >= clipLeft && mouseX < clipRight && mouseY >= clipTop && mouseY < clipBottom
		);
	}
	/** 按下音由动作自身播放，控件不发声。 */
	@Override
	public void playDownSound(SoundManager handler) {}
	/** 绘制与悬停回调；坐标与尺寸取自按钮自身。 */
	public interface Renderer {
		void render(LogicCellButton button, GuiGraphics gui, int mouseX, int mouseY, boolean hovered, boolean pressed);
	}
}
