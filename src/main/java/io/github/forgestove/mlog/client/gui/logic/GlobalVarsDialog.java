package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.logic.GlobalVars;
import io.github.forgestove.mlog.logic.GlobalVars.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.*;

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
/**
 * 内置变量表：竖条 / 名称 / 竖条 / 说明四列，
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
	 * <p>说明栏约 600 单位，按行高 40→16 的 0.4 比例折算，再加上名称列与竖条，取这个量级。
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
	/** 各条说明的折行缓存。条目固定不变，按需建立。 */
	private final Map<Entry, LogicText> descs = new HashMap<>();
	public GlobalVarsDialog(ProcessorScreen parent, LogicDialogScreen returnTo) {
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
		// 这张表没有底框：只有竖条 / 名称 / 说明三列，不像变量表套了层按钮纹理。
		// 表居中，滚动条另贴在屏幕最右边
		var left = contentLeft();
		var right = contentRight();
		var top = contentTop();
		var bottom = contentBottom();
		var viewH = bottom - top;
		scrollbar.area(barX(), top, viewH, contentHeight());
		scrollbar.update();
		var clip = LogicClip.begin(gui, left, top, right, bottom);
		renderRows(gui, top, left);
		clip.end();
		scrollbar.render(gui);
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (scrollbar.mousePressed(mouseX, mouseY)) return true;
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
			h += Math.max(ROW_H, descLines(entry, descW - GAP * 2).size() * 9 + GAP * 2) + GAP;
		}
		return Math.max(0, h - GAP);
	}
	/**
	 * @return 说明面板的宽度：内容区减去两条竖条与列间距、名称格，再减去滚动条真正压进来的那部分。
	 */
	private int descWidth() {
		// 滚动条贴屏幕最右边、内容居中摆：屏幕够宽时它在内容之外，那一条宽度不再扣除，
		// 屏幕窄到它压进内容里则让位
		var barLane = Math.max(0, contentRight() - barX());
		return contentWidth() - barLane - STUB * 2 - GAP * 2 - NAME_W;
	}
	/**
	 * @return 内容区宽度。表本身就这么宽，不像基类那样按屏幕比例撑开。
	 */
	@Override
	protected int contentWidth() {
		return CONTENT_W;
	}
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (scrollbar.mouseDragged(mouseY)) return true;
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
				// 标题横条按行的实际跨度铺：整块内容区绘制会比下面的名称格与说明面板长出一截。
				// 左端再向左延伸 GAP，与左对齐的标题文字对齐
				var barLeft = rowLeft + STUB - GAP;
				var barRight = descX + descW;
				LogicFont.drawCentered(gui, LogicFont.text(entry.descKey()), (barLeft + barRight) / 2, cursor + 4, ACCENT);
				gui.fill(barLeft, cursor + 16, barRight, cursor + 18, ACCENT);
				cursor += SECTION_H;
				continue;
			}
			var lines = descLines(entry, descW - GAP * 2);
			var h = Math.max(ROW_H, lines.size() * 9 + GAP * 2);
			// 名称铺灰底，只铺本格、不越过右侧的列间距
			gui.fill(nameX, cursor, nameX + NAME_W, cursor + h, STUB_CELL);
			// 两条竖条是所在格的压暗版：名称那格用灰、说明那格用暗色面板的底色
			gui.fill(rowLeft, cursor, rowLeft + STUB, cursor + h, STUB_DIM);
			gui.fill(stubDesc, cursor, stubDesc + STUB, cursor + h, STUB_DIM);
			LogicFont.draw(gui, LogicFont.literal(entry.name()), nameX + GAP, cursor + 4, TEXT);
			// 说明装在面板纹理里
			LogicRow.pane(gui, descX, cursor, descW, h, GAP + panelInset(), TEXT, lines);
			cursor += h + GAP;
		}
	}
	/** @return 该条说明按 {@code width} 折行后的文本。 */
	private List<FormattedCharSequence> descLines(Entry entry, int width) {
		return descs.computeIfAbsent(entry, e -> new LogicText(e.descKey())).lines(width);
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollbar.wheel(-scrollY);
		return true;
	}
}
