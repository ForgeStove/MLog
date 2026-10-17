package io.github.forgestove.mlog.client.gui.component;
import io.github.forgestove.mlog.client.gui.*;
import net.minecraft.client.gui.GuiGraphics;
/** 放大镜、输入框与横线合为一行，由 {@link #layout} 定位到给定行边界；放大镜与横线绘制在控件边界之外。 */
public class LogicSearchBox extends LogicEditBox {
	public static final int HEIGHT = 12;
	/** 图标到输入框的间距。 */
	private static final int ICON_GAP = 4;
	/** 放大镜与横线按它定位；输入框在两者之间。 */
	private int rowLeft, rowTop, rowRight;
	public LogicSearchBox() {
		super(0, 0, 0, LogicFont.LINE_H, LogicFont.text("gui.mlog.search"));
		setBordered(false);
	}
	public void layout(int left, int top, int right) {
		rowLeft = left;
		rowTop = top;
		rowRight = right;
		setX(left + LogicIcons.SEARCH.width() + ICON_GAP);
		setWidth(Math.max(0, right - getX()));
		// 控件占整行，文字在内部垂直居中
		setY(top);
		setHeight(HEIGHT);
	}
	@Override
	public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		LogicIcons.SEARCH.render(gui, rowLeft, LogicIcons.centerY(rowTop, HEIGHT), LogicColors.TEXT);
		super.renderWidget(gui, mouseX, mouseY, partialTick);
		LogicGuiTextures.UNDERLINE.renderTinted(
			gui,
			getX(),
			rowTop + HEIGHT,
			rowRight - getX(),
			LogicGuiTextures.UNDERLINE_H,
			LogicColors.BORDER
		);
	}
}
