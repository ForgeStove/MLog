package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.logic.LAssembler;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.*;

import java.util.List;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 编辑菜单，对应 Mindustry 的 {@code @edit}：清除 / 复制到剪贴板 / 从剪贴板导入 / 重新运行。 */
@OnlyIn(Dist.CLIENT)
public class EditMenuDialog extends LogicDialogScreen {
	/**
	 * 菜单项的高度。
	 * <p>Mindustry 那边是 {@code size(280f, 60f)}，行高 40 折过来，60 × 0.4 = 24。
	 */
	private static final int ROW_H = 24;
	/**
	 * 图标距按钮左边缘的距离。
	 * <p>和 {@link LogicButton} 用同一个值、同一种排法：图标贴左，文字在图标右边剩下的那段里居中。
	 */
	private static final int ICON_PAD = 6;
	/**
	 * 内容区宽度。
	 * <p>Mindustry 的菜单按钮是 {@code size(280f, 60f)}，不是撑满内容区；折过来按同一比例取这个量级，
	 * 不然一行拉得太长。
	 */
	private static final int CONTENT_W = 120;
	/** 底框在内容区里垂直居中，行的起点记下来给事件用。 */
	private int listTop;
	public EditMenuDialog(MicroProcessorScreen parent) {
		super(parent, LogicFont.text("gui.mlog.edit"));
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
		// 菜单项装在一个按钮纹理的底框里，对齐 Mindustry 的 table(Tex.button)。框高按项数收、在内容区居中
		var inset = frameInset();
		var listH = Action.values().length * ROW_H;
		var frameH = Math.min(listH + inset * 2, contentBottom() - contentTop());
		listTop = contentTop() + (contentBottom() - contentTop() - frameH) / 2 + inset;
		renderContentFrame(gui, listTop - inset, frameH);
		var bx = contentLeft() + inset;
		var bw = contentWidth() - inset * 2;
		for (var i = 0; i < Action.values().length; i++) {
			var action = Action.values()[i];
			var by = listTop + i * ROW_H;
			var hovered = isOver(mouseX, mouseY, bx, by, bw);
			// 常态纯黑底、悬停铺一层灰，文字恒为白，对齐 Mindustry 的 Styles.flatt
			gui.fill(bx, by, bx + bw, by + ROW_H, hovered ? FLAT_OVER : 0xFF000000);
			if (hovered) LogicCursor.setHand();
			// 图标贴左、文字在图标右边剩下的那段里居中，和 LogicButton 一致
			var iconX = bx + ICON_PAD;
			action.icon.render(gui, iconX, LogicIcons.centerY(by, ROW_H), TEXT);
			var textCenter = (iconX + action.icon.width() + bx + bw) / 2;
			LogicFont.drawCentered(gui, LogicFont.text(action.key), textCenter, by + ROW_H / 2 - 4, TEXT);
		}
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	@Override
	protected int contentWidth() {
		return CONTENT_W;
	}
	/** @return 鼠标是否落在第 {@code by} 行的按钮上。 */
	private static boolean isOver(double mouseX, double mouseY, int bx, int by, int bw) {
		return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + ROW_H;
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		var inset = frameInset();
		var bx = contentLeft() + inset;
		var bw = contentWidth() - inset * 2;
		for (var i = 0; i < Action.values().length; i++)
			if (isOver(mouseX, mouseY, bx, listTop + i * ROW_H, bw)) {
				run(Action.values()[i]);
				return true;
			}
		return super.mouseClicked(mouseX, mouseY, button);
	}
	private void run(Action action) {
		var canvas = parent.getCanvas();
		switch (action) {
			case CLEAR -> canvas.setStatements(List.of());
			case COPY -> mc.keyboardHandler.setClipboard(LAssembler.write(canvas.statements()));
			case LOAD -> {
				try {
					var text = mc.keyboardHandler.getClipboard();
					canvas.setStatements(LAssembler.read(text));
				} catch (RuntimeException ignored) {
					// 剪贴板里不是合法逻辑代码，保持原样
				}
			}
			// 即使代码没改也要重发，否则服务端不会重新编译
			case RESTART -> parent.save(true);
		}
		onClose();
	}
	/** 菜单项、图标与对应的 lang key。 */
	private enum Action {
		// 图标对齐 Mindustry 的 @edit：清除用 Icon.cancel、复制用 Icon.copy
		CLEAR("gui.mlog.edit.clear", LogicIcons.CANCEL),
		COPY("gui.mlog.edit.copy", LogicIcons.COPY),
		LOAD("gui.mlog.edit.load", LogicIcons.DOWNLOAD),
		RESTART("gui.mlog.edit.restart", LogicIcons.REFRESH);
		final String key;
		final LogicIcons icon;
		Action(String key, LogicIcons icon) {
			this.key = key;
			this.icon = icon;
		}
	}
}
