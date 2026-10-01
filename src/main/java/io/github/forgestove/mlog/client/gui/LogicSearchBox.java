package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.*;
import net.neoforged.api.distmarker.*;
/**
 * 搜索框：放大镜、输入框与其下的横线合为一行，由 {@link #layout} 摆到给定的行边界上。
 * <p>放大镜画在控件边界以左、横线画在下方，两者都在列表等相邻内容之外。
 */
@OnlyIn(Dist.CLIENT)
public class LogicSearchBox extends LogicEditBox {
	/** 行高，宿主按它算搜索行占多高。 */
	public static final int HEIGHT = 14;
	/** 图标到输入框的间距、输入框相对行顶的下移量。 */
	private static final int ICON_GAP = 4, NUDGE = 3;
	/** 行的左端、顶端与右端，放大镜与横线按它定位；输入框在两者之间。 */
	private int rowLeft, rowTop, rowRight;
	public LogicSearchBox() {
		super(0, 0, 0, HEIGHT, LogicFont.text("gui.mlog.search"));
		setBordered(false);
	}
	/** 按整行摆放：放大镜贴左，输入框居中，横线贴右。 */
	public void layout(int left, int top, int right) {
		rowLeft = left;
		rowTop = top;
		rowRight = right;
		setX(left + LogicIcons.SEARCH.width() + ICON_GAP);
		setY(top + NUDGE);
		setWidth(Math.max(0, right - getX()));
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
