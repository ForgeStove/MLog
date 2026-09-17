package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Picker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 参数控件点开后弹出的选项列表。
 * <p>做成独立界面，对齐 CCG 的 {@code EnumDropdownScreen}：父界面自己画在下方当背景，
 * 鼠标与键盘由 MC 隔离，父界面不必再为它转发任何事件。
 * <p>列表紧贴触发它的参数框弹出，所以既不居中、也没有标题与按钮栏。
 * <p>选项按 {@link Picker#cols()} 分列铺开，对齐 Mindustry 的 {@code showSelect(..., cols, ...)}：
 * {@code jump} 的条件是三列。
 */
@OnlyIn(Dist.CLIENT)
public class OptionPopupScreen extends Screen {
	private static final int ROW_H = 14, SCROLLBAR_W = 3, PAD = 2;
	/**
	 * 每个选项按钮在文字两侧留出的宽度。
	 * <p>和面板内边距 {@link #PAD} 分开：那个管的是面板边缘到内容的距离，这个只影响按钮本身，
	 * 按钮比面板的留白宽松些才不显得挤。
	 */
	private static final int CELL_PAD = 6;
	private final MicroProcessorScreen parent;
	private final Picker picker;
	/** 选中后的回调，用于重建卡片控件（算子会改变参数个数）。 */
	private final Runnable onSelect;
	/** 列宽取最宽的那个选项，各列等宽；行数按选项总数摊到列上。 */
	private final int colW, cols, rows;
	private final int x, y, width, height, visible;
	private double scroll, targetScroll;
	public OptionPopupScreen(MicroProcessorScreen parent, Picker picker, Runnable onSelect) {
		super(Component.empty());
		this.parent = parent;
		this.picker = picker;
		this.onSelect = onSelect;
		var options = picker.options.get();
		cols = Math.clamp(picker.cols(), 1, Math.max(1, options.size()));
		var textW = 0;
		for (var option : options) textW = Math.max(textW, LogicFont.width(LogicFont.text(picker.display(option))));
		colW = textW + CELL_PAD * 2;
		rows = Math.max(1, (options.size() + cols - 1) / cols);
		// 至少显示一行；屏幕太矮时 Math.clamp 会因为上界小于下界而抛异常
		visible = Math.clamp(rows, 1, Math.max(1, (parent.height - PAD * 2) / ROW_H));
		// 不滚动就不给滚动条留位，否则右边平白多出一条空档
		width = Math.min(colW * cols + PAD * 2 + (rows > visible ? SCROLLBAR_W : 0), parent.width);
		height = visible * ROW_H + PAD * 2;
		// 居中到触发它的那个按钮上，对齐 Mindustry 的 setPosition(..., Align.center)；
		// 越出屏幕就顺着推回来，相当于那边的 keepInStage()
		var centerX = picker.anchorCenter();
		var centerY = picker.y + ParamElement.SIZE / 2;
		x = Math.clamp(centerX - width / 2, 0, Math.max(0, parent.width - width));
		y = Math.clamp(centerY - height / 2, 0, Math.max(0, parent.height - height));
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(0, 0, -100);
		var previous = mc.screen;
		mc.screen = parent;
		try {
			parent.render(gui, -1, -1, partialTick);
		} finally {
			// 父界面渲染若抛异常，mc.screen 会永久停在父界面上，那是很难排查的状态
			mc.screen = previous;
		}
		pose.popPose();
		scroll += (targetScroll - scroll) * 0.35;
		if (Math.abs(targetScroll - scroll) < 0.05) scroll = targetScroll;
		// 必须是不透明实心底，否则会透出后面的卡片与世界
		LogicGuiTextures.PANE_SOLID.render(gui, x, y, width, height);
		var options = picker.options.get();
		var current = picker.get.get();
		var scrollbar = rows > visible;
		var contentX = x + PAD;
		var contentY = y + PAD;
		var contentW = width - PAD * 2 - (scrollbar ? SCROLLBAR_W : 0);
		var contentH = visible * ROW_H;
		var first = (int) scroll;
		gui.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
		for (var i = 0; i < visible * cols; i++) {
			var index = first * cols + i;
			if (index >= options.size()) break;
			var option = options.get(index);
			var ox = contentX + i % cols * colW;
			var oy = contentY + i / cols * ROW_H;
			var hovered = mouseX >= ox && mouseX < ox + colW && mouseY >= oy && mouseY < oy + ROW_H;
			if (hovered) LogicCursor.setHand();
			// 对齐 Mindustry 的 Styles.logicTogglet：选中铺强调色底、悬停铺灰底，文字始终是白的
			if (option.equals(current)) gui.fill(ox, oy, ox + colW, oy + ROW_H, ACCENT);
			else if (hovered) gui.fill(ox, oy, ox + colW, oy + ROW_H, HOVER);
			LogicFont.drawOutlinedCentered(gui, LogicFont.text(picker.display(option)), ox + colW / 2, oy + 3, TEXT);
		}
		gui.disableScissor();
		if (!scrollbar) return;
		var trackX = contentX + contentW + 1;
		gui.fill(trackX, contentY, trackX + SCROLLBAR_W, contentY + contentH, PANEL_BORDER);
		var maxScroll = rows - visible;
		var barH = Math.max(8, contentH * visible / rows);
		var barY = contentY + (int) ((contentH - barH) * (maxScroll <= 0 ? 0 : scroll / maxScroll));
		gui.fill(trackX, barY, trackX + SCROLLBAR_W, barY + barH, TEXT_DIM);
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
			onClose();
			return true;
		}
		var col = (int) ((mouseX - (x + PAD)) / colW);
		var row = (int) ((mouseY - (y + PAD)) / ROW_H) + (int) scroll;
		var options = picker.options.get();
		if (col < 0 || col >= cols || row < 0) return true;
		var index = row * cols + col;
		if (index >= options.size()) return true;
		picker.set.accept(options.get(index));
		onClose();
		onSelect.run();
		return true;
	}
	/** 关闭后回到编辑器，而不是走 {@code Screen} 默认的弹出界面栈。 */
	@Override
	public void onClose() {
		mc.setScreen(parent);
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return true;
		targetScroll = Math.clamp(targetScroll - scrollY, 0, Math.max(0, rows - visible));
		return true;
	}
}
