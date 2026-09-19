package io.github.forgestove.mlog.client.gui;
import io.github.forgestove.mlog.client.event.LinkMode;
import io.github.forgestove.mlog.client.gui.logic.*;
import io.github.forgestove.mlog.content.microprocessor.*;
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
 * 逻辑处理器界面：图形化的语句画布 + 底部按钮栏。
 * <p>界面是整屏自绘的，没有任何物品槽，所以不继承 {@code AbstractContainerScreen}——
 * 它的居中偏移、槽位循环与背包标签全都是要绕开的东西。
 * <p>但仍然保留菜单（{@link MicroProcessorMenu}），因为「谁在看这个方块、何时该关」这套
 * 生命周期是挂载菜单上的：距离校验、方块破坏、玩家死亡或切维度都会由它自动处理。
 */
@OnlyIn(Dist.CLIENT)
public class MicroProcessorScreen extends Screen implements MenuAccess<MicroProcessorMenu> {
	/** 按钮长宽比对齐 Mindustry 的 240:96（即 2.5:1）。尺寸本身按 MC 的 GUI 尺度缩小。 */
	private static final int MARGIN = 4, BUTTON_H = 24, BUTTON_W = 60, BUTTON_GAP = 2;
	private final MicroProcessorMenu menu;
	private final LogicCanvas canvas = new LogicCanvas();
	/** 语句只在首次 init 时从服务端数据装载，窗口尺寸变化重建控件时不重复装载。 */
	private boolean loaded;
	/** 上次提交给服务端的代码，用来判断有没有改动，避免无变化时重复发包。 */
	private String savedCode = "";
	public MicroProcessorScreen(MicroProcessorMenu menu, Inventory ignoredInventory, Component title) {
		super(title);
		this.menu = menu;
	}
	@Override
	public MicroProcessorMenu getMenu() {
		return menu;
	}
	@Override
	protected void init() {
		super.init();
		var buttonY = height - MARGIN - BUTTON_H;
		// 对齐 Mindustry 的 LogicDialog：它 clearChildren 掉了标题栏，所以顶部没有标题
		var contentY = MARGIN;
		canvas.setBounds(MARGIN, contentY, width - MARGIN * 2, buttonY - 6 - contentY);
		// 画布自己就是控件：渲染、鼠标与键盘都由 MC 分发，界面不必再逐个转发
		addRenderableWidget(canvas);
		setFocused(canvas);
		// 卡片头部的「+」和底部「添加」走同一条路，只是插入位置不同
		canvas.setAddRequest(index -> openDialog(new AddStatementDialog(this, index)));
		// 参数控件的选项列表同样是独立界面
		canvas.setOptionRequest((select, onSelect) -> openDialog(new OptionPopupScreen(this, select, onSelect)));
		if (!loaded) {
			loadStatements();
			loaded = true;
		}
		addButtons(buttonY);
	}
	/** 从方块实体同步下来的代码解析出语句列表。 */
	private void loadStatements() {
		canvas.setStatements(List.of());
		if (!(menu.getBlockEntity() instanceof MicroProcessorBlockEntity processor)) return;
		try {
			// 按处理器自己的特权级别解析：非世界处理器里的特权语句会变成占位，和那边编译的结果一致
			canvas.setStatements(LAssembler.read(processor.getCode(), processor.privileged()));
			savedCode = processor.getCode();
		} catch (RuntimeException ignored) {
			// 服务端代码解析失败时留空，玩家可以重新导入；savedCode 也保持空，免得把坏代码覆盖掉
		}
	}
	/** @return 当前处理器是不是世界处理器（带特权）。语句表按它过滤特权语句。 */
	public boolean privileged() {
		return menu.getBlockEntity() instanceof MicroProcessorBlockEntity be && be.privileged();
	}
	/**
	 * 底部按钮栏，整排居中。
	 * <p>顺序对齐 Mindustry：返回 / 编辑 / 变量 / 添加，它没有「保存」按钮——点返回时提交。
	 * <p>我们比它多一个「链接」，因为 MC 里链接方块需要额外的选取操作。
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
	/** 子对话框是独立界面，这里只负责收起输入焦点，避免两处同时闪现光标。 */
	private void openDialog(Screen dialog) {
		canvas.unfocus();
		if (minecraft != null) minecraft.setScreen(dialog);
	}
	/** 代码有变化才发给服务端，对齐 Mindustry 关闭时提交、没改就不发的行为。 */
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
	/** 保存后进链接模式。必须走 onClose 关掉菜单，直接换 Screen 会让服务端以为界面还开着。 */
	private void startLinkMode() {
		var pos = menu.getPos();
		onClose();
		LinkMode.start(pos);
	}
	@Override
	public void onClose() {
		save();
		// AbstractContainerScreen 会替我们关掉菜单，换成 Screen 后必须自己来，
		// 否则服务端会一直认为玩家还开着这个界面
		if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
		super.onClose();
	}
	/** 界面被强制换掉（而非正常关闭）时也会走到这里，与原版容器界面保持一致。 */
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
		// 拖拽中的卡片要压在按钮栏之上
		canvas.renderTopLayer(gui, mouseX, mouseY);
	}
	/** 对齐 Mindustry：没有面板，卡片直接浮在压暗的游戏画面上。不调 {@code super} 以免叠上模糊背景。 */
	@Override
	public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		gui.fill(0, 0, width, height, DIM);
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		var handled = super.mouseClicked(mouseX, mouseY, button);
		// 点到画布外（按钮或空白）就收起输入焦点，卡片上输入框的光标才不会一直闪
		if (!canvas.isMouseOver(mouseX, mouseY)) {
			canvas.unfocus();
			setFocused(null);
		}
		return handled;
	}
	public LogicCanvas getCanvas() {
		return canvas;
	}
	/** 对齐 Mindustry：逻辑编辑器暂停游戏，玩家不用一边编辑一边躲怪。变量表会临时解除暂停。 */
	@Override
	public boolean isPauseScreen() {
		return true;
	}
	/** 按钮栏的一项。 */
	private record BarButton(String key, LogicIcons icon, LogicButton.OnPress onPress) {}
}
