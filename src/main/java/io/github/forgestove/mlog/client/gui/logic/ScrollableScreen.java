package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.component.ScrollBar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
/** 带滚动条的界面基类：统一登记控件、滚轮分发与逐帧更新。 */
public abstract class ScrollableScreen extends Screen {
	private final List<ScrollBar> scrollbars = new ArrayList<>();
	protected final ScrollBar scrollbar = bar(0);
	protected ScrollableScreen(Component title) {
		super(title);
	}
	/** 按序号取滚动条：用到第几条就创建到第几条，{@link #init()} 时统一 addWidget。 */
	protected final ScrollBar bar(int index) {
		while (scrollbars.size() <= index) scrollbars.add(new ScrollBar());
		return scrollbars.get(index);
	}
	@Override
	protected void init() {
		super.init();
		for (var bar : scrollbars) addWidget(bar);
	}
	/** 滚轮先交给命中区的滚动条，都不命中时退回主滚动条。 */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		wheelStep();
		for (var bar : scrollbars) {
			if (!bar.wheelHit(mouseX, mouseY)) continue;
			bar.wheel(scrollY);
			return true;
		}
		scrollbar.wheel(scrollY);
		return true;
	}
	/** 每帧渲染内容之前调一次。 */
	protected final void updateScrollbars() {
		for (var bar : scrollbars) bar.update();
	}
	/** 子类自绘界面时统一渲染全部滚动条。 */
	protected final void renderScrollbars(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		for (var bar : scrollbars) bar.render(gui, mouseX, mouseY, partialTick);
	}
	/** 覆盖以按当前可视区调整一格距离。 */
	protected void wheelStep() {}
}
