package io.github.forgestove.mlog.client.gui;
import io.github.forgestove.mlog.client.event.LinkMode;
import io.github.forgestove.mlog.client.gui.component.*;
import io.github.forgestove.mlog.client.gui.logic.*;
import io.github.forgestove.mlog.client.gui.logic.LogicCanvas.Host;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Color;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Picker;
import io.github.forgestove.mlog.content.processor.*;
import io.github.forgestove.mlog.core.net.CodeUpdatePayload;
import io.github.forgestove.mlog.logic.LAssembler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

import static io.github.forgestove.mlog.client.gui.LogicColors.DIM;
/** 逻辑处理器界面：语句画布与底部按钮栏；整屏自绘且无物品槽，故不继承 {@code AbstractContainerScreen}，只用 {@link ProcessorMenu} 承载生命周期。 */
public class ProcessorScreen extends Screen implements MenuAccess<ProcessorMenu>, Host {
	/** 底部按钮与留白尺寸；长宽比 240:96（即 2.5:1），实际尺寸按 MC 的 GUI 尺度缩小。 */
	private static final int MARGIN = 4, BUTTON_H = 24, BUTTON_W = 60, BUTTON_GAP = 2;
	private final ProcessorMenu menu;
	private final LogicCanvas canvas = new LogicCanvas(this);
	/** 语句只在首次 init 时从服务端数据装载，重建控件时不重复装载；解析失败时保持空值以便重新导入，同时避免覆盖坏代码。 */
	private boolean loaded;
	/** 上次提交给服务端的代码，无改动时跳过发包。 */
	private String savedCode = "";
	public ProcessorScreen(ProcessorMenu menu, Inventory ignoredInventory, Component title) {
		super(title);
		this.menu = menu;
		LogicSounds.button();
	}
	@Override
	public ProcessorMenu getMenu() {
		return menu;
	}
	private void loadStatements() {
		canvas.setStatements(List.of());
		if (!(menu.getBlockEntity() instanceof AbstractProcessorBlockEntity processor)) return;
		try {
			// 按处理器自身的特权级别解析：非世界处理器中的特权语句转为占位
			canvas.setStatements(LAssembler.read(processor.getCode(), processor.privileged()));
			savedCode = processor.getCode();
		} catch (RuntimeException ignored) {
			// 服务端代码解析失败时留空以便重新导入；savedCode 同样保持空，避免覆盖坏代码
		}
	}
	@Override
	public boolean privileged() {
		return menu.getBlockEntity() instanceof AbstractProcessorBlockEntity be && be.privileged();
	}
	/** 底部按钮栏，整排居中 */
	private void addButtons(int buttonY) {
		var buttons = new BarButton[]{
			new BarButton("mlog.gui.back", LogicIcons.BACK, b -> onClose()),
			new BarButton("mlog.gui.edit", LogicIcons.EDIT, b -> openDialog(new EditMenuDialog(this))),
			new BarButton("mlog.gui.variables", LogicIcons.MENU, b -> openDialog(new VariablesDialog(this))),
			new BarButton("mlog.gui.add", LogicIcons.ADD, b -> openDialog(new AddStatementDialog(this, canvas.cards.size()))),
			new BarButton("mlog.gui.link", LogicIcons.LINK, b -> startLinkMode()),
		};
		var x = (width - (BUTTON_W * buttons.length + BUTTON_GAP * (buttons.length - 1))) / 2;
		for (var button : buttons) {
			addRenderableWidget(new LogicButton(
				x,
				buttonY,
				BUTTON_W,
				BUTTON_H,
				LogicFont.text(button.key()),
				button.icon(),
				button.onPress()
			));
			x += BUTTON_W + BUTTON_GAP;
		}
	}
	@Override
	public void addStatement(int index) {
		openDialog(new AddStatementDialog(this, index));
	}
	@Override
	public void openPicker(Picker picker, Runnable rebuild) {
		openDialog(pickerDialog(picker, rebuild));
	}
	private Screen pickerDialog(Picker picker, Runnable rebuild) {
		if (!(picker instanceof Color color)) return new OptionPopupScreen(this, picker, rebuild);
		return new ColorPickerDialog(this, color.get.get(), value -> {
			color.set.accept(value);
			rebuild.run();
		});
	}
	// 子对话框为独立界面，仅收起输入焦点以避免两处光标同时闪烁
	private void openDialog(Screen dialog) {
		canvas.unfocus();
		if (minecraft != null) minecraft.setScreen(dialog);
	}
	public void save() {
		save(false);
	}
	/** 仅当代码有变化时发送至服务端；{@code force} 为 {@code true} 时即使无改动也重发，供「重新运行」使用。 */
	public void save(boolean force) {
		canvas.unfocus();
		canvas.refresh();
		var code = LAssembler.write(canvas.statements());
		if (!force && code.equals(savedCode)) return;
		savedCode = code;
		PacketDistributor.sendToServer(new CodeUpdatePayload(menu.getPos(), code));
	}
	/** 保存后进入链接模式；须经 onClose 关闭菜单，直接替换 Screen 会使服务端认为界面仍开启。 */
	private void startLinkMode() {
		var pos = menu.getPos();
		onClose();
		LinkMode.start(pos);
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		canvas.sync();
		canvas.update();
		super.render(gui, mouseX, mouseY, partialTick);
		// 拖拽中的卡片需绘制在按钮栏之上
		canvas.renderTopLayer(gui, mouseX, mouseY);
	}
	// AbstractContainerScreen 会代为关闭菜单，改用 Screen 后须自行关闭，否则服务端会一直认为玩家仍开启该界面
	@Override
	public void onClose() {
		save();
		if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
		super.onClose();
	}
	@Override
	protected void init() {
		super.init();
		var buttonY = height - MARGIN - BUTTON_H;
		// 界面无标题栏，内容自顶部开始
		var contentY = MARGIN;
		canvas.setBounds(MARGIN, contentY, width - MARGIN * 2, buttonY - 6 - contentY);
		// 画布本身即控件：渲染、鼠标与键盘均由 MC 分发，界面无需逐个转发
		addRenderableWidget(canvas);
		setFocused(canvas);
		if (!loaded) {
			loadStatements();
			loaded = true;
		}
		addButtons(buttonY);
	}
	@Override
	public void removed() {
		super.removed();
		if (minecraft != null && minecraft.player != null) menu.removed(minecraft.player);
	}
	/** 卡片直接绘制在压暗的游戏画面上；不调用 {@code super} 以免叠加模糊背景，游戏本身仍在渲染故不必自行绘制背景。 */
	@Override
	public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		gui.fill(0, 0, width, height, DIM);
	}
	/** 逻辑编辑器暂停游戏；变量表临时解除暂停，故此处返回固定值。 */
	@Override
	public boolean isPauseScreen() {
		return true;
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		var handled = super.mouseClicked(mouseX, mouseY, button);
		// 点击画布外时收起输入焦点，避免光标持续闪烁
		if (!canvas.isMouseOver(mouseX, mouseY)) {
			canvas.unfocus();
			// 按钮须保持焦点：容器仅向持焦点的控件转发松开事件，清除焦点会使松手触发失效
			if (!handled) setFocused(null);
		}
		return handled;
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		var handled = super.mouseReleased(mouseX, mouseY, button);
		// 转发已完成；清除按钮焦点，否则父界面重绘时按钮会一直呈悬停态
		if (getFocused() instanceof LogicButton) setFocused(null);
		return handled;
	}
	public LogicCanvas getCanvas() {
		return canvas;
	}
	private record BarButton(String key, LogicIcons icon, LogicButton.OnPress onPress) {}
}
