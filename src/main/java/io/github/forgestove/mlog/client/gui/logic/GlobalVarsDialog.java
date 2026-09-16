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
	private static final int PAD = 6, STUB = 2, GAP = 4, NAME_W = 76, ROW_H = 16, ROW_GAP = 4;
	/** 分组标题占的高度。 */
	private static final int SECTION_H = 22;
	private double scroll, targetScroll;
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
		var top = contentTop() + PAD;
		var bottom = contentBottom();
		var viewH = bottom - top;
		targetScroll = Math.clamp(targetScroll, 0, Math.max(0, contentHeight() - viewH));
		scroll += (targetScroll - scroll) * 0.35;
		if (Math.abs(targetScroll - scroll) < 0.5) scroll = targetScroll;
		gui.enableScissor(contentLeft() + PAD, top, contentRight() - PAD, bottom);
		renderRows(gui, top);
		gui.disableScissor();
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	private void renderRows(GuiGraphics gui, int top) {
		var cursor = top - (int) scroll;
		var stubName = contentLeft() + PAD;
		var nameX = stubName + STUB + GAP;
		var stubDesc = nameX + NAME_W + GAP;
		var descX = stubDesc + STUB + GAP;
		var descW = descWidth();
		for (var entry : GlobalVars.ENTRIES) {
			if (entry.section()) {
				LogicFont.drawCentered(gui, LogicFont.text(entry.descKey()), width / 2, cursor + 4, ACCENT);
				gui.fill(contentLeft() + PAD, cursor + 16, contentRight() - PAD, cursor + 18, ACCENT);
				cursor += SECTION_H;
				continue;
			}
			var lines = mc.font.split(LogicFont.text(entry.descKey()), descW);
			var h = Math.max(ROW_H, lines.size() * 9 + 4);
			gui.fill(stubName, cursor + 2, stubName + STUB, cursor + h - 2, STUB);
			gui.fill(stubDesc, cursor + 2, stubDesc + STUB, cursor + h - 2, STUB);
			// 名称是灰底深色字的标签，对应 Mindustry 的 stack(Image, Label)
			var name = LogicFont.literal(entry.name());
			var nameW = mc.font.width(name) + 8;
			gui.fill(nameX, cursor, nameX + nameW, cursor + ROW_H, TEXT_DIM);
			LogicFont.draw(gui, name, nameX + 4, cursor + 4, HEADER_TEXT);
			// 说明装在带边框的暗色面板里
			gui.fill(descX, cursor, descX + descW, cursor + h, 0x40000000);
			gui.renderOutline(descX, cursor, descW, h, BORDER);
			var textY = cursor + (h - lines.size() * 9) / 2;
			for (var line : lines) {
				LogicFont.draw(gui, line, descX + 3, textY, TEXT);
				textY += 9;
			}
			cursor += h + ROW_GAP;
		}
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
			h += Math.max(ROW_H, mc.font.split(LogicFont.text(entry.descKey()), descW).size() * 9 + 4) + ROW_GAP;
		}
		return Math.max(0, h - ROW_GAP);
	}
	private int descWidth() {
		return contentWidth() - PAD * 2 - STUB * 2 - GAP * 3 - NAME_W;
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		var viewH = contentBottom() - (contentTop() + PAD);
		targetScroll = Math.clamp(targetScroll - scrollY * 12, 0, Math.max(0, contentHeight() - viewH));
		return true;
	}
}
