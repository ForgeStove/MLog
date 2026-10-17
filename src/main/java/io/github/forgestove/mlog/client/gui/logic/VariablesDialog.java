package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.component.*;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import io.github.forgestove.mlog.logic.VarType;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 变量表：变量名、值、类型三列以竖条分隔，末根竖条与类型标签按类型着色，值变化时闪现强调色。 */
public class VariablesDialog extends LogicDialogScreen {
	/** 行高：按 0.36 比例折算 45 单位，45 × 0.36 ≈ 16。 */
	private static final int ROW_H = 16;
	/** 竖条宽、列间距、变量名列宽、类型标签列宽：均按 0.36 折算。 */
	private static final int STUB = 3, GAP = 2, NAME_W = 40, TYPE_W = 44;
	/** 变量名列最多为默认宽度的三倍；更长的名字换行，不再撑宽这一列。 */
	private static final int NAME_MAX_W = NAME_W * 3;
	private static final long FLASH_MS = 200;
	/** 内容区宽度：各列按行高 40→16 的比例折算并计两侧内边距。 */
	private static final int CONTENT_W = 220;
	private final List<Entry> entries = new ArrayList<>();
	private final Map<String, String> lastValues = new HashMap<>();
	private final Map<String, Long> flashUntil = new HashMap<>();
	/** 推送时整份替换，引用未变即内容未变，无须重排。 */
	private @Nullable CompoundTag lastSnapshot;
	/** 变量名列的实际宽度：随最长的名字增长，上限 {@link #NAME_MAX_W}，下限 {@link #NAME_W}。 */
	private int nameW = NAME_W;
	public VariablesDialog(ProcessorScreen parent) {
		super(parent, LogicFont.text("gui.mlog.vars"));
		refresh();
	}
	private static int colorOf(VarType type) {
		return switch (type) {
			case NUMBER -> PLACE;
			case NULL -> TEXT_DIM;
			case STRING -> AMMO;
			case BLOCK, LINK, BLOCK_POS -> BLOCKS;
			case ITEM, FLUID -> OPERATIONS;
			case ENTITY, ENTITY_TYPE -> UNITS;
			case ENUM -> IO;
			case LIST, OBJECT -> TEXT;
		};
	}
	private static int dim(int color) {
		return 0xFF000000 | (color >> 16 & 0xFF) / 2 << 16 | (color >> 8 & 0xFF) / 2 << 8 | (color & 0xFF) / 2;
	}
	/** 两个 ARGB 之间线性插值，{@code t} 为 1 时取 {@code to}。 */
	@SuppressWarnings("SameParameterValue")
	private static int lerp(int from, int to, float t) {
		var r = (int) ((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
		var g = (int) ((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
		var b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
	/** 快照换了才重排：变量位于服务端，客户端只有每 5 tick 推送的快照。 */
	private void refresh() {
		var snapshot = parent.getMenu().getBlockEntity() instanceof AbstractProcessorBlockEntity processor
			? processor.getVarSnapshot()
			: null;
		if (snapshot == lastSnapshot) return;
		lastSnapshot = snapshot;
		entries.clear();
		if (snapshot == null) return;
		for (var name : snapshot.getAllKeys().stream().sorted().toList()) {
			var entry = snapshot.getCompound(name);
			entries.add(new Entry(name, entry.getString("v"), VarType.byId(entry.getByte("t"))));
		}
		// 名字列按最长名字撑宽，至多三倍；再长交由换行处理
		var maxNameW = 0;
		for (var entry : entries) maxNameW = Math.max(maxNameW, LogicFont.width(LogicFont.text(entry.name())));
		nameW = Math.clamp(maxNameW + GAP * 2, NAME_W, NAME_MAX_W);
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		// 每帧检查快照，使值跟随服务端推送更新
		refresh();
		renderBackground(gui, mouseX, mouseY, partialTick);
		renderPanel(gui);
		// 整块行内容装在按钮纹理底框里，框高按变量数收缩，撑满后滚动
		var inset = frameInset();
		var area = contentBottom() - contentTop();
		var frameH = Math.min(contentHeight() + inset * 2, area);
		// 表在内容区中居中摆放，空间不足时从顶部开始
		var frameY = contentTop() + (area - frameH) / 2;
		renderContentFrame(gui, frameY, frameH);
		var left = contentLeft() + inset;
		// 滚动条贴框的右内边；仅当需要滚动时让出这一条宽度
		var barX = contentRight() - inset - (scrollable() ? ScrollBar.WIDTH : 0);
		var top = frameY + inset;
		var bottom = frameY + frameH - inset;
		var viewH = bottom - top;
		scrollbar.area(barX, top, viewH, contentHeight());
		scrollbar.update();
		if (entries.isEmpty()) LogicFont.draw(gui, LogicFont.text("gui.mlog.vars.empty"), left, top, TEXT_DIM);
		else renderRows(gui, top, bottom, left, barX);
		scrollbar.render(gui, mouseX, mouseY, partialTick);
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	@Override
	protected void init() {
		super.init();
		addBottomButtons(
			new BottomButton("gui.mlog.back", LogicIcons.BACK, b -> onClose()),
			new BottomButton("gui.mlog.globals", LogicIcons.MENU, b -> mc.setScreen(new GlobalVarsDialog(parent, this)))
		);
	}
	private int contentArea() {
		return contentBottom() - contentTop();
	}
	private boolean scrollable() {
		// 按不留位的宽度测算：让位会使值列变窄、换行增多，互相推导会来回翻转
		return contentHeight(false) > contentArea() - frameInset() * 2;
	}
	private int contentHeight() {
		return contentHeight(scrollable());
	}
	/** 变量为空时返回 0。 */
	private int contentHeight(boolean withBar) {
		if (entries.isEmpty()) return 0;
		var valueW = valueWidth(withBar);
		var h = 0;
		for (var entry : entries) h += rowHeight(entry, valueW) + GAP;
		return h - GAP;
	}
	private void renderRows(GuiGraphics gui, int top, int bottom, int rowLeft, int rowRight) {
		var clip = LogicClip.begin(gui, rowLeft, top, rowRight, bottom);
		var now = Util.getMillis();
		// 各列依次为竖条、变量名、竖条、值、竖条、类型，值列占剩余宽度，列间留 GAP
		// 第 1、2 列不留缝会使变量名格覆盖竖条；文字内边距在 renderRow 单独留出
		var nameX = rowLeft + STUB;
		var stubMid = nameX + nameW + GAP;
		var valueX = stubMid + STUB;
		var stubType = rowRight - TYPE_W - GAP - STUB;
		var typeX = stubType + STUB;
		var valueW = stubType - valueX - GAP;
		var cursor = top - (int) Math.round(scrollbar.scroll());
		for (var entry : entries) {
			var h = rowHeight(entry, valueW);
			renderRow(gui, entry, cursor, h, now, rowLeft, nameX, stubMid, valueX, valueW, stubType, typeX);
			cursor += h + GAP;
		}
		clip.end();
	}
	/** 值格宽度，行高按它计算，须与排版一致。 */
	private int valueWidth(boolean withBar) {
		return contentWidth() - frameInset() * 2 - STUB * 3 - GAP * 3 - nameW - TYPE_W - (withBar ? ScrollBar.WIDTH : 0);
	}
	/** 行高：{@link #ROW_H} 起算，折行较多者多出的行按 9 递增，使各行对齐。 */
	private int rowHeight(Entry entry, int valueW) {
		var lines = Math.max(entry.nameLines(nameW - GAP * 2).size(), entry.valueLines(valueW - GAP * 2 - panelInset()).size());
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
		// 前两条竖条为灰色，仅类型竖条跟随类型；名字格与类型块各占半行高居中
		gui.fill(rowLeft, rowY, rowLeft + STUB, rowY + rowH, STUB_DIM);
		gui.fill(stubMid, rowY, stubMid + STUB, rowY + rowH, STUB_DIM);
		gui.fill(stubType, rowY, stubType + STUB, rowY + rowH, dim(typeColor));
		// 变量名铺灰底、过长换行；值装在面板纹理里，文字内边距再让出一个面板边框宽
		LogicRow.cell(gui, nameX, rowY, nameW, rowH, STUB_CELL, GAP, ACCENT, entry.nameLines(nameW - GAP * 2));
		LogicRow.pane(
			gui,
			valueX,
			rowY,
			valueW,
			rowH,
			GAP + panelInset(),
			valueColor(entry, now),
			entry.valueLines(valueW - GAP * 2 - panelInset())
		);
		gui.fill(typeX, rowY, typeX + TYPE_W, rowY + rowH, typeColor);
		LogicFont.draw(gui, LogicFont.literal(entry.type().display()), typeX + GAP, rowY + rowH / 2 - 4, HEADER_TEXT);
	}
	/** 名字列撑宽时表一并变宽、值格宽度不变，避免名字变长挤压值列；屏幕放不下时收回。 */
	@Override
	protected int contentWidth() {
		return Math.min(CONTENT_W + nameW - NAME_W, width - frameInset() * 2);
	}
	/** 值依赖服务端每 5 tick 推送，暂停后看不到任何更新。 */
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	/** 值变化后在 {@link #FLASH_MS} 内由强调色淡回普通文字色。 */
	private int valueColor(Entry entry, long now) {
		var last = lastValues.put(entry.name(), entry.value());
		// 首次出现不算变化
		if (last != null && !last.equals(entry.value())) flashUntil.put(entry.name(), now + FLASH_MS);
		var remain = flashUntil.getOrDefault(entry.name(), 0L) - now;
		if (remain <= 0) return TEXT;
		return lerp(TEXT, ACCENT, remain / (float) FLASH_MS);
	}
	/** 折行结果由 {@link LogicText} 按列宽缓存。 */
	private static final class Entry {
		private final LogicText name;
		private final LogicText value;
		private final VarType type;
		private Entry(String name, String value, VarType type) {
			this.name = new LogicText(name);
			this.value = new LogicText(value);
			this.type = type;
		}
		private String name() {
			return name.text();
		}
		private String value() {
			return value.text();
		}
		private VarType type() {
			return type;
		}
		private List<FormattedCharSequence> nameLines(int width) {
			return name.lines(width);
		}
		private List<FormattedCharSequence> valueLines(int width) {
			return value.lines(width);
		}
	}
}
