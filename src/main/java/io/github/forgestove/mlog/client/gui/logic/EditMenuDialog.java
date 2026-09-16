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
	private static final int ROW_H = 20, PAD = 6, ICON_GAP = 6;
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
		for (var i = 0; i < Action.values().length; i++) {
			var action = Action.values()[i];
			var bx = contentLeft() + PAD;
			var by = contentTop() + PAD + i * ROW_H;
			var bw = contentWidth() - PAD * 2;
			var hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + ROW_H - 2;
			if (hovered) {
				LogicCursor.setHand();
				gui.fill(bx, by, bx + bw, by + ROW_H - 2, HOVER);
			}
			var color = hovered ? ACCENT : TEXT;
			action.icon.render(gui, bx + 3, LogicIcons.centerY(by, ROW_H), color);
			LogicFont.draw(gui, LogicFont.text(action.key), bx + 3 + action.icon.width() + ICON_GAP, by + 5, color);
		}
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (var i = 0; i < Action.values().length; i++) {
			var bx = contentLeft() + PAD;
			var by = contentTop() + PAD + i * ROW_H;
			if (mouseX >= bx && mouseX < bx + contentWidth() - PAD * 2 && mouseY >= by && mouseY < by + ROW_H - 2) {
				run(Action.values()[i]);
				return true;
			}
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
		CLEAR("gui.mlog.edit.clear", LogicIcons.TRASH),
		COPY("gui.mlog.edit.copy", LogicIcons.EXPORT),
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
