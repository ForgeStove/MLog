package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.component.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
public class TextInputDialog extends LogicDialogScreen {
	private static final int CONTENT_W = 200;
	/** 输入框比字形高 4 像素，并把下划线高计入自身，点下划线也能命中。 */
	private static final int GAP = 4, LABEL_H = 8, FIELD_H = LogicFont.LINE_H + 4 + LogicGuiTextures.UNDERLINE_H;
	private static final int BUTTON_W = 56, BUTTON_H = 20, BUTTON_GAP = 4, BOTTOM_PAD = 8;
	private static final int BAR_Y = 0;
	private static final int LABEL_Y = BAR_Y + LogicGuiTextures.UNDERLINE_H + GAP;
	private static final int FIELD_Y = LABEL_Y + LABEL_H + GAP;
	private static final int BUTTON_Y = FIELD_Y + FIELD_H + GAP;
	private static final int CONTENT_H = BUTTON_Y + BUTTON_H + BOTTOM_PAD;
	private final Component label, hint;
	private final int maxLength;
	private final String initial;
	private final Consumer<String> onConfirm;
	private @Nullable LogicEditBox box;
	public TextInputDialog(
		ProcessorScreen parent,
		Component title,
		Component label,
		Component hint,
		int maxLength,
		String initial,
		Consumer<String> onConfirm
	) {
		super(parent, title);
		this.label = label;
		this.hint = hint;
		this.maxLength = maxLength;
		this.initial = initial;
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
		LogicFont.drawCentered(gui, label, width / 2, top + LABEL_Y, TEXT);
	}
	@Override
	protected void init() {
		super.init();
		var left = contentLeft();
		var top = contentTop();
		box = new LogicEditBox(left, top + FIELD_Y, CONTENT_W, FIELD_H, hint);
		box.setBordered(false);
		box.setMaxLength(maxLength);
		box.setValue(initial);
		// 主题色传白，光标与选中底按 accent 调制后保持底色灰
		box.setAccent(TEXT);
		box.setUnderlined(true);
		addRenderableWidget(box);
		setInitialFocus(box);
		var by = top + BUTTON_Y;
		addRenderableWidget(new LogicButton(
			width / 2 - BUTTON_W - BUTTON_GAP / 2,
			by,
			BUTTON_W,
			BUTTON_H,
			LogicFont.text("mlog.gui.cancel"),
			null,
			b -> onClose()
		));
		addRenderableWidget(new LogicButton(
			width / 2 + BUTTON_GAP / 2,
			by,
			BUTTON_W,
			BUTTON_H,
			LogicFont.text("mlog.gui.ok"),
			null,
			b -> apply()
		));
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		renderPanel(gui);
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER) {
			apply();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
	private void apply() {
		if (box == null) return;
		onConfirm.accept(box.getValue());
		onClose();
	}
}
