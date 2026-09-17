package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.client.gui.GuiGraphics;
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
	private static final int ICON_SHIFT_X = 3, ICON_SHIFT_Y = 0;
	/** 插入位置，来自触发它的那张卡片。 */
	private final int insertAt;
	private final List<Row> rows = new ArrayList<>();
	@SuppressWarnings("NotNullFieldNotInitialized") private LogicEditBox search;
	private double scroll, targetScroll;
	private int contentHeight;
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
		search.setFocused(true);
		search.setResponder(text -> rebuildRows());
		addRenderableWidget(search);
		rebuildRows();
	}
	/** @return 放大镜图标的宽度，搜索框的位置要跟着它走。 */
	private static int searchIconWidth() {
		return LogicIcons.SEARCH.width();
	}
	/** @return 内容区刚好放下三列按钮，两侧各留一个内边距。 */
	@Override
	protected int contentWidth() {
		return PAD * 2 + COLS * ITEM_W;
	}
	/** 按搜索词过滤并按分类分组。 */
	private void rebuildRows() {
		var query = search.getValue().toLowerCase(Locale.ROOT);
		rows.clear();
		contentHeight = 0;
		for (var category : LCategory.values()) {
			var items = new ArrayList<LStatement>();
			for (var supplier : LStatements.ALL) {
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
		scroll = targetScroll = 0;
	}
	private static boolean matches(LStatement example, String query) {
		return example.typeName().contains(query) || LogicFont.text(example.nameKey()).getString().toLowerCase(Locale.ROOT).contains(query);
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		scroll += (targetScroll - scroll) * 0.35;
		if (Math.abs(targetScroll - scroll) < 0.5) scroll = targetScroll;
		// 不能走 super.render：它会把搜索框画在面板之前，被面板盖住
		renderBackground(gui, mouseX, mouseY, partialTick);
		renderPanel(gui);
		// 内容和搜索框同处一个按钮纹理的框里，对齐 Mindustry 的 table.background(Tex.button)
		renderContentFrame(gui, contentTop(), contentBottom() - contentTop());
		renderSearch(gui, mouseX, mouseY, partialTick);
		renderList(gui, mouseX, mouseY);
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	private void renderSearch(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		// 白色：Mindustry 那边是 s.image(Icon.zoom) 没指定颜色，走 defaultImage 的白
		LogicIcons.SEARCH.render(gui, contentLeft() + PAD, LogicIcons.centerY(contentTop() + PAD, SEARCH_H), TEXT);
		search.render(gui, mouseX, mouseY, partialTick);
		var lineX = contentLeft() + PAD + searchIconWidth() + SEARCH_GAP;
		LogicGuiTextures.UNDERLINE.renderTinted(
			gui, lineX, contentTop() + PAD + SEARCH_H, contentRight() - PAD - lineX, LogicGuiTextures.UNDERLINE_H, BORDER
		);
	}
	private void renderList(GuiGraphics gui, int mouseX, int mouseY) {
		var top = listTop();
		var bottom = contentBottom();
		var viewH = bottom - top;
		targetScroll = Math.clamp(targetScroll, 0, Math.max(0, contentHeight - viewH));
		gui.enableScissor(contentLeft() + PAD, top, contentRight() - PAD, bottom);
		var cursor = top - (int) scroll;
		for (var row : rows) {
			if (row.header() != null) renderHeader(gui, row.header(), cursor);
			else for (var i = 0; i < row.items().size(); i++) renderItem(gui, row.items().get(i), itemX(i), cursor, mouseX, mouseY);
			cursor += row.header() != null ? HEADER_H : ITEM_H;
		}
		gui.disableScissor();
	}
	/** @return 第 {@code col} 列按钮的左边。 */
	private int itemX(int col) {
		return contentLeft() + PAD + col * ITEM_W;
	}
	/**
	 * 分类标题：名称加一条拉到右边缘的分隔线。
	 * <p>对齐 Mindustry：这里用 {@code Pal.darkishGray}，不走分类自己的颜色——颜色留给下面的语句按钮。
	 */
	private void renderHeader(GuiGraphics gui, LCategory category, int y) {
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
		var barX = x + LogicFont.width(text) + BAR_GAP;
		LogicGuiTextures.UNDERLINE.renderTinted(
			gui, barX, y + 8, contentRight() - PAD - barX, LogicGuiTextures.UNDERLINE_H, DARKISH
		);
	}
	/**
	 * @return 分类标题前的图标，对齐 Mindustry 的 {@code LCategory.icon}；{@code unknown} 没有图标。
	 * <p>映射放在界面层而不是 {@link LCategory} 里：那个枚举在服务端也会加载，
	 * 引用客户端的 {@link LogicIcons} 会让服务端崩掉。
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
	/**
	 * 语句按钮。
	 * <p>对齐 Mindustry 的 {@code Styles.flatt}：常态纯黑底、悬停铺一层灰；文字用分类色并带描边，
	 * 描边是那边 {@code Fonts.outline} 的替代。
	 */
	private void renderItem(GuiGraphics gui, LStatement statement, int x, int y, int mouseX, int mouseY) {
		var hovered = isOverItem(x, y, mouseX, mouseY);
		if (hovered) LogicCursor.setHand();
		gui.fill(x, y, x + ITEM_W, y + ITEM_H, hovered ? FLAT_OVER : 0xFF000000);
		LogicFont.drawOutlinedCentered(gui, LogicFont.text(statement.nameKey()), x + ITEM_W / 2, y + (ITEM_H - 8) / 2, statement.category().color);
	}
	private static boolean isOverItem(int x, int y, double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + ITEM_W && mouseY >= y && mouseY < y + ITEM_H;
	}
	private int listTop() {
		return contentTop() + PAD + SEARCH_H + PAD;
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		var clicked = rowAt(mouseX, mouseY);
		if (clicked != null) {
			parent.getCanvas().insert(insertAt, clicked);
			onClose();
			return true;
		}
		// 搜索框与底部按钮的命中交给控件自己
		return super.mouseClicked(mouseX, mouseY, button);
	}
	/** @return 命中的语句，没命中则返回 {@code null}。 */
	private @Nullable LStatement rowAt(double mouseX, double mouseY) {
		var top = listTop();
		if (mouseX < contentLeft() + PAD || mouseX >= contentRight() - PAD || mouseY < top || mouseY >= contentBottom()) return null;
		var cursor = top - (int) scroll;
		for (var row : rows) {
			if (row.header() == null) for (var i = 0; i < row.items().size(); i++)
				if (isOverItem(itemX(i), cursor, mouseX, mouseY)) return row.items().get(i);
			cursor += row.header() != null ? HEADER_H : ITEM_H;
		}
		return null;
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		var viewH = contentBottom() - listTop();
		targetScroll = Math.clamp(targetScroll - scrollY * 12, 0, Math.max(0, contentHeight - viewH));
		return true;
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
