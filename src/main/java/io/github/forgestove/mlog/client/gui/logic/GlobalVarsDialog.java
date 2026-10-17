package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.component.*;
import io.github.forgestove.mlog.logic.GlobalVars;
import io.github.forgestove.mlog.logic.GlobalVars.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
/** 内置变量表：竖条 / 名称 / 竖条 / 说明四列，分组标题用强调色并带一条横线，说明自动换行。 */
public class GlobalVarsDialog extends LogicDialogScreen {
	private static final int STUB = 3, GAP = 2, NAME_W = 76;
	/** 说明栏约 600 单位，按行高 40→16 的 0.4 比例折算，再加上名称列与竖条。 */
	private static final int CONTENT_W = 320;
	private static final int BAR_MARGIN = 2;
	/** 说明会自动换行，行高取这里与文字高度的较大者。 */
	private static final int ROW_H = 16;
	private static final int SECTION_H = 22;
	/** 条目固定不变，折行缓存按需建立。 */
	private final Map<Entry, LogicText> descs = new HashMap<>();
	public GlobalVarsDialog(ProcessorScreen parent, LogicDialogScreen returnTo) {
		super(parent, LogicFont.text("gui.mlog.globals"));
		this.returnTo = returnTo;
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		renderBackground(gui, mouseX, mouseY, partialTick);
		renderPanel(gui);
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
		scrollbar.render(gui, mouseX, mouseY, partialTick);
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	@Override
	protected void init() {
		super.init();
		addBottomButtons(new BottomButton("gui.mlog.back", LogicIcons.BACK, b -> onClose()));
	}
	/** 贴屏幕最右边并留出边距。 */
	private int barX() {
		return width - ScrollBar.WIDTH - BAR_MARGIN;
	}
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
	private int descWidth() {
		// 滚动条在内容之外则不扣除其宽度，与内容重叠时才扣除
		var barLane = Math.max(0, contentRight() - barX());
		return contentWidth() - barLane - STUB * 2 - GAP * 2 - NAME_W;
	}
	@Override
	protected int contentWidth() {
		return CONTENT_W;
	}
	private void renderRows(GuiGraphics gui, int top, int rowLeft) {
		var cursor = top - (int) Math.round(scrollbar.scroll());
		var nameX = rowLeft + STUB;
		var stubDesc = nameX + NAME_W + GAP;
		var descX = stubDesc + STUB;
		var descW = descWidth();
		for (var entry : GlobalVars.ENTRIES) {
			if (entry.section()) {
				// 标题横条按行实际跨度铺，左端再向左延伸 GAP 以对齐标题文字
				var barLeft = rowLeft + STUB - GAP;
				var barRight = descX + descW;
				LogicFont.drawCentered(gui, LogicFont.text(entry.descKey()), (barLeft + barRight) / 2, cursor + 4, ACCENT);
				gui.fill(barLeft, cursor + 16, barRight, cursor + 18, ACCENT);
				cursor += SECTION_H;
				continue;
			}
			var lines = descLines(entry, descW - GAP * 2);
			var h = Math.max(ROW_H, lines.size() * 9 + GAP * 2);
			// 名称铺灰底，仅限本格，不越过右侧列间距
			gui.fill(nameX, cursor, nameX + NAME_W, cursor + h, STUB_CELL);
			// 两条竖条取所在格的暗色：名称格用灰、说明格用面板底色
			gui.fill(rowLeft, cursor, rowLeft + STUB, cursor + h, STUB_DIM);
			gui.fill(stubDesc, cursor, stubDesc + STUB, cursor + h, STUB_DIM);
			LogicFont.draw(gui, LogicFont.literal(entry.name()), nameX + GAP, cursor + 4, TEXT);
			LogicRow.pane(gui, descX, cursor, descW, h, GAP + panelInset(), TEXT, lines);
			cursor += h + GAP;
		}
	}
	private List<FormattedCharSequence> descLines(Entry entry, int width) {
		return descs.computeIfAbsent(entry, e -> new LogicText(e.descKey())).lines(width);
	}
}
