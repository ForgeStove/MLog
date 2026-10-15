package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
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
	/** 内容区留白与图标、分隔线的间距。 */
	private static final int PAD = 4, ICON_GAP = 4, BAR_GAP = 4;
	/** 图标 15 单位按行高 40→16 的 0.4 比例折为约 6 像素，故取 {@code 0.55}。 */
	private static final float ICON_SCALE = 0.55F;
	/** 字形留白与文字行高不对齐，靠这两个值校正；{@link #ICON_SHIFT_Y} 往下为正。 */
	private static final int ICON_SHIFT_X = 3, ICON_SHIFT_Y = 3;
	private final int insertAt;
	private final List<Row> rows = new ArrayList<>();
	private final ScrollBar scrollbar = new ScrollBar();
	/** 由 {@link #renderItem} 记下，列表画完再统一出提示。 */
	private @Nullable String hoveredTip;
	@SuppressWarnings("NotNullFieldNotInitialized") private LogicSearchBox search;
	/** 是否需要为滚动条留位。 */
	private boolean scrollable;
	public AddStatementDialog(ProcessorScreen parent, int insertAt) {
		super(parent, LogicFont.text("gui.mlog.add"));
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
			case world -> LogicIcons.TERRAIN;
			default -> null;
		};
	}
	private static boolean isOverItem(int x, int y, double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + ITEM_W && mouseY >= y && mouseY < y + ITEM_H;
	}
	/** 三列按钮加两侧内边距。 */
	@Override
	protected int contentWidth() {
		return PAD * 2 + COLS * ITEM_W + (scrollable ? ScrollBar.WIDTH : 0);
	}
	private void rebuildRows() {
		var query = search.getValue().toLowerCase(Locale.ROOT);
		rows.clear();
		var contentHeight = 0;
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
			rows.add(new Row(category, List.of()));
			contentHeight += HEADER_H;
			for (var i = 0; i < items.size(); i += COLS) {
				rows.add(new Row(null, List.copyOf(items.subList(i, Math.min(i + COLS, items.size())))));
				contentHeight += ITEM_H;
			}
		}
		// 仅滚动时给滚动条留位，使左右内边距对称
		// 宽度变化后搜索框按内容区重新定位
		scrollable = contentHeight > listHeight();
		search.layout(contentLeft() + PAD, contentTop() + PAD, contentRight() - PAD);
		scrollbar.area(barX(), listTop(), listHeight(), contentHeight);
		scrollbar.reset();
	}
	private int listTop() {
		return contentTop() + PAD + LogicSearchBox.HEIGHT + PAD;
	}
	/** 从底框边框往内收，否则末行会盖住框线与圆角。 */
	private int listBottom() {
		return contentBottom() - frameInset();
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
		renderContentFrame(gui, contentTop(), contentBottom() - contentTop());
		hoveredTip = null;
		renderList(gui, mouseX, mouseY);
		scrollbar.render(gui);
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
				parent.getCanvas().insert(insertAt, row.items().getFirst().factory().get());
				onClose();
				return true;
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
	@Override
	protected void init() {
		super.init();
		fillScreen();
		addBottomButtons(new BottomButton("gui.mlog.back", LogicIcons.BACK, b -> onClose()));
		// 搜索框作为控件，键盘输入交给原版处理
		search = new LogicSearchBox();
		search.setResponder(text -> rebuildRows());
		addRenderableWidget(search);
		// 键盘事件依赖 Screen 焦点，仅控件自身 setFocused 只画出光标、收不到按键
		setFocused(search);
		rebuildRows();
	}
	private void renderList(GuiGraphics gui, int mouseX, int mouseY) {
		var top = listTop();
		var bottom = listBottom();
		var clip = LogicClip.begin(gui, contentLeft() + PAD, top, contentRight() - PAD, bottom);
		var mx = clip.mouseX(mouseX);
		var my = clip.mouseY(mouseY);
		var cursor = top - (int) scrollbar.scroll();
		for (var row : rows) {
			var height = row.header() != null ? HEADER_H : ITEM_H;
			var y = cursor;
			cursor += height;
			// 整行在可视区外则跳过，省去逐格悬停判断
			if (y + height <= top || y >= bottom) continue;
			if (row.header() != null) renderHeader(gui, row.header(), y, mx, my);
			else for (var i = 0; i < row.items().size(); i++) renderItem(gui, row.items().get(i), itemX(i), y, mx, my);
		}
		clip.end();
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
		LogicGuiTextures.UNDERLINE.renderTinted(gui, barX, y + 8, contentRight() - PAD - barX, LogicGuiTextures.UNDERLINE_H, DARKISH);
	}
	/** 常态纯黑底、悬停铺一层灰；文字用分类色并带描边。 */
	private void renderItem(GuiGraphics gui, Entry entry, int x, int y, int mouseX, int mouseY) {
		var over = isOverItem(x, y, mouseX, mouseY);
		if (over) {
			LogicCursor.setHand();
			// 无说明文本的语句不显示提示
			if (Language.getInstance().has(entry.tipKey())) hoveredTip = entry.tipKey();
		}
		gui.fill(x, y, x + ITEM_W, y + ITEM_H, over ? FLAT_OVER : 0xFF000000);
		LogicFont.drawOutlinedCentered(gui, LogicFont.text(entry.nameKey()), x + ITEM_W / 2, y + (ITEM_H - 8) / 2, entry.category().color);
	}
	private int itemX(int col) {
		return contentLeft() + PAD + col * ITEM_W;
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// 先给滚动条：点在它上面不得作为插入语句
		if (scrollbar.mousePressed(mouseX, mouseY)) return true;
		var clicked = rowAt(mouseX, mouseY);
		if (clicked != null) {
			LogicSounds.button();
			parent.getCanvas().insert(insertAt, clicked.factory().get());
			onClose();
			return true;
		}
		// 搜索框与底部按钮的命中交给控件自身
		var handled = super.mouseClicked(mouseX, mouseY, button);
		// 点内容区空白处时收起搜索框焦点，避免光标持续闪烁
		if (mouseY >= contentTop() && mouseY < contentBottom() && !search.isMouseOver(mouseX, mouseY)) setFocused(null);
		return handled;
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		scrollbar.release();
		return super.mouseReleased(mouseX, mouseY, button);
	}
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (scrollbar.mouseDragged(mouseY)) return true;
		// 未拖动滚动条时转交控件，搜索框的框选由此接入
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// 可视区高须布局后得知，故每帧现算
		scrollbar.step(listHeight() * ScrollBar.WHEEL_RATIO);
		scrollbar.wheel(-scrollY);
		return true;
	}
	private @Nullable Entry rowAt(double mouseX, double mouseY) {
		var top = listTop();
		if (mouseX < contentLeft() + PAD || mouseX >= contentRight() - PAD || mouseY < top || mouseY >= listBottom()) return null;
		var cursor = top - (int) scrollbar.scroll();
		for (var row : rows) {
			if (row.header() == null) for (var i = 0; i < row.items().size(); i++)
				if (isOverItem(itemX(i), cursor, mouseX, mouseY)) return row.items().get(i);
			cursor += row.header() != null ? HEADER_H : ITEM_H;
		}
		return null;
	}
	/** 语句表里的一行：分类标题，或最多 {@link #COLS} 个语句按钮。 */
	private record Row(@Nullable LCategory header, List<Entry> items) {}
}
