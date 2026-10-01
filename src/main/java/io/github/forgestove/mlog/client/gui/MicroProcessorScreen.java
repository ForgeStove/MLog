package io.github.forgestove.mlog.client.gui;
import io.github.forgestove.mlog.client.event.LinkMode;
import io.github.forgestove.mlog.client.gui.logic.*;
import io.github.forgestove.mlog.content.processor.*;
import io.github.forgestove.mlog.core.net.CodeUpdatePayload;
import io.github.forgestove.mlog.logic.LAssembler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

import static io.github.forgestove.mlog.client.gui.LogicColors.DIM;
/**
 * 逻辑处理器界面：图形化语句画布与底部按钮栏。
 * <p>界面为整屏自绘且无物品槽，故不继承 {@code AbstractContainerScreen}：
 * 其居中偏移、槽位循环与背包标签均须绕开。
 * <p>仍保留菜单（{@link MicroProcessorMenu}）：查看者判定与关闭时机等生命周期挂载于菜单之上，
 * 距离校验、方块破坏、玩家死亡与切换维度均由菜单自动处理。
 */
@OnlyIn(Dist.CLIENT)
public class MicroProcessorScreen extends Screen implements MenuAccess<MicroProcessorMenu> {
	/** 按钮长宽比 240:96（即 2.5:1）；实际尺寸按 MC 的 GUI 尺度缩小。 */
	private static final int MARGIN = 4, BUTTON_H = 24, BUTTON_W = 60, BUTTON_GAP = 2;
	private final MicroProcessorMenu menu;
	private final LogicCanvas canvas = new LogicCanvas();
	/** 语句只在首次 init 时从服务端数据装载，窗口尺寸变化重建控件时不重复装载。 */
	private boolean loaded;
	/** 上次提交给服务端的代码，用于判断是否有改动，避免无变化时重复发包。 */
	private String savedCode = "";
	public MicroProcessorScreen(MicroProcessorMenu menu, Inventory ignoredInventory, Component title) {
		super(title);
		this.menu = menu;
		// 编辑器打开音效
		LogicSounds.button();
	}
	@Override
	public MicroProcessorMenu getMenu() {
		return menu;
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
		// 卡片头部的「+」与底部「添加」逻辑相同，仅插入位置不同
		canvas.setAddRequest(index -> openDialog(new AddStatementDialog(this, index)));
		// 参数控件的选项列表同为独立界面
		canvas.setOptionRequest((select, onSelect) -> openDialog(new OptionPopupScreen(this, select, onSelect)));
		canvas.setColorRequest(
			(color, onPick) -> openDialog(new ColorPickerDialog(this, color.get.get(), value -> {
				color.set.accept(value);
				onPick.run();
			}))
		);
		if (!loaded) {
			loadStatements();
			loaded = true;
		}
		addButtons(buttonY);
	}
	/** 从方块实体同步下来的代码解析出语句列表。 */
	private void loadStatements() {
		canvas.setStatements(List.of());
		if (!(menu.getBlockEntity() instanceof AbstractProcessorBlockEntity processor)) return;
		try {
			// 按处理器自身的特权级别解析：非世界处理器中的特权语句转为占位，与服务端编译结果一致
			canvas.setStatements(LAssembler.read(processor.getCode(), processor.privileged()));
			savedCode = processor.getCode();
		} catch (RuntimeException ignored) {
			// 服务端代码解析失败时留空以便重新导入；savedCode 同样保持空，避免覆盖坏代码
		}
	}
	/** @return 当前处理器是否为世界处理器（带特权）；语句表据此过滤特权语句。 */
	public boolean privileged() {
		return menu.getBlockEntity() instanceof AbstractProcessorBlockEntity be && be.privileged();
	}
	/**
	 * 底部按钮栏，整排居中。
	 * <p>依次为返回、编辑、变量、添加，无「保存」按钮，返回时提交。
	 * <p>另有「链接」，因链接方块需要额外的选取操作。
	 */
	private void addButtons(int buttonY) {
		var buttons = new BarButton[]{
			new BarButton("gui.mlog.back", LogicIcons.BACK, b -> onClose()),
			new BarButton("gui.mlog.edit", LogicIcons.EDITOR, b -> openDialog(new EditMenuDialog(this))),
			new BarButton("gui.mlog.variables", LogicIcons.MENU, b -> openDialog(new VariablesDialog(this))),
			new BarButton("gui.mlog.add", LogicIcons.ADD, b -> openDialog(new AddStatementDialog(this, canvas.cards.size()))),
			new BarButton("gui.mlog.link", LogicIcons.LINK, b -> startLinkMode()),
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
	/** 子对话框为独立界面，此处仅收起输入焦点，避免两处同时闪烁光标。 */
	private void openDialog(Screen dialog) {
		canvas.unfocus();
		if (minecraft != null) minecraft.setScreen(dialog);
	}
	/** 代码有变化时才发送至服务端。 */
	public void save() {
		save(false);
	}
	/** @param force 为 {@code true} 时即使没改动也重发，供「重新运行」使用。 */
	public void save(boolean force) {
		canvas.unfocus();
		canvas.refresh();
		var code = LAssembler.write(canvas.statements());
		if (!force && code.equals(savedCode)) return;
		savedCode = code;
		PacketDistributor.sendToServer(new CodeUpdatePayload(menu.getPos(), code));
	}
	/** 保存后进入链接模式。须经 onClose 关闭菜单，直接替换 Screen 会使服务端认为界面仍开启。 */
	private void startLinkMode() {
		var pos = menu.getPos();
		onClose();
		LinkMode.start(pos);
	}
	@Override
	public void onClose() {
		save();
		// AbstractContainerScreen 会代为关闭菜单，改用 Screen 后须自行关闭，
		// 否则服务端会一直认为玩家仍开启该界面
		if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
		super.onClose();
	}
	/** 界面被强制替换（而非正常关闭）时亦会执行，与原版容器界面一致。 */
	@Override
	public void removed() {
		super.removed();
		if (minecraft != null && minecraft.player != null) menu.removed(minecraft.player);
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		canvas.sync();
		canvas.update();
		super.render(gui, mouseX, mouseY, partialTick);
		// 拖拽中的卡片需绘制在按钮栏之上
		canvas.renderTopLayer(gui, mouseX, mouseY);
	}
	/** 无面板，卡片直接绘制在压暗的游戏画面上；不调用 {@code super} 以免叠加模糊背景。 */
	@Override
	public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		gui.fill(0, 0, width, height, DIM);
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		var handled = super.mouseClicked(mouseX, mouseY, button);
		// 点击画布外（按钮或空白）时收起输入焦点，避免卡片输入框光标持续闪烁
		if (!canvas.isMouseOver(mouseX, mouseY)) {
			canvas.unfocus();
			setFocused(null);
		}
		return handled;
	}
	public LogicCanvas getCanvas() {
		return canvas;
	}
	/** 逻辑编辑器暂停游戏；变量表临时解除暂停。 */
	@Override
	public boolean isPauseScreen() {
		return true;
	}
	/** 按钮栏的一项。 */
	private record BarButton(String key, LogicIcons icon, LogicButton.OnPress onPress) {}
}
