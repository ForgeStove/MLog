package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.component.ScrollBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** 带滚动条的界面基类：统一登记控件与滚轮分发。 */
public abstract class ScrollableScreen extends Screen {
	protected final ScrollBar scrollbar = new ScrollBar();
	protected ScrollableScreen(Component title) {
		super(title);
	}
	@Override
	protected void init() {
		super.init();
		addWidget(scrollbar);
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		wheelStep();
		scrollbar.wheel(scrollY);
		return true;
	}
	/** 覆盖以按当前可视区调整一格距离。 */
	protected void wheelStep() {}
}
