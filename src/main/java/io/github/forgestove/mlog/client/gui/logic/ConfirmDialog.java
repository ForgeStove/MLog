package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.component.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
/** 确认对话框：顶部标题、中间正文与底部取消/确定。 */
public class ConfirmDialog extends LogicDialogScreen {
	private static final int CONTENT_W = 200;
	/** 标题压在横条上，正文与按钮沿用命名对话框的间距。 */
	private static final int GAP = 4, BODY_H = LogicFont.LINE_H;
	/** 取消/确定按 200×54 折算，行高 40→16 的比例为 0.375。 */
	private static final int BUTTON_W = 75, BUTTON_H = 20, BUTTON_GAP = 4, BOTTOM_PAD = 8;
	private static final int LABEL_Y = 0;
	private static final int BAR_Y = LABEL_Y + LogicFont.LINE_H;
	private static final int BODY_Y = BAR_Y + LogicGuiTextures.UNDERLINE_H + GAP;
	private static final int BUTTON_Y = BODY_Y + BODY_H + GAP * 2;
	private static final int CONTENT_H = BUTTON_Y + BUTTON_H + BOTTOM_PAD;
	private final Component label, body;
	private final Runnable onConfirm;
	public ConfirmDialog(ProcessorScreen parent, Component label, Component body, Runnable onConfirm) {
		super(parent, label);
		this.label = label;
		this.body = body;
		this.onConfirm = onConfirm;
	}
	@Override
	protected int contentWidth() {
		return CONTENT_W;
	}
	@Override
	protected int contentTop() {
		return (height - CONTENT_H) / 2;
	}
	@Override
	protected void renderPanel(GuiGraphics gui) {
		renderParent(gui);
		gui.fill(0, 0, width, height, STAGE);
		var left = contentLeft();
		var top = contentTop();
		LogicGuiTextures.UNDERLINE.renderTinted(gui, left, top + BAR_Y, CONTENT_W, LogicGuiTextures.UNDERLINE_H, ACCENT);
		LogicFont.drawCentered(gui, label, width / 2, top + LABEL_Y, ACCENT);
	}
	@Override
	protected void init() {
		super.init();
		var by = contentTop() + BUTTON_Y;
		addRenderableWidget(new LogicButton(
			width / 2 - BUTTON_W - BUTTON_GAP / 2,
			by,
			BUTTON_W,
			BUTTON_H,
			LogicFont.text("mlog.gui.cancel"),
			LogicIcons.CANCEL,
			b -> onClose()
		));
		addRenderableWidget(new LogicButton(
			width / 2 + BUTTON_GAP / 2,
			by,
			BUTTON_W,
			BUTTON_H,
			LogicFont.text("mlog.gui.ok"),
			LogicIcons.OK,
			b -> confirm()
		));
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		renderPanel(gui);
		LogicFont.drawCentered(gui, body, width / 2, contentTop() + BODY_Y, TEXT);
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER) {
			confirm();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
	private void confirm() {
		onConfirm.run();
		onClose();
	}
}
