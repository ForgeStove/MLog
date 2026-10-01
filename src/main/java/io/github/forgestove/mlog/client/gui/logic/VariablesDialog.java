package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.*;

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 变量表：
 * <pre>
 * ▌ 变量名 ▌ ┌─值───────┐ ▌ ██ 类型 ██
 * </pre>
 * 竖条分隔各列，最后一根与类型标签按变量类型着色；值变化时闪现强调色。
 */
@OnlyIn(Dist.CLIENT)
public class VariablesDialog extends LogicDialogScreen {
	/**
	 * 行高。
	 * <p>取 45 单位折过来，比例 0.36，45 × 0.36 ≈ 16。
	 */
	private static final int ROW_H = 16;
	/**
	 * 竖条宽度、行列间距、变量名列宽、类型标签列宽。
	 * <p>都按 0.36 的比例折算：竖条 8 → 3，间距 4 → 1.4 取 2，
	 * 变量名列 110 → 40，类型块 120 → 43 取 44。
	 */
	private static final int STUB = 3, GAP = 2, NAME_W = 40, TYPE_W = 44;
	/** 变量名列最多为默认宽度的三倍；更长的名字换行，不再撑宽这一列。 */
	private static final int NAME_MAX_W = NAME_W * 3;
	/** 值变化后高亮持续的毫秒数。 */
	private static final long FLASH_MS = 200;
	/**
	 * 内容区宽度。
	 * <p>各列宽度按行高 40→16 的比例折过来，再加上两侧内边距约 220 单位，
	 * 换算后就是这个量级；再宽的行只会把值列拉空。
	 */
	private static final int CONTENT_W = 220;
	private final List<Entry> entries = new ArrayList<>();
	/** 右侧的滚动条。滚动量、拖动状态与平滑均由它维护。 */
	private final ScrollBar scrollbar = new ScrollBar();
	/** 上一帧的值，用于判断是否变化。 */
	private final Map<String, String> lastValues = new HashMap<>();
	private final Map<String, Long> flashUntil = new HashMap<>();
	/** 变量名列的实际宽度：随最长的名字增长，上限 {@link #NAME_MAX_W}，下限 {@link #NAME_W}。 */
	private int nameW = NAME_W;
	/** 底框的上下边，渲染时记录，鼠标事件据此换算滚动条与行区域。 */
	private int frameTop, frameBottom;
	public VariablesDialog(MicroProcessorScreen parent) {
		super(parent, LogicFont.text("gui.mlog.vars"));
		read();
	}
	/**
	 * 从方块实体取一次快照填进 {@link #entries}。
	 * <p>必须每帧调用：变量位于服务端，客户端仅有每 5 tick 推送的快照；只在构造时读取一次会使值一直停留在打开界面的那一刻。
	 */
	private void read() {
		entries.clear();
		if (!(parent.getMenu().getBlockEntity() instanceof AbstractProcessorBlockEntity processor)) return;
		var snapshot = processor.getVarSnapshot();
		for (var name : snapshot.getAllKeys().stream().sorted().toList()) {
			var entry = snapshot.getCompound(name);
			entries.add(new Entry(name, entry.getString("v"), entry.getInt("t")));
		}
		// 名字列先按最长的名字撑宽，至多三倍；再长则交由换行处理（行高随之增长）
		var maxNameW = 0;
		for (var entry : entries) maxNameW = Math.max(maxNameW, LogicFont.width(LogicFont.text(entry.name())));
		nameW = Math.clamp(maxNameW + GAP * 2, NAME_W, NAME_MAX_W);
	}
	/** @return 这段文字按宽度会折成几行。 */
	private static int lineCount(String text, int width) {
		return mc.font.split(LogicFont.text(text), width).size();
	}
	/** 变量类型对应的颜色。 */
	private static int colorOf(int type) {
		return switch (type) {
			case AbstractProcessorBlockEntity.TYPE_NUMBER -> PLACE;
			case AbstractProcessorBlockEntity.TYPE_NULL -> TEXT_DIM;
			case AbstractProcessorBlockEntity.TYPE_STRING -> AMMO;
			// 方块与链接，以及 query 查出的建筑（存的是坐标）都归入「建筑」一档
			case AbstractProcessorBlockEntity.TYPE_BLOCK, AbstractProcessorBlockEntity.TYPE_LINK, AbstractProcessorBlockEntity.TYPE_BUILDING ->
				BLOCKS;
			// 物品与流体都是内容物，同色
			case AbstractProcessorBlockEntity.TYPE_ITEM, AbstractProcessorBlockEntity.TYPE_FLUID -> OPERATIONS;
			case AbstractProcessorBlockEntity.TYPE_UNIT -> UNITS;
			case AbstractProcessorBlockEntity.TYPE_ENUM -> IO;
			// 无法识别的对象（TYPE_OBJECT）使用普通文字色
			default -> TEXT;
		};
	}
	/** @return 压暗一档的颜色。 */
	private static int dim(int color) {
		return 0xFF000000 | (color >> 16 & 0xFF) / 2 << 16 | (color >> 8 & 0xFF) / 2 << 8 | (color & 0xFF) / 2;
	}
	/** 类型名，不做本地化。 */
	private static String typeName(int type) {
		return switch (type) {
			case AbstractProcessorBlockEntity.TYPE_NUMBER -> "number";
			case AbstractProcessorBlockEntity.TYPE_NULL -> "null";
			case AbstractProcessorBlockEntity.TYPE_STRING -> "string";
			case AbstractProcessorBlockEntity.TYPE_BLOCK -> "block";
			case AbstractProcessorBlockEntity.TYPE_ITEM -> "item";
			case AbstractProcessorBlockEntity.TYPE_FLUID -> "fluid";
			case AbstractProcessorBlockEntity.TYPE_UNIT -> "unit";
			case AbstractProcessorBlockEntity.TYPE_BUILDING -> "building";
			case AbstractProcessorBlockEntity.TYPE_LINK -> "link";
			case AbstractProcessorBlockEntity.TYPE_ENUM -> "enum";
			case AbstractProcessorBlockEntity.TYPE_OBJECT -> "object";
			default -> "unknown";
		};
	}
	/** 两个 ARGB 之间线性插值，{@code t} 为 1 时取 {@code to}。 */
	@SuppressWarnings("SameParameterValue")
	private static int lerp(int from, int to, float t) {
		var r = (int) ((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
		var g = (int) ((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
		var b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
	@Override
	protected void init() {
		super.init();
		fillScreen();
		addBottomButtons(
			new BottomButton("gui.mlog.back", LogicIcons.BACK, b -> onClose()),
			new BottomButton("gui.mlog.globals", LogicIcons.MENU, b -> mc.setScreen(new GlobalVarsDialog(parent, this)))
		);
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		// 每帧重新读取快照，值才能跟随服务端推送的新数据变化
		read();
		renderBackground(gui, mouseX, mouseY, partialTick);
		renderPanel(gui);
		// 整块行内容装在一个按钮纹理的底框里。
		// 框的高度按变量个数收缩：变量少时不留大片空白，撑满后才滚动
		var inset = frameInset();
		var area = contentBottom() - contentTop();
		var frameH = Math.min(contentHeight() + inset * 2, area);
		// 表在内容区中居中摆放，空间不足时从顶部开始
		var frameY = contentTop() + (area - frameH) / 2;
		frameTop = frameY;
		frameBottom = frameY + frameH;
		renderContentFrame(gui, frameY, frameH);
		var left = contentLeft() + inset;
		// 滚动条贴框的右内边；仅当行内容需要滚动时让出这一条宽度，不滚动时不留
		var barX = contentRight() - inset - (scrollable() ? ScrollBar.WIDTH : 0);
		var top = frameY + inset;
		var bottom = frameY + frameH - inset;
		var viewH = bottom - top;
		scrollbar.update(viewH, contentHeight());
		if (entries.isEmpty()) LogicFont.draw(gui, LogicFont.text("gui.mlog.vars.empty"), left, top, TEXT_DIM);
		else renderRows(gui, top, bottom, left, barX);
		scrollbar.render(gui, barX, top, viewH, contentHeight());
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	/** @return 内容区可用高度，表格最多占这么高。 */
	private int contentArea() {
		return contentBottom() - contentTop();
	}
	/** @return 内容是否超出一屏，即值列是否需给滚动条让位。 */
	private boolean scrollable() {
		// 按不留位的宽度测算一遍：让位会使值列变窄、换行增多，两者互相推导会来回翻转
		return contentHeight(false) > contentArea() - frameInset() * 2;
	}
	private int contentHeight() {
		return contentHeight(scrollable());
	}
	/**
	 * @param withBar 值列是否让出滚动条那条宽度
	 * @return 整个表的高度，变量为空时为 0
	 */
	private int contentHeight(boolean withBar) {
		if (entries.isEmpty()) return 0;
		var valueW = valueWidth(withBar);
		var h = 0;
		for (var entry : entries) h += rowHeight(entry.name(), entry.value(), valueW) + GAP;
		return h - GAP;
	}
	private void renderRows(GuiGraphics gui, int top, int bottom, int rowLeft, int rowRight) {
		gui.enableScissor(rowLeft, top, rowRight, bottom);
		var now = Util.getMillis();
		// 各列位置：竖条 / 变量名 / 竖条 / 值 / 竖条 / 类型，值列宽度占据剩余空间。
		// 每格紧贴其左侧竖条（仅此处不留缝），列与列之间留 GAP。
		// 第 1、2 列之间若不留缝，变量名格会顶到分隔竖条上；文字的内边距在 renderRow 中单独留出
		var nameX = rowLeft + STUB;
		var stubMid = nameX + nameW + GAP;
		var valueX = stubMid + STUB;
		var stubType = rowRight - TYPE_W - GAP - STUB;
		var typeX = stubType + STUB;
		var valueW = stubType - valueX - GAP;
		var cursor = top - (int) Math.round(scrollbar.scroll());
		for (var entry : entries) {
			var h = rowHeight(entry.name(), entry.value(), valueW);
			renderRow(gui, entry, cursor, h, now, rowLeft, nameX, stubMid, valueX, valueW, stubType, typeX);
			cursor += h + GAP;
		}
		gui.disableScissor();
	}
	/**
	 * @param withBar 是否让出滚动条那条宽度
	 * @return 值格的宽度，行高按它计算；与排版中由 {@code barX} 算出的值相同
	 */
	private int valueWidth(boolean withBar) {
		return contentWidth() - frameInset() * 2 - STUB * 3 - GAP * 3 - nameW - TYPE_W - (withBar ? ScrollBar.WIDTH : 0);
	}
	/**
	 * @return 这一行的高度：名字与值中折行较多者决定。
	 * 	<p>两者过长都要换行，行高随文字变化：先按 {@link #ROW_H} 起算，
	 * 	多出的行按 MC 字体行高的倍数递增，各行才能对齐。
	 */
	private int rowHeight(String name, String value, int valueW) {
		var lines = Math.max(lineCount(name, nameW - GAP * 2), lineCount(value, valueW - GAP * 2 - panelInset()));
		return ROW_H + (lines - 1) * 9;
	}
	private void renderRow(
		GuiGraphics gui,
		Entry entry,
		int rowY,
		int rowH,
		long now,
		int rowLeft,
		int nameX,
		int stubMid,
		int valueX,
		int valueW,
		int stubType,
		int typeX
	) {
		var typeColor = colorOf(entry.type());
		// 前两条竖条为灰色；
		// 仅类型竖条跟随类型，类型变化时随之换色。名字格与类型块各占半行高居中，行高增加时同步增长
		var midY = rowY + rowH / 2;
		gui.fill(rowLeft, rowY, rowLeft + STUB, rowY + rowH, STUB_DIM);
		gui.fill(stubMid, rowY, stubMid + STUB, rowY + rowH, STUB_DIM);
		gui.fill(stubType, rowY, stubType + STUB, rowY + rowH, dim(typeColor));
		// 变量名铺灰底，仅铺本格、不越过右侧列间距；文字在其中留一个左边距。
		// 高度跟随整行：值换行后行高增加，该格须同步增长，否则与另一侧的类型块对不齐
		gui.fill(nameX, rowY, nameX + nameW, rowY + rowH, STUB_CELL);
		// 名字过长则换行，不再截断，行高已按其行数计算
		var nameLines = mc.font.split(LogicFont.text(entry.name()), nameW - GAP * 2);
		var nameY = midY - nameLines.size() * 9 / 2;
		for (var line : nameLines) {
			LogicFont.draw(gui, line, nameX + GAP, nameY, ACCENT);
			nameY += 9;
		}
		// 值装在面板纹理里。文字过长会换行
		LogicGuiTextures.PANE_SOLID.render(gui, valueX, rowY, valueW, rowH);
		// 文字再向内让出一个面板边框的宽度，避免压住边框
		var textX = valueX + GAP + panelInset();
		var lines = mc.font.split(LogicFont.text(entry.value()), valueW - GAP * 2 - panelInset());
		var lineY = midY - lines.size() * 9 / 2;
		for (var line : lines) {
			LogicFont.draw(gui, line, textX, lineY, valueColor(entry, now));
			lineY += 9;
		}
		// 类型标签为整格类型色实心块加深色文字
		gui.fill(typeX, rowY, typeX + TYPE_W, rowY + rowH, typeColor);
		var typeY = midY - 4;
		LogicFont.draw(gui, LogicFont.literal(typeName(entry.type())), typeX + GAP, typeY, HEADER_TEXT);
	}
	/**
	 * @return 内容区宽度。表本身即为此宽度，不按屏幕比例撑开，底框随屏幕拉满会显得空旷。
	 * 	<p>名字列撑宽时表一并变宽，值格宽度保持不变，否则名字变长会挤压值列。
	 * 	屏幕确实放不下时才收回，优先保证两侧均不越界。
	 */
	@Override
	protected int contentWidth() {
		return Math.min(CONTENT_W + nameW - NAME_W, width - frameInset() * 2);
	}
	/** 值变化时闪一下强调色后淡回白色。 */
	private int valueColor(Entry entry, long now) {
		var last = lastValues.put(entry.name(), entry.value());
		// 首次出现不算变化
		if (last != null && !last.equals(entry.value())) flashUntil.put(entry.name(), now + FLASH_MS);
		var remain = flashUntil.getOrDefault(entry.name(), 0L) - now;
		if (remain <= 0) return TEXT;
		return lerp(TEXT, ACCENT, remain / (float) FLASH_MS);
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		var top = frameTop + frameInset();
		var viewH = frameBottom - frameInset() - top;
		if (scrollbar.mousePressed(mouseX, mouseY, barX(), top, viewH, contentHeight())) return true;
		return super.mouseClicked(mouseX, mouseY, button);
	}
	/** @return 滚动条所在的 x，贴着框的右内边。 */
	private int barX() {
		return contentRight() - frameInset() - ScrollBar.WIDTH;
	}
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		var top = frameTop + frameInset();
		var viewH = frameBottom - frameInset() - top;
		if (scrollbar.mouseDragged(mouseY, top, viewH, contentHeight())) return true;
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (scrollbar.dragging()) {
			scrollbar.release();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}
	/** 覆盖对话框基类的暂停：值依赖服务端每 5 tick 推送，暂停后无法看到任何更新。 */
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollbar.wheel(-scrollY);
		return true;
	}
	private record Entry(String name, String value, int type) {}
}
