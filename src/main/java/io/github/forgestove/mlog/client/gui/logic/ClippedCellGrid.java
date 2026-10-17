package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.LogicClip;
import io.github.forgestove.mlog.client.gui.component.LogicCellButton;
import io.github.forgestove.mlog.client.gui.component.ScrollBar;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.List;
/** 裁剪滚动的格子网格：同步滚动条、按行摆放格子、裁剪命中与绘制。 */
public final class ClippedCellGrid {
	private final ScrollBar scrollbar;
	private @Nullable List<Row> rows;
	private int viewX, viewY, viewW, viewH, cellW;
	public ClippedCellGrid(ScrollBar scrollbar) {
		this.scrollbar = scrollbar;
	}
	/** 可视区、格子宽度与内容高；滚动条位置与滚轮步长一并设置。 */
	public void area(int viewX, int viewY, int viewW, int viewH, int barX, int cellW, int content) {
		this.viewX = viewX;
		this.viewY = viewY;
		this.viewW = viewW;
		this.viewH = viewH;
		this.cellW = cellW;
		scrollbar.area(barX, viewY, viewH, content);
		scrollbar.step(viewH * ScrollBar.WHEEL_RATIO);
		clip();
	}
	public void rows(List<Row> rows) {
		this.rows = rows;
		clip();
	}
	/** 所有格子的命中都限制在可视区内；滚出视野的格子位置过期也不会误命中。 */
	private void clip() {
		if (rows == null) return;
		for (var row : rows) for (var cell : row.cells()) cell.setClip(viewX, viewY, viewX + viewW, viewY + viewH);
	}
	/** 第一可见行的下标，按行高累计定位。 */
	public int firstRow() {
		if (rows == null) return 0;
		var scroll = scrollbar.scroll();
		var cursor = 0.0;
		for (var i = 0; i < rows.size(); i++) {
			cursor += rows.get(i).height();
			if (cursor > scroll) return i;
		}
		return rows.size();
	}
	/** 按滚动量同步位置并在裁剪区内绘制；行外部分只同步位置、不绘制。 */
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		if (rows == null) return;
		var top = viewY;
		var bottom = viewY + viewH;
		var clip = LogicClip.begin(gui, viewX, top, viewX + viewW, bottom);
		var mx = clip.mouseX(mouseX);
		var my = clip.mouseY(mouseY);
		var cursor = top - (int) Math.round(scrollbar.scroll());
		for (var row : rows) {
			var height = row.height();
			var y = cursor;
			cursor += height;
			var onScreen = y + height > top && y < bottom;
			if (onScreen) row.render(gui, viewX, y, mx, my);
			var cells = row.cells();
			for (var i = 0; i < cells.size(); i++) {
				var cell = cells.get(i);
				cell.setX(viewX + i * cellW);
				cell.setY(y);
				cell.setWidth(cellW);
				cell.setHeight(height);
				if (onScreen) cell.render(gui, mx, my, partialTick);
			}
		}
		clip.end();
	}
	/** 一行格子，行高由调用方决定。 */
	public interface Row {
		int height();
		List<LogicCellButton> cells();
		default void render(GuiGraphics gui, int x, int y, int mouseX, int mouseY) {}
	}
	public record CellRow(int height, List<LogicCellButton> cells) implements Row {}
	/** 只有自绘内容、没有格子的行，如分类标题。 */
	public record HeaderRow(int height, Renderer renderer) implements Row {
		@Override
		public List<LogicCellButton> cells() {
			return List.of();
		}
		@Override
		public void render(GuiGraphics gui, int x, int y, int mouseX, int mouseY) {
			renderer.render(gui, x, y, mouseX, mouseY);
		}
	}
	public interface Renderer {
		void render(GuiGraphics gui, int x, int y, int mouseX, int mouseY);
	}
}
