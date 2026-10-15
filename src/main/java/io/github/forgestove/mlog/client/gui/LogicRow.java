package io.github.forgestove.mlog.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
/** 表格行里的格：铺底后把折行文字按行数垂直居中；行高须由调用方按同一份折行结果算出。 */
public final class LogicRow {
	/** 文字左侧留 {@code pad}。 */
	public static void cell(
		GuiGraphics gui,
		int x,
		int y,
		int width,
		int height,
		int fill,
		int pad,
		int color,
		List<FormattedCharSequence> lines
	) {
		gui.fill(x, y, x + width, y + height, fill);
		draw(gui, x + pad, y, height, color, lines);
	}
	public static void pane(GuiGraphics gui, int x, int y, int width, int height, int pad, int color, List<FormattedCharSequence> lines) {
		LogicGuiTextures.PANE_SOLID.render(gui, x, y, width, height);
		draw(gui, x + pad, y, height, color, lines);
	}
	/** 整块按行数在格内垂直居中。 */
	private static void draw(GuiGraphics gui, int x, int y, int height, int color, List<FormattedCharSequence> lines) {
		var lineY = y + height / 2 - lines.size() * 9 / 2;
		for (var line : lines) {
			LogicFont.draw(gui, line, x, lineY, color);
			lineY += 9;
		}
	}
}
