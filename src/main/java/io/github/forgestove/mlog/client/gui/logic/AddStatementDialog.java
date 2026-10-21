package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.component.*;
import io.github.forgestove.mlog.client.gui.logic.ClippedCellGrid.*;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.Statements.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
/** 语句表：顶部搜索，下面按分类分组列出全部语句，点击插入。 */
public class AddStatementDialog extends LogicDialogScreen {
	/** 按钮尺寸与列数：按行高 40→16 的 0.4 比例折算，每行三个。 */
	private static final int ITEM_W = 52, ITEM_H = 20, COLS = 3;
	/** 语句按钮之间不留行距，一行就是一个 {@link #ITEM_H}。 */
	private static final int HEADER_H = 16;
	/** 搜索框的最小宽度。 */
	private static final int SEARCH_MIN_W = 60;
	/** 分类行右端的内缩；5 单位按 0.4 折算。 */
	private static final int HEADER_PAD = 2;
	/** 内容区留白与图标、分隔线的间距。 */
	private static final int PAD = 4, ICON_GAP = 4, BAR_GAP = 4;
	/** 图标 15 单位按行高 40→16 的 0.4 比例折为约 6 像素，故取 {@code 0.55}。 */
	private static final float ICON_SCALE = 0.55F;
	/** 字形留白与文字行高不对齐，靠这两个值校正；{@link #ICON_SHIFT_Y} 往下为正。 */
	private static final int ICON_SHIFT_X = 3, ICON_SHIFT_Y = 3;
	private final int insertAt;
	private final List<EntryRow> rows = new ArrayList<>();
	/** 语句格控件，与 {@link #rows} 中的条目一一对应。 */
	private final List<LogicCellButton> cells = new ArrayList<>();
	private final ClippedCellGrid grid = new ClippedCellGrid(scrollbar);
	/** 由 {@link #renderItem} 记下，列表画完再统一出提示。 */
	private @Nullable String hoveredTip;
	@SuppressWarnings("NotNullFieldNotInitialized") private LogicSearchBox search;
	/** 底框在内容区内垂直居中，列表短时随内容收缩。 */
	private int frameTop, frameHeight;
	public AddStatementDialog(ProcessorScreen parent, int insertAt) {
		super(parent, LogicFont.text("mlog.gui.add"));
		this.insertAt = insertAt;
	}
	private static boolean matches(Entry entry, String query) {
		return entry.id().contains(query) || LogicFont.text(entry.nameKey()).getString().toLowerCase(Locale.ROOT).contains(query);
	}
	/** 分类标题图标，{@code unknown} 无；映射置于界面层，{@link LCategory} 在服务端引用客户端 {@link LogicIcons} 会崩。 */
	private static @Nullable LogicIcons iconOf(LCategory category) {
		return switch (category) {
			case io -> LogicIcons.LOGIC;
			case block -> LogicIcons.EFFECT;
			case operation -> LogicIcons.SETTINGS;
			case control -> LogicIcons.ROTATE;
			case unit -> LogicIcons.UNITS;
			case world -> LogicIcons.TERRAIN;
			default -> null;
		};
	}
	/** 按实际用到的列数取宽，不足时由搜索框托底。 */
	@Override
	protected int contentWidth() {
		return PAD * 2 + viewWidth() + (contentHeight() > listHeight() ? ScrollBar.WIDTH : 0);
	}
	private int viewWidth() {
		return Math.max(columns() * ITEM_W, SEARCH_MIN_W);
	}
	/** 各行实际用到的最大列数。 */
	private int columns() {
		var columns = 0;
		for (var row : rows) if (row.header() == null) columns = Math.max(columns, row.items().size());
		return columns;
	}
	/** 分类标题与语句行的总高。 */
	private int contentHeight() {
		var height = 0;
		for (var row : rows) height += row.header() == null ? ITEM_H : HEADER_H;
		return height;
	}
	private void rebuildRows() {
		var query = search.getValue().toLowerCase(Locale.ROOT);
		rows.clear();
		for (var category : LCategory.values()) {
			var items = new ArrayList<Entry>();
			for (var entry : Statements.ALL) {
				if (entry.category() != category) continue;
				// 仅能手写的语句不列入语句表
				if (entry.hidden()) continue;
				// 特权语句仅限世界处理器
				if (entry.privileged() && !parent.privileged()) continue;
				if (!query.isEmpty() && !matches(entry, query)) continue;
				items.add(entry);
			}
			if (items.isEmpty()) continue;
			rows.add(new EntryRow(category, List.of()));
			for (var i = 0; i < items.size(); i += COLS) {
				var itemRow = items.subList(i, Math.min(i + COLS, items.size()));
				rows.add(new EntryRow(null, List.copyOf(itemRow)));
			}
		}
		var rowsHeight = contentHeight();
		var available = contentBottom() - contentTop();
		frameHeight = Math.min(PAD + LogicSearchBox.HEIGHT + PAD + rowsHeight + frameInset(), available);
		frameTop = contentTop() + (available - frameHeight) / 2;
		// 宽度变化后搜索框按内容区重新定位
		search.layout(contentLeft() + PAD, frameTop + PAD, contentRight() - PAD);
		grid.area(contentLeft() + PAD, listTop(), viewWidth(), listHeight(), barX(), ITEM_W, rowsHeight);
		scrollbar.reset();
		rebuildCells();
	}
	/** 按当前过滤结果重建语句格控件，命中范围限制在列表可视区。 */
	private void rebuildCells() {
		for (var cell : cells) removeWidget(cell);
		cells.clear();
		var gridRows = new ArrayList<Row>();
		for (var row : rows) {
			var header = row.header();
			if (header != null) {
				gridRows.add(new HeaderRow(HEADER_H, (gui, x, y, mouseX, mouseY) -> renderHeader(gui, header, y, mouseX, mouseY)));
				continue;
			}
			var rowCells = new ArrayList<LogicCellButton>();
			for (var entry : row.items()) {
				var cell = new LogicCellButton(
					ITEM_W,
					ITEM_H,
					(self, gui, mouseX, mouseY, hovered, pressed) -> renderItem(self, gui, hovered, entry)
				);
				cell.setAction(() -> insert(entry));
				addWidget(cell);
				cells.add(cell);
				rowCells.add(cell);
			}
			gridRows.add(new CellRow(ITEM_H, rowCells));
		}
		grid.rows(gridRows);
	}
	private void insert(Entry entry) {
		LogicSounds.button();
		parent.getCanvas().insert(insertAt, entry.factory().get());
		onClose();
	}
	private int listTop() {
		return frameTop + PAD + LogicSearchBox.HEIGHT + PAD;
	}
	/** 从底框边框往内收，否则末行会盖住框线与圆角。 */
	private int listBottom() {
		return frameTop + frameHeight - frameInset();
	}
	private int listHeight() {
		return listBottom() - listTop();
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		scrollbar.update();
		// 不得走 super.render：控件会画在面板之前而被盖住
		renderBackground(gui, mouseX, mouseY, partialTick);
		renderPanel(gui);
		// 内容和搜索框同处一个按钮纹理的框里
		renderContentFrame(gui, frameTop, frameHeight);
		hoveredTip = null;
		grid.render(gui, mouseX, mouseY, partialTick);
		scrollbar.render(gui, mouseX, mouseY, partialTick);
		renderContent(gui, mouseX, mouseY, partialTick);
		// 提示最后画，避免被列表或滚动条盖住
		LogicTooltip.render(gui, hoveredTip == null ? null : LogicFont.text(hoveredTip), mouseX, mouseY, width, height);
	}
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// 回车插入第一个匹配项
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			for (var row : rows) {
				if (row.header() != null || row.items().isEmpty()) continue;
				insert(row.items().getFirst());
				return true;
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
	@Override
	protected void init() {
		super.init();
		addBottomButtons(new BottomButton("mlog.gui.back", LogicIcons.BACK, b -> onClose()));
		// 搜索框作为控件，键盘输入交给原版处理
		search = new LogicSearchBox();
		search.setResponder(text -> rebuildRows());
		addRenderableWidget(search);
		// 键盘事件依赖 Screen 焦点，仅控件自身 setFocused 只画出光标、收不到按键
		setFocused(search);
		rebuildRows();
	}
	/** 按钮列右侧的留白处。 */
	private int barX() {
		return contentRight() - PAD - ScrollBar.WIDTH;
	}
	/** 名称加一条拉到右边缘的分隔线；用暗灰色，分类色留给语句按钮。 */
	private void renderHeader(GuiGraphics gui, LCategory category, int y, int mouseX, int mouseY) {
		var text = LogicFont.text(category.nameKey());
		var x = contentLeft() + PAD;
		var icon = iconOf(category);
		if (icon != null) {
			var iconX = x + ICON_SHIFT_X;
			icon.renderScaled(gui, iconX, LogicIcons.centerY(y, HEADER_H) + ICON_SHIFT_Y, ICON_SCALE, DARKISH);
			x = iconX + icon.width(ICON_SCALE) + ICON_GAP;
		}
		LogicFont.draw(gui, text, x, y + 4, DARKISH);
		// 说明挂在分类名上
		if (mouseX >= x && mouseX < x + LogicFont.width(text) && mouseY >= y && mouseY < y + HEADER_H)
			hoveredTip = category.descriptionKey();
		var barX = x + LogicFont.width(text) + BAR_GAP;
		// 止于列表右沿而非底框边缘，滚动条所在的一列不算在内
		var right = contentLeft() + PAD + viewWidth() - HEADER_PAD;
		LogicGuiTextures.UNDERLINE.renderTinted(gui, barX, y + 8, right - barX, LogicGuiTextures.UNDERLINE_H, DARKISH);
	}
	/** 常态纯黑底、悬停铺一层灰；文字用分类色并带描边。 */
	private void renderItem(LogicCellButton button, GuiGraphics gui, boolean hovered, Entry entry) {
		var x = button.getX();
		var y = button.getY();
		if (hovered) {
			LogicCursor.setHand();
			// 无说明文本的语句不显示提示
			if (Language.getInstance().has(entry.tipKey())) hoveredTip = entry.tipKey();
		}
		gui.fill(x, y, x + ITEM_W, y + ITEM_H, hovered ? FLAT_OVER : 0xFF000000);
		LogicFont.drawOutlinedCentered(gui, LogicFont.text(entry.nameKey()), x + ITEM_W / 2, y + (ITEM_H - 8) / 2, entry.category().color);
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		var handled = super.mouseClicked(mouseX, mouseY, button);
		// 点内容区空白处时收起搜索框焦点，避免光标持续闪烁
		if (!handled && mouseY >= contentTop() && mouseY < contentBottom() && !search.isMouseOver(mouseX, mouseY)) setFocused(null);
		return handled;
	}
	/** 语句表里的一行：分类标题，或最多 {@link #COLS} 个语句按钮。 */
	private record EntryRow(@Nullable LCategory header, List<Entry> items) {}
}
