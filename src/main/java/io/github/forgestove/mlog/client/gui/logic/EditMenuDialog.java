package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.component.LogicCellButton;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import io.github.forgestove.mlog.core.net.ProcessorTagPayload;
import io.github.forgestove.mlog.logic.LAssembler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 编辑菜单：清除 / 复制到剪贴板 / 从剪贴板导入 / 重新运行。 */
public class EditMenuDialog extends LogicDialogScreen {
	/** 按行高 40→16 的 0.4 比例折算，60 × 0.4 = 24。 */
	private static final int ROW_H = 24;
	/** 图标贴左，文字在图标右边剩下的那段里居中。 */
	private static final int ICON_PAD = 6;
	/** 菜单按钮不撑满内容区，280 按行高 40→16 的 0.4 比例折成这个量级。 */
	private static final int CONTENT_W = 120;
	/** 底框在内容区里垂直居中，行的起点由此确定。 */
	private int listTop;
	/** 底框高度；内容不足一屏时按项数收缩。 */
	private int frameHeight;
	public EditMenuDialog(ProcessorScreen parent) {
		super(parent, LogicFont.text("gui.mlog.edit"));
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		renderBackground(gui, mouseX, mouseY, partialTick);
		renderPanel(gui);
		// 菜单项装在一个按钮纹理的底框里。框高按项数收、在内容区居中
		var inset = frameInset();
		renderContentFrame(gui, listTop - inset, frameHeight);
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	@Override
	protected void init() {
		super.init();
		addBottomButtons(new BottomButton("gui.mlog.back", LogicIcons.BACK, b -> onClose()));
		var inset = frameInset();
		var bw = contentWidth() - inset * 2;
		var list = LinearLayout.vertical();
		for (var action : Action.values()) {
			// 只有世界处理器可以命名
			if (action == Action.NAME && !parent.privileged()) continue;
			var button = list.addChild(new LogicCellButton(
				bw, ROW_H, (self, gui, mouseX, mouseY, hovered, pressed) -> {
					var x = self.getX();
					var y = self.getY();
					// 常态纯黑底、悬停铺灰；图标贴左，文字在右侧居中
					gui.fill(x, y, x + self.getWidth(), y + self.getHeight(), hovered ? FLAT_OVER : 0xFF000000);
					if (hovered) LogicCursor.setHand();
					var iconX = x + ICON_PAD;
					action.icon.render(gui, iconX, LogicIcons.centerY(y, ROW_H), TEXT);
					var textCenter = (iconX + action.icon.width() + x + self.getWidth()) / 2;
					LogicFont.drawCentered(gui, LogicFont.text(action.key), textCenter, y + ROW_H / 2 - 4, TEXT);
				}
			));
			button.setAction(() -> run(action));
		}
		list.arrangeElements();
		frameHeight = Math.min(list.getHeight() + inset * 2, contentBottom() - contentTop());
		listTop = contentTop() + (contentBottom() - contentTop() - frameHeight) / 2 + inset;
		list.setPosition(contentLeft() + inset, listTop);
		list.visitWidgets(this::addRenderableWidget);
	}
	@Override
	protected int contentWidth() {
		return CONTENT_W;
	}
	private void run(Action action) {
		LogicSounds.button();
		var canvas = parent.getCanvas();
		switch (action) {
			case CLEAR -> canvas.setStatements(List.of());
			case COPY -> mc.keyboardHandler.setClipboard(LAssembler.write(canvas.statements()));
			case LOAD -> {
				try {
					var text = mc.keyboardHandler.getClipboard();
					canvas.setStatements(LAssembler.read(text, parent.privileged()));
				} catch (RuntimeException ignored) {
					// 剪贴板里不是合法逻辑代码，保持原样
				}
			}
			// 即使代码没改也要重发，否则服务端不会重新编译
			case RESTART -> parent.save(true);
			case NAME -> {
				if (!(parent.getMenu().getBlockEntity() instanceof AbstractProcessorBlockEntity processor)) return;
				var pos = parent.getMenu().getPos();
				mc.setScreen(new TextInputDialog(
					parent,
					LogicFont.text("gui.mlog.edit.name"),
					LogicFont.text("gui.mlog.edit.name.label"),
					LogicFont.text("gui.mlog.edit.name.hint"),
					ProcessorTagPayload.MAX_TAG_LENGTH,
					processor.getTag(),
					tag -> PacketDistributor.sendToServer(new ProcessorTagPayload(pos, tag))
				));
				return;
			}
		}
		onClose();
	}
	private enum Action {
		NAME("gui.mlog.edit.name", LogicIcons.PENCIL),
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
