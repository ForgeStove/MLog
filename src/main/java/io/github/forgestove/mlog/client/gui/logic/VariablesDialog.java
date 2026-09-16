package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlockEntity;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.*;

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 变量表，布局对齐 Mindustry 的 {@code @variables}：
 * <pre>
 * ▌ 变量名 ▌ ┌─值───────┐ ▌ ██ 类型 ██
 * </pre>
 * 竖条分隔各列，最后一根与类型标签按变量类型着色；值变化时闪一下强调色。
 */
@OnlyIn(Dist.CLIENT)
public class VariablesDialog extends LogicDialogScreen {
	private static final int PAD = 6;
	/** 行高与行间距。 */
	private static final int ROW_H = 16, ROW_GAP = 3;
	/** 竖条宽度、列间距、变量名列宽、类型标签列宽。 */
	private static final int STUB = 2, GAP = 4, NAME_W = 64, TYPE_W = 52;
	/** 值变化后高亮持续的毫秒数。 */
	private static final long FLASH_MS = 200;
	private final List<Entry> entries = new ArrayList<>();
	/** 上一帧的值，用来判断有没有变化。 */
	private final Map<String, String> lastValues = new HashMap<>();
	private final Map<String, Long> flashUntil = new HashMap<>();
	private double scroll, targetScroll;
	public VariablesDialog(MicroProcessorScreen parent) {
		super(parent, LogicFont.text("gui.mlog.vars"));
		if (parent.getMenu().getBlockEntity() instanceof MicroProcessorBlockEntity processor) read(processor.getVarSnapshot());
	}
	/** 快照是服务端每 5 tick 推一次的值。 */
	private void read(CompoundTag snapshot) {
		for (var name : snapshot.getAllKeys().stream().sorted().toList()) {
			var entry = snapshot.getCompound(name);
			entries.add(new Entry(name, entry.getString("v"), entry.getInt("t")));
		}
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
		renderBackground(gui, mouseX, mouseY, partialTick);
		renderPanel(gui);
		var top = contentTop() + PAD;
		var bottom = contentBottom();
		var viewH = bottom - top;
		targetScroll = Math.clamp(targetScroll, 0, Math.max(0, contentHeight() - viewH));
		scroll += (targetScroll - scroll) * 0.35;
		if (Math.abs(targetScroll - scroll) < 0.5) scroll = targetScroll;
		if (entries.isEmpty()) LogicFont.draw(gui, LogicFont.text("gui.mlog.vars.empty"), contentLeft() + PAD, top, TEXT_DIM);
		else renderRows(gui, top, bottom, viewH);
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	private int contentHeight() {
		return entries.size() * (ROW_H + ROW_GAP) - ROW_GAP;
	}
	private void renderRows(GuiGraphics gui, int top, int bottom, int viewH) {
		gui.enableScissor(contentLeft() + PAD, top, contentRight() - PAD, bottom);
		var now = Util.getMillis();
		// 各列位置：竖条 / 变量名 / 竖条 / 值 / 竖条 / 类型，值的宽度吃掉剩余空间
		var stubHead = contentLeft() + PAD;
		var nameX = stubHead + STUB + GAP;
		var stubMid = nameX + NAME_W + GAP;
		var valueX = stubMid + STUB + GAP;
		var stubType = contentRight() - PAD - TYPE_W - GAP - STUB;
		var typeX = contentRight() - PAD - TYPE_W;
		var valueW = stubType - GAP - valueX;
		var cursor = top - (int) scroll;
		for (var entry : entries) {
			renderRow(gui, entry, cursor, now, stubHead, nameX, stubMid, valueX, valueW, stubType, typeX);
			cursor += ROW_H + ROW_GAP;
		}
		gui.disableScissor();
	}
	private void renderRow(
		GuiGraphics gui,
		Entry entry,
		int rowY,
		long now,
		int stubHead,
		int nameX,
		int stubMid,
		int valueX,
		int valueW,
		int stubType,
		int typeX
	) {
		var typeColor = colorOf(entry.type());
		var stubTop = rowY + 2;
		var stubBottom = rowY + ROW_H - 2;
		gui.fill(stubHead, stubTop, stubHead + STUB, stubBottom, STUB);
		gui.fill(stubMid, stubTop, stubMid + STUB, stubBottom, STUB);
		gui.fill(stubType, stubTop, stubType + STUB, stubBottom, typeColor);
		var textY = rowY + ROW_H / 2 - 4;
		// 变量名用强调色，和 Mindustry 一致
		LogicFont.draw(gui, LogicFont.clipped(entry.name(), NAME_W), nameX, textY, ACCENT);
		// 值装在带边框的暗色面板里
		gui.fill(valueX, rowY, valueX + valueW, rowY + ROW_H, 0x40000000);
		gui.renderOutline(valueX, rowY, valueW, ROW_H, BORDER);
		LogicFont.draw(gui, LogicFont.clipped(entry.value(), valueW - 6), valueX + 3, textY, valueColor(entry, now));
		// 类型标签是类型色实心块 + 深色文字
		gui.fill(typeX, rowY, typeX + TYPE_W, rowY + ROW_H, typeColor);
		LogicFont.draw(gui, LogicFont.literal(typeName(entry.type())), typeX + 4, textY, HEADER_TEXT);
	}
	/** 类型色，对应 Mindustry 的 {@code typeColor}。 */
	private static int colorOf(int type) {
		return switch (type) {
			case MicroProcessorBlockEntity.TYPE_NUMBER -> PLACE;
			case MicroProcessorBlockEntity.TYPE_NULL -> TEXT_DIM;
			case MicroProcessorBlockEntity.TYPE_STRING -> AMMO;
			case MicroProcessorBlockEntity.TYPE_BLOCK -> BLOCKS;
			case MicroProcessorBlockEntity.TYPE_ITEM -> OPERATIONS;
			case MicroProcessorBlockEntity.TYPE_LINK -> BLOCKS;
			case MicroProcessorBlockEntity.TYPE_ENUM -> IO;
			default -> TEXT;
		};
	}
	/** 值变化时闪一下强调色再淡回白色，对应 Mindustry 的闪烁反馈。 */
	private int valueColor(Entry entry, long now) {
		var last = lastValues.put(entry.name(), entry.value());
		// 首次出现不算变化
		if (last != null && !last.equals(entry.value())) flashUntil.put(entry.name(), now + FLASH_MS);
		var remain = flashUntil.getOrDefault(entry.name(), 0L) - now;
		if (remain <= 0) return TEXT;
		return lerp(TEXT, ACCENT, remain / (float) FLASH_MS);
	}
	/** 类型名，对应 Mindustry 的 {@code typeName}（它也不做本地化）。 */
	private static String typeName(int type) {
		return switch (type) {
			case MicroProcessorBlockEntity.TYPE_NUMBER -> "number";
			case MicroProcessorBlockEntity.TYPE_NULL -> "null";
			case MicroProcessorBlockEntity.TYPE_STRING -> "string";
			case MicroProcessorBlockEntity.TYPE_BLOCK -> "block";
			case MicroProcessorBlockEntity.TYPE_ITEM -> "item";
			case MicroProcessorBlockEntity.TYPE_LINK -> "link";
			case MicroProcessorBlockEntity.TYPE_ENUM -> "enum";
			default -> "unknown";
		};
	}
	/** 两个 ARGB 之间线性插值，{@code t} 为 1 时取 {@code to}。 */
	private static int lerp(int from, int to, float t) {
		var r = (int) ((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
		var g = (int) ((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
		var b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
	/**
	 * 覆盖对话框基类的暂停：这里的值要靠服务端每 5 tick 推送，暂停了就什么都看不到。
	 * <p>对应 Mindustry 打开变量表时把游戏切回 playing、关闭时再恢复 paused。
	 */
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		var viewH = contentBottom() - (contentTop() + PAD);
		targetScroll = Math.clamp(targetScroll - scrollY * 12, 0, Math.max(0, contentHeight() - viewH));
		return true;
	}
	private record Entry(String name, String value, int type) {}
}
