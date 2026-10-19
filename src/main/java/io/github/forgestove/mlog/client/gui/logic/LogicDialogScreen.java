package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.component.LogicButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 逻辑编辑器的子对话框：铺满整屏，内容居中并比屏幕窄，两侧留白仍可交互。 */
public abstract class LogicDialogScreen extends ScrollableScreen {
	/** 屏幕四边留白；与父界面 {@code MARGIN} 一致，两处按钮才对齐。 */
	protected static final int MARGIN = 4;
	/** 按钮底纹按 40 行高绘制，减半后边框 2 像素、圆角 6 像素。 */
	protected static final float FRAME_SCALE = 0.5F;
	/** 标题区上边距与标题、横条的间距；9 为 MC 字体行高，横条高度见 {@link LogicGuiTextures#UNDERLINE_H}。 */
	private static final int TITLE_PAD = 2, BAR_GAP = 2;
	/** 横条下面到内容区的间距，上下都留了这么多。 */
	private static final int CONTENT_GAP = 4;
	/** 标题区总高，内容区从它下方开始。 */
	protected static final int TITLE_AREA_H = TITLE_PAD + 30 + BAR_GAP + LogicGuiTextures.UNDERLINE_H + CONTENT_GAP;
	/** 对话框按钮按 210×64 折算；主界面底部按钮对应 160×64，故比它长一截。 */
	private static final int BUTTON_W = 79, BUTTON_H = 24, BUTTON_GAP = 4;
	private static final int CONTENT_BUTTON_GAP = 6;
	/** 底部按钮区总高：与内容区的间距、按钮高度与底部留白之和。 */
	private static final int BUTTON_AREA_H = CONTENT_BUTTON_GAP + BUTTON_H + MARGIN;
	/** 内容区宽度占屏幕宽度的比例，两侧各留出 1/10。 */
	private static final int CONTENT_DIV = 5;
	protected final ProcessorScreen parent;
	/** 关闭时回到的界面；嵌套对话框在构造后覆盖它。 */
	protected Screen returnTo;
	protected LogicDialogScreen(ProcessorScreen parent, Component title) {
		super(title);
		this.parent = parent;
		returnTo = parent;
	}
	protected int contentRight() {
		return contentLeft() + contentWidth();
	}
	/** 按内容宽度居中，子类覆盖 {@link #contentWidth()} 时会随之变化。 */
	protected int contentLeft() {
		return (width - contentWidth()) / 2;
	}
	/** 子类可覆盖它以贴合自身内容。 */
	protected int contentWidth() {
		return width * (CONTENT_DIV - 1) / CONTENT_DIV;
	}
	/** 各子类内容的起始 y。 */
	protected int contentTop() {
		return TITLE_AREA_H;
	}
	protected int contentBottom() {
		return height - BUTTON_AREA_H;
	}
	/** 在内容区铺按钮底框；4 像素边框与 12 像素圆角按 40 行高绘制，不缩会显得过厚。 */
	protected void renderContentFrame(GuiGraphics gui, int y, int height) {
		LogicGuiTextures.BUTTON.render(gui, contentLeft(), y, contentWidth(), height, FRAME_SCALE);
	}
	/** 底框的圆角半径，框里的内容要从内容区再往里缩这么多。 */
	protected int frameInset() {
		return Math.round(LogicGuiTextures.CORNER * FRAME_SCALE);
	}
	/** 行内小面板边框占用的宽度，文字须再向内让出这么多。 */
	protected int panelInset() {
		return Math.max(1, LogicGuiTextures.PANE_SOLID.margin());
	}
	/** 底部按钮行上沿。 */
	protected int buttonTop() {
		return height - MARGIN - BUTTON_H;
	}
	/** 在底部居中排一行按钮，子类在 {@link #init()} 里调用。 */
	protected void addBottomButtons(BottomButton... buttons) {
		var row = LinearLayout.horizontal().spacing(BUTTON_GAP);
		for (var button : buttons) row.addChild(new LogicButton(0, 0, BUTTON_W, BUTTON_H, LogicFont.text(button.key()), button.icon(), button.onPress()));
		row.arrangeElements();
		row.setPosition((width - row.getWidth()) / 2, buttonTop());
		row.visitWidgets(this::addRenderableWidget);
	}
	/** 在绘制内容之前调用，绘制面板、标题与标题下横条（同为强调色）。 */
	protected void renderPanel(GuiGraphics gui) {
		renderParent(gui);
		// 铺的是压暗层而非纯黑，下方的父界面能够透出
		gui.fill(0, 0, width, height, STAGE);
		LogicFont.drawCentered(gui, title, width / 2, TITLE_PAD, ACCENT);
		// 横条跟随屏幕宽度，两侧仅留 MARGIN，不随内容区宽度变化
		LogicGuiTextures.UNDERLINE.renderTinted(
			gui,
			MARGIN,
			TITLE_PAD + 9 + BAR_GAP,
			width - MARGIN * 2,
			LogicGuiTextures.UNDERLINE_H,
			ACCENT
		);
	}
	/** 重绘父界面，压暗层与内容叠在其上；绘在 z=-100 上，否则会被父界面自绘元素遮挡。 */
	protected void renderParent(GuiGraphics gui) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(0, 0, -100);
		var previous = mc.screen;
		mc.screen = parent;
		try {
			LogicTooltip.whileRenderingParent(() -> parent.render(gui, -1, -1, 0F));
		} finally {
			// 父界面渲染若抛异常，mc.screen 会永久停在父界面上
			mc.screen = previous;
		}
		pose.popPose();
	}
	/** 绘制子类的自绘内容与控件，在 {@link #renderPanel} 之后调用。 */
	protected void renderContent(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		for (var renderable : renderables) renderable.render(gui, mouseX, mouseY, partialTick);
	}
	/** 关闭时回到 {@link #returnTo}，不走 {@code Screen} 默认的弹出界面栈。 */
	@Override
	public void onClose() {
		// 每次收起对话框播放 uiBack
		LogicSounds.back();
		mc.setScreen(returnTo);
	}
	/** 对话框默认暂停，只有需要看实时数据的界面才覆盖它。 */
	@Override
	public boolean isPauseScreen() {
		return true;
	}
	/** 同时 resize 父界面：MC 只通知当前界面，不转发会使其停在旧尺寸。 */
	@Override
	public void resize(Minecraft minecraft, int width, int height) {
		super.resize(minecraft, width, height);
		parent.resize(minecraft, width, height);
	}
	protected record BottomButton(String key, LogicIcons icon, LogicButton.OnPress onPress) {}
}
