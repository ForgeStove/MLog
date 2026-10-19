package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.component.*;
import io.github.forgestove.mlog.client.gui.logic.ClippedCellGrid.*;
import io.github.forgestove.mlog.client.render.ParticlePreview;
import io.github.forgestove.mlog.logic.LStatements.SenseNames;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.particle.Particle;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static io.github.forgestove.mlog.client.gui.LogicColors.TEXT;
/** 粒子效果：网格预览所有粒子，点击选中。 */
public class EffectsDialog extends LogicDialogScreen {
	/** 按 280 单位、行高 40→16 的 0.4 比例折算。 */
	private static final int PAD = 4, CELL = 112;
	/** 标题横条下沿再留一点间距，网格从这里开始。 */
	private static final int CONTENT_TOP = 20;
	/** 棋盘格纹理 128 见方、每格 16 像素；悬停时叠一层黑。 */
	private static final int HOVER_DARKEN = 0x33000000;
	/** 每刻 50ms；一帧内最多补 4 刻，避免长时间未渲染后一次补太多。 */
	private static final long TICK_NANOS = 50_000_000L;
	private final Consumer<String> onPick;
	private final ClippedCellGrid grid = new ClippedCellGrid(scrollbar);
	private final List<LogicCellButton> cells = new ArrayList<>();
	/** 每种粒子的模拟状态，重建控件时保留。 */
	private final Map<String, Preview> previews = new HashMap<>();
	public EffectsDialog(ProcessorScreen parent, Consumer<String> onPick) {
		super(parent, LogicFont.text("mlog.gui.effects"));
		this.onPick = onPick;
	}
	private static void outline(GuiGraphics gui, int x, int y) {
		gui.fill(x, y, x + CELL, y + 1, 0xFF000000);
		gui.fill(x, y + CELL - 1, x + CELL, y + CELL, 0xFF000000);
		gui.fill(x, y + 1, x + 1, y + CELL - 1, 0xFF000000);
		gui.fill(x + CELL - 1, y + 1, x + CELL, y + CELL - 1, 0xFF000000);
	}
	/** 棋盘格底一次绘制；悬停时压暗。 */
	private static void checker(GuiGraphics gui, int x, int y, boolean hovered) {
		LogicGuiTextures.CHECKER.render(gui, x, y, CELL, CELL);
		if (hovered) gui.fill(x, y, x + CELL, y + CELL, HOVER_DARKEN);
	}
	@Override
	protected int contentWidth() {
		return width;
	}
	@Override
	protected int contentTop() {
		return CONTENT_TOP;
	}
	/** 与底部按钮上沿留出和按钮到屏幕底边相同的外边距。 */
	@Override
	protected int contentBottom() {
		return buttonTop() - MARGIN;
	}
	/** 不重绘父界面：整屏预览下它被完全遮住，逐帧重画编辑画布开销很大。 */
	@Override
	protected void renderParent(GuiGraphics gui) {}
	@Override
	protected void init() {
		super.init();
		addBottomButtons(new BottomButton("mlog.gui.back", LogicIcons.BACK, b -> onClose()));
		for (var cell : cells) removeWidget(cell);
		cells.clear();
		var options = SenseNames.PARTICLES;
		var cols = Math.max(1, (width - PAD * 2 - ScrollBar.WIDTH) / CELL);
		var rows = (options.size() + cols - 1) / cols;
		var viewX = (width - cols * CELL) / 2;
		var viewH = Math.max(0, contentBottom() - contentTop());
		grid.area(viewX, contentTop(), cols * CELL, viewH, width - PAD - ScrollBar.WIDTH, CELL, rows * CELL);
		scrollbar.reset();
		var gridRows = new ArrayList<Row>();
		for (var r = 0; r < rows; r++) {
			var from = r * cols;
			var to = Math.min(from + cols, options.size());
			var rowCells = new ArrayList<LogicCellButton>();
			for (var i = from; i < to; i++) {
				var option = options.get(i);
				var cell = new LogicCellButton(
					CELL,
					CELL,
					(self, gui, mouseX, mouseY, hovered, pressed) -> renderCell(gui, self, option, hovered)
				);
				cell.setAction(() -> pick(option));
				addWidget(cell);
				cells.add(cell);
				rowCells.add(cell);
			}
			gridRows.add(new CellRow(CELL, rowCells));
		}
		grid.rows(gridRows);
	}
	private void pick(String option) {
		LogicSounds.button();
		onPick.accept(option);
		onClose();
	}
	private void renderCell(GuiGraphics gui, LogicCellButton cell, String option, boolean hovered) {
		var x = cell.getX();
		var y = cell.getY();
		if (hovered) LogicCursor.setHand();
		checker(gui, x, y, hovered);
		// 预览限制在格子里，避免粒子的四边形溢出到相邻格
		var clip = LogicClip.begin(gui, x, y, x + CELL, y + CELL);
		try {
			previews.computeIfAbsent(option, Preview::new).render(gui, x, y);
		} finally {
			clip.end();
		}
		LogicFont.drawOutlined(gui, LogicFont.text(SenseNames.display(option)), x + 2, y + CELL - 10, TEXT);
		outline(gui, x, y);
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		scrollbar.update();
		renderBackground(gui, mouseX, mouseY, partialTick);
		renderPanel(gui);
		grid.render(gui, mouseX, mouseY, partialTick);
		scrollbar.render(gui, mouseX, mouseY, partialTick);
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	/** 单个格子的循环播放状态；取景固定在出生位置，缩放按粒子大小一次定好。 */
	private static final class Preview {
		private final String option;
		private @Nullable Particle particle;
		private long lastNanos, pendingNanos;
		private float anchorX, anchorY, span = 0.01F;
		private boolean anchored;
		private Preview(String option) {
			this.option = option;
		}
		private void spawn() {
			particle = ParticlePreview.create(option);
			anchored = false;
		}
		private void render(GuiGraphics gui, int x, int y) {
			var now = System.nanoTime();
			if (lastNanos == 0) lastNanos = now;
			pendingNanos += Math.min(now - lastNanos, TICK_NANOS * 4);
			lastNanos = now;
			while (pendingNanos >= TICK_NANOS) {
				pendingNanos -= TICK_NANOS;
				if (particle == null || !particle.isAlive()) spawn();
				if (particle == null) return;
				try {
					particle.tick();
				} catch (RuntimeException ignored) {
					particle = null;
					return;
				}
			}
			if (particle == null || !particle.isAlive()) spawn();
			if (particle == null) return;
			var frame = ParticlePreview.capture(particle, pendingNanos / (float) TICK_NANOS);
			var bounds = ParticlePreview.bounds(frame);
			if (bounds == null) return;
			if (!anchored) {
				var fixed = ParticlePreview.measure(option);
				if (fixed == null) fixed = bounds;
				anchorX = fixed.centerX();
				anchorY = fixed.centerY();
				span = Math.max(Math.max(fixed.spanX(), fixed.spanY()), 0.01F);
				anchored = true;
			}
			var scale = CELL * 0.5F / span;
			var centerX = x + CELL / 2F;
			var centerY = y + CELL / 2F - 6F;
			ParticlePreview.draw(gui, frame, centerX - anchorX * scale, centerY + anchorY * scale, scale);
		}
	}
}
