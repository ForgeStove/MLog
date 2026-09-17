package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 逻辑编辑器弹出的子对话框。
 * <p>三段式布局，参照 CCG 的 {@code ConfigScreen}：铺满整屏，顶部标题、中间内容、底部按钮。
 * 内容比屏幕窄并居中（同 {@code ConfigEntryList.getRowWidth()}），两侧留白仍然可以交互。
 * <p>做成独立界面而不是画在父界面之上：渲染层级与事件隔离都由 MC 保证，
 * 父界面不必为它重写任何事件方法。关闭时直接换回父界面。
 */
@OnlyIn(Dist.CLIENT)
public abstract class LogicDialogScreen extends Screen {
	/** 屏幕四边的留白，内容与按钮都从这里算起。取父界面的 {@code MARGIN}，两处的按钮才对得上。 */
	protected static final int MARGIN = 4;
	/** 标题区：上边距、标题与横条的间距。其中 9 是 MC 字体的行高；横条本身多粗见 {@link LogicGuiTextures#UNDERLINE_H}。 */
	private static final int TITLE_PAD = 2, BAR_GAP = 2;
	/** 横条下面到内容区的间距。Mindustry 那边的 titleImage 是 {@code pad(4f)}，上下都留了这么多。 */
	private static final int CONTENT_GAP = 4;
	/** 标题区总高，内容区从它下方开始。 */
	protected static final int TITLE_AREA_H = TITLE_PAD + 30 + BAR_GAP + LogicGuiTextures.UNDERLINE_H + CONTENT_GAP;
	/** 底部按钮的尺寸与间距。 */
	private static final int BUTTON_W = 60, BUTTON_H = 24, BUTTON_GAP = 4;
	/** 内容区底部到按钮行顶部的间距。 */
	private static final int CONTENT_BUTTON_GAP = 6;
	/** 底部按钮区总高：与内容区的间距 + 按钮 + 底部留白。 */
	private static final int BUTTON_AREA_H = CONTENT_BUTTON_GAP + BUTTON_H + MARGIN;
	/** 内容区宽度占屏幕宽度的比例，两侧各留出 1/10。 */
	private static final int CONTENT_DIV = 5;
	/**
	 * 内容底框纹理的缩放系数。
	 * <p>按钮底纹取自 Mindustry 原图，边框与圆角是按那边 40 的行高画的；这里按一半缩着画，
	 * 边框就成了 2 像素、圆角 6 像素，配这个尺寸的内容区才不显厚。
	 */
	protected static final float FRAME_SCALE = 0.5F;
	protected final MicroProcessorScreen parent;
	/** 关闭时回到的界面。嵌套对话框（如变量表开内置变量）在构造后覆盖它。 */
	protected Screen returnTo;
	/** 面板矩形，等于整屏。 */
	protected int panelX, panelY, panelW, panelH;
	protected LogicDialogScreen(MicroProcessorScreen parent, Component title) {
		super(title);
		this.parent = parent;
		returnTo = parent;
	}
	/** 对话框铺满整屏，子类在 {@link #init()} 里调用。 */
	protected void fillScreen() {
		panelX = 0;
		panelY = 0;
		panelW = width;
		panelH = height;
	}
	/** @return 内容区右边。 */
	protected int contentRight() {
		return contentLeft() + contentWidth();
	}
	/** @return 内容区左边。按内容宽度居中，子类覆盖 {@link #contentWidth()} 时会跟着走。 */
	protected int contentLeft() {
		return (width - contentWidth()) / 2;
	}
	/**
	 * @return 内容区宽度，对齐 CCG 的 {@code ConfigEntryList.getRowWidth()}。
	 * 	<p>子类可以覆盖它来贴合自己的内容，比如语句表只要刚好放下三列按钮。
	 */
	protected int contentWidth() {
		return width * (CONTENT_DIV - 1) / CONTENT_DIV;
	}
	/** @return 内容区顶部，各子类的内容都从这里往下排。 */
	protected int contentTop() {
		return TITLE_AREA_H;
	}
	/** @return 内容区底部，在底部按钮行之上。 */
	protected int contentBottom() {
		return height - BUTTON_AREA_H;
	}
	/**
	 * 在内容区铺一层按钮纹理的底框，对齐 Mindustry 的 {@code table(Tex.button)}。
	 * <p>纹理按一半缩着画：原图的 4 像素边框和 12 像素圆角是按那边 40 的行高画的，在这里显得很厚。
	 */
	protected void renderContentFrame(GuiGraphics gui, int y, int height) {
		LogicGuiTextures.BUTTON.render(gui, contentLeft(), y, contentWidth(), height, FRAME_SCALE);
	}
	/** @return 底框的圆角半径，框里的内容要从内容区再往里缩这么多。 */
	protected int frameInset() {
		return Math.round(LogicGuiTextures.CORNER * FRAME_SCALE);
	}
	/** @return 行内小面板（值、说明）铺完后边框占掉的宽度，里面的文字要再往里让这么多。 */
	protected int panelInset() {
		return Math.max(1, LogicGuiTextures.PANE_SOLID.margin());
	}
	/** 在底部居中排一行按钮，子类在 {@link #init()} 里调用。 */
	protected void addBottomButtons(BottomButton... buttons) {
		var y = height - MARGIN - BUTTON_H;
		var x = (width - (BUTTON_W * buttons.length + BUTTON_GAP * (buttons.length - 1))) / 2;
		for (var button : buttons) {
			addRenderableWidget(new LogicButton(x, y, BUTTON_W, BUTTON_H, LogicFont.text(button.key()), button.icon(), button.onPress()));
			x += BUTTON_W + BUTTON_GAP;
		}
	}
	/**
	 * 画对话框面板、标题与标题下的横条，应在绘制内容之前调用。
	 * <p>对齐 Mindustry 的 {@code BaseDialog}：标题居中在上、颜色为 {@code Pal.accent}，
	 * 下面一条同色横条，对应它的 {@code titleImage}。
	 */
	protected void renderPanel(GuiGraphics gui) {
		renderParent(gui);
		// 铺的是压暗层而不是死黑：对齐 Mindustry 的 stageBackground，下面的父界面能透出来
		gui.fill(panelX, panelY, panelX + panelW, panelY + panelH, STAGE);
		LogicFont.drawCentered(gui, title, width / 2, TITLE_PAD, ACCENT);
		// 横条跟着屏幕走，两侧只留 MARGIN——对齐 Mindustry 的 titleImage，
		// 它挂在整条 titleTable 上 growX，不随内容区宽度变
		LogicGuiTextures.UNDERLINE.renderTinted(
			gui,
			MARGIN,
			TITLE_PAD + 9 + BAR_GAP,
			width - MARGIN * 2,
			LogicGuiTextures.UNDERLINE_H,
			ACCENT
		);
	}
	/**
	 * 把父界面按当前状态重画一遍，后面的压暗层与内容都叠在它上面。
	 * <p>对话框是独立界面，MC 不会替它画下面那一层；不补这一步就只能看到压暗后的世界，
	 * 编辑器里的语句全看不见了。
	 * <p>画在往屏幕外推 100 的 z 上，是因为父界面里有自绘元素（连线、滚动条）带自己的层级，
	 * 不推到后面会被它们盖住。
	 */
	private void renderParent(GuiGraphics gui) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(0, 0, -100);
		var previous = mc.screen;
		mc.screen = parent;
		try {
			parent.render(gui, -1, -1, 0F);
		} finally {
			// 父界面渲染若抛异常，mc.screen 会永久停在父界面上，那是很难排查的状态
			mc.screen = previous;
		}
		pose.popPose();
	}
	/** 画子类的自绘内容与控件，在 {@link #renderPanel} 之后调用。 */
	protected void renderContent(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		for (var renderable : renderables) renderable.render(gui, mouseX, mouseY, partialTick);
	}
	/**
	 * 窗口尺寸变化时顺带把父界面也 resize 一遍。
	 * <p>对话框是独立界面，打开时父界面被换下，MC 只会通知当前界面；
	 * 不转发的话父界面的宽高与控件会一直停在旧尺寸，背景重画出来就是错位的。
	 */
	@Override
	public void resize(Minecraft minecraft, int width, int height) {
		super.resize(minecraft, width, height);
		parent.resize(minecraft, width, height);
	}
	/** 关闭对话框回到 {@link #returnTo}，而不是走 {@code Screen} 默认的弹出界面栈。 */
	@Override
	public void onClose() {
		mc.setScreen(returnTo);
	}
	/** 对话框默认也暂停，与编辑器一致。只有需要看实时数据的界面才覆盖它。 */
	@Override
	public boolean isPauseScreen() {
		return true;
	}
	/** 底部按钮栏的一项。 */
	protected record BottomButton(String key, LogicIcons icon, LogicButton.OnPress onPress) {}
}
