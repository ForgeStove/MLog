package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.logic.GlobalVars;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 内置变量表，对齐 Mindustry 的 {@code GlobalVarsDialog}：竖条 / 名称 / 竖条 / 说明四列，
 * 分组标题用强调色并带一条横线，说明自动换行。
 */
@OnlyIn(Dist.CLIENT)
public class GlobalVarsDialog extends LogicDialogScreen {
	/**
	 * 竖条宽度、行列间距、名称列宽。
	 * <p>和变量表取同一组值，两张表看起来才是一套。
	 */
	private static final int STUB = 3, GAP = 2, NAME_W = 76;
	/**
	 * 内容区宽度。
	 * <p>按 Mindustry 的 {@code prefWidth} 折算：说明栏在那边约 600 单位、行高 40，
	 * 这里行高 16，比例 0.4，再加上名称列与竖条，取这个量级。
	 */
	private static final int CONTENT_W = 320;
	/** 滚动条到屏幕右边的间隙。 */
	private static final int BAR_MARGIN = 2;
	/** 行高。说明会自动换行，实际行高取这里和文字高度的较大者。 */
	private static final int ROW_H = 16;
	/** 分组标题占的高度。 */
	private static final int SECTION_H = 22;
	/** 右侧的滚动条。滚动量、拖动状态与平滑都在它自己身上。 */
	private final ScrollBar scrollbar = new ScrollBar();
	/** 底框的上下边，渲染时记下，鼠标事件按它换算滚动条与行区域。 */
	private int frameTop, frameBottom;
	public GlobalVarsDialog(MicroProcessorScreen parent, LogicDialogScreen returnTo) {
		super(parent, LogicFont.text("gui.mlog.globals"));
		this.returnTo = returnTo;
	}
	@Override
	protected void init() {
		super.init();
		fillScreen();
		addBottomButtons(new BottomButton("gui.mlog.back", LogicIcons.BACK, b -> onClose()));
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		renderBackground(gui, mouseX, mouseY, partialTick);
		renderPanel(gui);
		// 这张表没有底框：Mindustry 那边只有竖条 / 名称 / 说明三列，不像变量表套了层按钮纹理。
		// 表居中，滚动条另贴在屏幕最右边，对齐 Mindustry 那个铺满整屏的 pane
		var left = contentLeft();
		var right = contentRight();
		var top = frameTop = contentTop();
		var bottom = frameBottom = contentBottom();
		var viewH = bottom - top;
		scrollbar.update(viewH, contentHeight());
		gui.enableScissor(left, top, right, bottom);
		renderRows(gui, top, left);
		gui.disableScissor();
		var barX = barX();
		scrollbar.render(gui, barX, top, viewH, contentHeight());
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		var top = frameTop + frameInset();
		var viewH = frameBottom - frameInset() - top;
		if (scrollbar.mousePressed(mouseX, mouseY, barX(), top, viewH, contentHeight())) return true;
		return super.mouseClicked(mouseX, mouseY, button);
	}
	/** @return 滚动条所在的 x，贴着屏幕最右边但留出一点边距。 */
	private int barX() {
		return width - ScrollBar.WIDTH - BAR_MARGIN;
	}
	/** @return 内容总高，没内容时为 0。 */
	private int contentHeight() {
		var h = 0;
		var descW = descWidth();
		for (var entry : GlobalVars.ENTRIES) {
			if (entry.section()) {
				h += SECTION_H;
				continue;
			}
			h += Math.max(ROW_H, mc.font.split(LogicFont.text(entry.descKey()), descW - GAP * 2).size() * 9 + GAP * 2) + GAP;
		}
		return Math.max(0, h - GAP);
	}
	/**
	 * @return 说明面板的宽度：内容区减去两条竖条与列间距、名称格，再减去滚动条真正压进来的那部分。
	 */
	private int descWidth() {
		// 滚动条贴屏幕最右边、内容居中摆：屏幕够宽时它在内容之外，那一条宽度不该再扣，
		// 只有屏幕窄到它压进内容里才让位
		var barLane = Math.max(0, contentRight() - barX());
		return contentWidth() - barLane - STUB * 2 - GAP * 2 - NAME_W;
	}
	/**
	 * @return 内容区宽度。表本身就这么宽，不像基类那样按屏幕比例撑开——
	 * 	Mindustry 那边是表占自己需要的宽度、居中摆在撑满父容器的对话框里。
	 */
	@Override
	protected int contentWidth() {
		return CONTENT_W;
	}
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		var top = frameTop + frameInset();
		var viewH = frameBottom - frameInset() - top;
		if (scrollbar.mouseDragged(mouseY, top, viewH, contentHeight())) return true;
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (scrollbar.dragging()) {
			scrollbar.release();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}
	private void renderRows(GuiGraphics gui, int top, int rowLeft) {
		var cursor = top - (int) Math.round(scrollbar.scroll());
		// 名称格紧贴它左边的竖条，列与列之间留 GAP
		var nameX = rowLeft + STUB;
		var stubDesc = nameX + NAME_W + GAP;
		var descX = stubDesc + STUB;
		var descW = descWidth();
		for (var entry : GlobalVars.ENTRIES) {
			if (entry.section()) {
				// 标题横条按行的实际跨度铺：整块内容区画的话，会比下面的名称格与说明面板长出一截。
				// 左端再往回探 GAP，和左对齐的标题文字对得上，不然看着缺一小段
				var barLeft = rowLeft + STUB - GAP;
				var barRight = descX + descW;
				LogicFont.drawCentered(gui, LogicFont.text(entry.descKey()), (barLeft + barRight) / 2, cursor + 4, ACCENT);
				gui.fill(barLeft, cursor + 16, barRight, cursor + 18, ACCENT);
				cursor += SECTION_H;
				continue;
			}
			var lines = mc.font.split(LogicFont.text(entry.descKey()), descW - GAP * 2);
			var h = Math.max(ROW_H, lines.size() * 9 + GAP * 2);
			// 名称铺灰底，只铺本格、不越过右侧的列间距
			gui.fill(nameX, cursor, nameX + NAME_W, cursor + h, STUB_CELL);
			// 两条竖条是所在格的压暗版：名称那格用灰、说明那格用暗色面板的底色
			gui.fill(rowLeft, cursor, rowLeft + STUB, cursor + h, STUB_DIM);
			gui.fill(stubDesc, cursor, stubDesc + STUB, cursor + h, STUB_DIM);
			LogicFont.draw(gui, LogicFont.literal(entry.name()), nameX + GAP, cursor + 4, TEXT);
			// 说明装在面板纹理里，对齐 Mindustry 的 table(Tex.pane)
			LogicGuiTextures.PANE_SOLID.render(gui, descX, cursor, descW, h);
			var textY = cursor + (h - lines.size() * 9) / 2;
			// 文字再往里让一个面板边框的宽度，别压在边框上
			for (var line : lines) {
				LogicFont.draw(gui, line, descX + GAP + panelInset(), textY, TEXT);
				textY += 9;
			}
			cursor += h + GAP;
		}
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollbar.wheel(-scrollY);
		return true;
	}
}
