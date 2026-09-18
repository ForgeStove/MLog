package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
/** 语句表：顶部搜索，下面按分类分组列出全部语句，点击插入。 */
@OnlyIn(Dist.CLIENT)
public class AddStatementDialog extends LogicDialogScreen {
	/**
	 * 语句按钮的尺寸与列数。
	 * <p>对齐 Mindustry 的 {@code .size(130f, 50f)} 与每行三个。那边 UI 的基准行高是 40，
	 * 这里是 16，折算比例 0.4，130×50 就成了 52×20。
	 */
	private static final int ITEM_W = 52, ITEM_H = 20, COLS = 3;
	/** 分类标题行的高度。语句按钮之间不留行距，一行就是一个 {@link #ITEM_H}。 */
	private static final int HEADER_H = 16;
	/** 内容区两侧与搜索行的留白，图标到名称、名称到分隔线的间距。 */
	private static final int PAD = 4, SEARCH_H = 14, ICON_GAP = 4, BAR_GAP = 4;
	/** 放大镜和输入框之间的距离。 */
	private static final int SEARCH_GAP = 4;
	/**
	 * 分类图标的缩放系数。
	 * <p>图标字号是字体级的（11 像素），而分类标题行只有 {@link #HEADER_H} 高，原尺寸占了七成；
	 * Mindustry 那边图标是 15 单位对 40 的行高，折过来约 6 像素，所以按 0.55 缩。
	 */
	private static final float ICON_SCALE = 0.55F;
	/**
	 * 图标的微调：字形四周的留白和视觉重心跟文字不一定对得上，靠这两个值对齐。
	 * <p>{@link #ICON_SHIFT_Y} 是往下为正——{@link LogicIcons#centerY} 是按文字行高算的，
	 * 图标字形有自己的重心，通常要往下压一点才和右侧的分类名对齐。
	 */
	private static final int ICON_SHIFT_X = 3, ICON_SHIFT_Y = 3;
	/** 悬停提示：内边距、离鼠标的距离与行高。 */
	private static final int TIP_PAD = 2, TIP_GAP = 8, TIP_LINE_H = 8;
	/** 提示的层级，抬到列表与滚动条之上。 */
	private static final float TIP_Z = 200;
	/** 插入位置，来自触发它的那张卡片。 */
	private final int insertAt;
	private final List<Row> rows = new ArrayList<>();
	/** 滚动量、滑块、拖动、翻页与平滑都由它管，和主界面画布用的是同一套。 */
	private final ScrollBar scrollbar = new ScrollBar();
	/** 本帧悬停的语句按钮，由 {@link #renderItem} 记下，列表画完再统一出提示。 */
	private @Nullable String hoveredTip;
	@SuppressWarnings("NotNullFieldNotInitialized") private LogicEditBox search;
	private int contentHeight;
	/** 内容是否超出一屏，也就是要不要给滚动条留位。 */
	private boolean scrollable;
	public AddStatementDialog(MicroProcessorScreen parent, int insertAt) {
		super(parent, LogicFont.text("gui.mlog.add"));
		this.insertAt = insertAt;
	}
	@Override
	protected void init() {
		super.init();
		fillScreen();
		addBottomButtons(new BottomButton("gui.mlog.back", LogicIcons.BACK, b -> onClose()));
		// 搜索框做成控件，键盘输入交给原版处理
		search = new LogicEditBox(
			contentLeft() + PAD + searchIconWidth() + SEARCH_GAP,
			contentTop() + PAD + 3,
			contentWidth() - PAD * 2 - searchIconWidth() - SEARCH_GAP,
			SEARCH_H,
			LogicFont.text("gui.mlog.search")
		);
		search.setBordered(false);
		search.setResponder(text -> rebuildRows());
		addRenderableWidget(search);
		// 键盘事件走的是 Screen 的焦点，只给控件自己 setFocused 只会画出光标、实际收不到按键
		setFocused(search);
		rebuildRows();
	}
	/** @return 放大镜图标的宽度，搜索框的位置要跟着它走。 */
	private static int searchIconWidth() {
		return LogicIcons.SEARCH.width();
	}
	/** @return 内容区刚好放下三列按钮，两侧各留一个内边距；真要滚动时再给滚动条留一条。 */
	@Override
	protected int contentWidth() {
		return PAD * 2 + COLS * ITEM_W + (scrollable ? ScrollBar.WIDTH : 0);
	}
	/** 按搜索词过滤并按分类分组。 */
	private void rebuildRows() {
		var query = search.getValue().toLowerCase(Locale.ROOT);
		rows.clear();
		contentHeight = 0;
		for (var category : LCategory.values()) {
			var items = new ArrayList<LStatement>();
			for (var supplier : Statements.ALL) {
				var example = supplier.get();
				if (example.category() != category) continue;
				if (!query.isEmpty() && !matches(example, query)) continue;
				items.add(example);
			}
			if (items.isEmpty()) continue;
			rows.add(new Row(category, List.of()));
			contentHeight += HEADER_H;
			for (var i = 0; i < items.size(); i += COLS) {
				rows.add(new Row(null, List.copyOf(items.subList(i, Math.min(i + COLS, items.size())))));
				contentHeight += ITEM_H;
			}
		}
		// 只有真要滚动时才给滚动条留位，不滚动就不留——否则右边平白多出一条空档，和左边对不上。
		// 宽度变了搜索框也得跟着重放，它是按内容区定位的
		scrollable = contentHeight > contentBottom() - listTop();
		search.setX(contentLeft() + PAD + searchIconWidth() + SEARCH_GAP);
		search.setWidth(contentWidth() - PAD * 2 - searchIconWidth() - SEARCH_GAP);
		scrollbar.reset();
	}
	private static boolean matches(LStatement example, String query) {
		return example.typeName().contains(query) || LogicFont.text(example.nameKey()).getString().toLowerCase(Locale.ROOT).contains(query);
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		var viewH = contentBottom() - listTop();
		scrollbar.update(viewH, contentHeight);
		// 不能走 super.render：它会把搜索框画在面板之前，被面板盖住
		renderBackground(gui, mouseX, mouseY, partialTick);
		renderPanel(gui);
		// 内容和搜索框同处一个按钮纹理的框里，对齐 Mindustry 的 table.background(Tex.button)
		renderContentFrame(gui, contentTop(), contentBottom() - contentTop());
		renderSearch(gui, mouseX, mouseY, partialTick);
		hoveredTip = null;
		renderList(gui, mouseX, mouseY);
		scrollbar.render(gui, barX(), listTop(), viewH, contentHeight);
		renderContent(gui, mouseX, mouseY, partialTick);
		// 提示最后画，免得被列表或滚动条盖住
		if (hoveredTip != null) renderTooltip(gui, LogicFont.text(hoveredTip), mouseX, mouseY);
	}
	private int listTop() {
		return contentTop() + PAD + SEARCH_H + PAD;
	}
	private void renderSearch(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		// 白色：Mindustry 那边是 s.image(Icon.zoom) 没指定颜色，走 defaultImage 的白
		LogicIcons.SEARCH.render(gui, contentLeft() + PAD, LogicIcons.centerY(contentTop() + PAD, SEARCH_H), TEXT);
		search.render(gui, mouseX, mouseY, partialTick);
		var lineX = contentLeft() + PAD + searchIconWidth() + SEARCH_GAP;
		LogicGuiTextures.UNDERLINE.renderTinted(
			gui,
			lineX,
			contentTop() + PAD + SEARCH_H,
			contentRight() - PAD - lineX,
			LogicGuiTextures.UNDERLINE_H,
			BORDER
		);
	}
	private void renderList(GuiGraphics gui, int mouseX, int mouseY) {
		var top = listTop();
		var bottom = contentBottom();
		gui.enableScissor(contentLeft() + PAD, top, contentRight() - PAD, bottom);
		var cursor = top - (int) scrollbar.scroll();
		for (var row : rows) {
			if (row.header() != null) renderHeader(gui, row.header(), cursor, mouseX, mouseY);
			else for (var i = 0; i < row.items().size(); i++) renderItem(gui, row.items().get(i), itemX(i), cursor, mouseX, mouseY);
			cursor += row.header() != null ? HEADER_H : ITEM_H;
		}
		gui.disableScissor();
	}
	/** @return 滚动条的左边缘，在按钮列右侧那条留白里。 */
	private int barX() {
		return contentRight() - PAD - ScrollBar.WIDTH;
	}
	/**
	 * 自绘悬停提示，对齐 Mindustry 的 {@code tooltip}：{@code Styles.black6} 底色 + 描边文字。
	 * <p>不走 {@code Screen} 那套提示是因为它的样式改不了，跟界面其余部分对不上。
	 */
	private void renderTooltip(GuiGraphics gui, Component text, int mouseX, int mouseY) {
		// 说明可能有多行，按 \n 拆开逐行画；Mindustry 的语句说明也是手写换行的，不用自动折行
		var lines = text.getString().split("\n", -1);
		var w = 0;
		for (var line : lines) w = Math.max(w, LogicFont.width(LogicFont.rich(line)));
		w += TIP_PAD * 2;
		var h = lines.length * TIP_LINE_H + TIP_PAD * 2;
		// 跟着鼠标走，贴到屏幕外就推回来
		var tx = Math.clamp(mouseX + TIP_GAP, 0, Math.max(0, width - w));
		var ty = Math.clamp(mouseY + TIP_GAP, 0, Math.max(0, height - h));
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(0F, 0F, TIP_Z);
		gui.fill(tx, ty, tx + w, ty + h, CARD_BG);
		for (var i = 0; i < lines.length; i++)
			LogicFont.drawOutlined(gui, LogicFont.rich(lines[i]), tx + TIP_PAD, ty + TIP_PAD + i * TIP_LINE_H, TEXT);
		pose.popPose();
	}
	/**
	 * 分类标题：名称加一条拉到右边缘的分隔线。
	 * <p>对齐 Mindustry：这里用 {@code Pal.darkishGray}，不走分类自己的颜色——颜色留给下面的语句按钮。
	 */
	private void renderHeader(GuiGraphics gui, LCategory category, int y, int mouseX, int mouseY) {
		var text = LogicFont.text(category.nameKey());
		var x = contentLeft() + PAD;
		// 图标在名称前面，和 Mindustry 一样
		var icon = iconOf(category);
		if (icon != null) {
			var iconX = x + ICON_SHIFT_X;
			icon.renderScaled(gui, iconX, LogicIcons.centerY(y, HEADER_H) + ICON_SHIFT_Y, ICON_SCALE, DARKISH);
			x = iconX + icon.width(ICON_SCALE) + ICON_GAP;
		}
		LogicFont.draw(gui, text, x, y + 4, DARKISH);
		// 说明挂在分类名上，和 Mindustry 的 tooltip(category.description()) 一致
		if (mouseX >= x && mouseX < x + LogicFont.width(text) && mouseY >= y && mouseY < y + HEADER_H)
			hoveredTip = category.descriptionKey();
		var barX = x + LogicFont.width(text) + BAR_GAP;
		LogicGuiTextures.UNDERLINE.renderTinted(gui, barX, y + 8, contentRight() - PAD - barX, LogicGuiTextures.UNDERLINE_H, DARKISH);
	}
	/**
	 * 语句按钮。
	 * <p>对齐 Mindustry 的 {@code Styles.flatt}：常态纯黑底、悬停铺一层灰；文字用分类色并带描边，
	 * 描边是那边 {@code Fonts.outline} 的替代。
	 */
	private void renderItem(GuiGraphics gui, LStatement statement, int x, int y, int mouseX, int mouseY) {
		var over = isOverItem(x, y, mouseX, mouseY);
		if (over) {
			LogicCursor.setHand();
			// 没有说明文本的语句不出提示，对齐 Mindustry 的 Core.bundle.has 判断
			if (Language.getInstance().has(statement.tipKey())) hoveredTip = statement.tipKey();
		}
		gui.fill(x, y, x + ITEM_W, y + ITEM_H, over ? FLAT_OVER : 0xFF000000);
		LogicFont.drawOutlinedCentered(
			gui,
			LogicFont.text(statement.nameKey()),
			x + ITEM_W / 2,
			y + (ITEM_H - 8) / 2,
			statement.category().color
		);
	}
	/** @return 第 {@code col} 列按钮的左边。 */
	private int itemX(int col) {
		return contentLeft() + PAD + col * ITEM_W;
	}
	/**
	 * @return 分类标题前的图标，对齐 Mindustry 的 {@code LCategory.icon}；{@code unknown} 没有图标。
	 * 	<p>映射放在界面层而不是 {@link LCategory} 里：那个枚举在服务端也会加载，
	 * 	引用客户端的 {@link LogicIcons} 会让服务端崩掉。
	 */
	private static @Nullable LogicIcons iconOf(LCategory category) {
		return switch (category) {
			case io -> LogicIcons.LOGIC;
			case block -> LogicIcons.EFFECT;
			case operation -> LogicIcons.SETTINGS;
			case control -> LogicIcons.ROTATE;
			default -> null;
		};
	}
	private static boolean isOverItem(int x, int y, double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + ITEM_W && mouseY >= y && mouseY < y + ITEM_H;
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// 先给滚动条：点在它上面不该被当成插入语句
		if (scrollbar.mousePressed(mouseX, mouseY, barX(), listTop(), contentBottom() - listTop(), contentHeight)) return true;
		var clicked = rowAt(mouseX, mouseY);
		if (clicked != null) {
			LogicSounds.button();
			parent.getCanvas().insert(insertAt, clicked);
			onClose();
			return true;
		}
		// 搜索框与底部按钮的命中交给控件自己
		var handled = super.mouseClicked(mouseX, mouseY, button);
		// 点在内容区的空白处（列表空白、搜索行右侧那些）就收起搜索框的焦点，光标不该一直闪
		if (mouseY >= contentTop() && mouseY < contentBottom() && !search.isMouseOver(mouseX, mouseY)) setFocused(null);
		return handled;
	}
	/** @return 命中的语句，没命中则返回 {@code null}。 */
	private @Nullable LStatement rowAt(double mouseX, double mouseY) {
		var top = listTop();
		if (mouseX < contentLeft() + PAD || mouseX >= contentRight() - PAD || mouseY < top || mouseY >= contentBottom()) return null;
		var cursor = top - (int) scrollbar.scroll();
		for (var row : rows) {
			if (row.header() == null) for (var i = 0; i < row.items().size(); i++)
				if (isOverItem(itemX(i), cursor, mouseX, mouseY)) return row.items().get(i);
			cursor += row.header() != null ? HEADER_H : ITEM_H;
		}
		return null;
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// 可视区高要布局完才知道，所以每帧现算
		scrollbar.step((contentBottom() - listTop()) * ScrollBar.WHEEL_RATIO);
		scrollbar.wheel(-scrollY);
		return true;
	}
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		return scrollbar.mouseDragged(mouseY, listTop(), contentBottom() - listTop(), contentHeight);
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		scrollbar.release();
		return super.mouseReleased(mouseX, mouseY, button);
	}
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// 回车插入第一个匹配项
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			for (var row : rows) {
				if (row.header() != null || row.items().isEmpty()) continue;
				parent.getCanvas().insert(insertAt, row.items().getFirst());
				onClose();
				return true;
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
	/** 语句表里的一行：分类标题，或最多 {@link #COLS} 个语句按钮。 */
	private record Row(@Nullable LCategory header, List<LStatement> items) {}
}
