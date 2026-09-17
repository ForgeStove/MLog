package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlockEntity;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
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
	/**
	 * 行高。
	 * <p>取 Mindustry 的 45 单位折过来（那边 UI 基准行高 40，这里是 16），比例 0.36，45 × 0.36 ≈ 16。
	 */
	private static final int ROW_H = 16;
	/**
	 * 竖条宽度、行列间距、变量名列宽、类型标签列宽。
	 * <p>都按那边同一比例折算：竖条 8 → 3，间距 4 → 1.4 取 2，
	 * 变量名列 110 → 40，类型块 120 → 43 取 44。
	 */
	private static final int STUB = 3, GAP = 2, NAME_W = 40, TYPE_W = 44;
	/** 值变化后高亮持续的毫秒数。 */
	private static final long FLASH_MS = 200;
	/**
	 * 内容区宽度。
	 * <p>各列宽度是按 Mindustry 那边 40 的行高折过来的（这里行高 16），原表加上两侧内边距约 220 单位，
	 * 换算后就是这个量级；再宽的行只会把值那列拉空。
	 */
	private static final int CONTENT_W = 220;
	private final List<Entry> entries = new ArrayList<>();
	/** 右侧的滚动条。滚动量、拖动状态与平滑都在它自己身上。 */
	private final ScrollBar scrollbar = new ScrollBar();
	/** 上一帧的值，用来判断有没有变化。 */
	private final Map<String, String> lastValues = new HashMap<>();
	private final Map<String, Long> flashUntil = new HashMap<>();
	/** 底框的上下边，渲染时记下，鼠标事件按它换算滚动条与行区域。 */
	private int frameTop, frameBottom;
	public VariablesDialog(MicroProcessorScreen parent) {
		super(parent, LogicFont.text("gui.mlog.vars"));
		read();
	}
	/**
	 * 从方块实体取一次快照填进 {@link #entries}。
	 * <p>必须每帧调：Mindustry 的值就在同一个进程里，读一下就是最新的；
	 * 这里变量在服务端，客户端只有每 5 tick 推来的快照，只在构造时读一次的话值会一直停在开界面那一刻。
	 */
	private void read() {
		entries.clear();
		if (!(parent.getMenu().getBlockEntity() instanceof MicroProcessorBlockEntity processor)) return;
		var snapshot = processor.getVarSnapshot();
		for (var name : snapshot.getAllKeys().stream().sorted().toList()) {
			var entry = snapshot.getCompound(name);
			entries.add(new Entry(name, entry.getString("v"), entry.getInt("t")));
		}
	}
	/**
	 * @return 内容区宽度。表本身就这么宽，不像基类那样按屏幕比例撑开——
	 * 	Mindustry 那边的变量表是表占自己需要的宽度、居中摆在撑满父容器的对话框里，
	 * 	底框跟着屏幕拉满会显得空旷。
	 */
	@Override
	protected int contentWidth() {
		return CONTENT_W;
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
		// 每帧重读快照，值才会跟着服务端推的新数据动
		read();
		renderBackground(gui, mouseX, mouseY, partialTick);
		renderPanel(gui);
		// 整块行内容装在一个按钮纹理的底框里，对齐 Mindustry 的 table(Tex.button)。
		// 框的高度按变量个数收：变量少的时候不该留一个空荡荡的大框，撑满了才滚
		var inset = frameInset();
		var area = contentBottom() - contentTop();
		var frameH = Math.min(contentHeight() + inset * 2, area);
		// 表在内容区里居中摆，对齐 Mindustry 那个撑满父容器、表居中的对话框。空间不够时才从顶上开始
		var frameY = contentTop() + (area - frameH) / 2;
		frameTop = frameY;
		frameBottom = frameY + frameH;
		renderContentFrame(gui, frameY, frameH);
		var left = contentLeft() + inset;
		// 滚动条贴着框的右内边，行内容让出它这一条宽度
		var barX = contentRight() - inset - ScrollBar.WIDTH;
		var top = frameY + inset;
		var bottom = frameY + frameH - inset;
		var viewH = bottom - top;
		scrollbar.update(viewH, contentHeight());
		if (entries.isEmpty()) LogicFont.draw(gui, LogicFont.text("gui.mlog.vars.empty"), left, top, TEXT_DIM);
		else renderRows(gui, top, bottom, left, barX);
		scrollbar.render(gui, barX, top, viewH, contentHeight());
		renderContent(gui, mouseX, mouseY, partialTick);
	}
	/** @return 滚动条所在的 x，贴着框的右内边。 */
	private int barX() {
		return contentRight() - frameInset() - ScrollBar.WIDTH;
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		var top = frameTop + frameInset();
		var viewH = frameBottom - frameInset() - top;
		if (scrollbar.mousePressed(mouseX, mouseY, barX(), top, viewH, contentHeight())) return true;
		return super.mouseClicked(mouseX, mouseY, button);
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
	/**
	 * @return 值那格换算行后每行的高度。
	 * 	<p>值过长要像 Mindustry 那样换行，行高跟着文字走：一格先按 {@link #ROW_H} 起算，
	 * 	多出来的行按 MC 字体行高的倍数往上加，各行之间才对齐。
	 */
	private int rowHeight(String value, int valueW) {
		var lines = mc.font.split(LogicFont.text(value), valueW - GAP * 2 - panelInset()).size();
		return ROW_H + (lines - 1) * 9;
	}
	private int contentHeight() {
		if (entries.isEmpty()) return 0;
		var valueW = valueWidth();
		var h = 0;
		for (var entry : entries) h += rowHeight(entry.value(), valueW) + GAP;
		return h - GAP;
	}
	/** @return 值那格的宽度，行高与排版都按它算。 */
	private int valueWidth() {
		return contentWidth() - frameInset() * 2 - STUB * 3 - GAP * 3 - NAME_W - TYPE_W - ScrollBar.WIDTH;
	}
	private void renderRows(GuiGraphics gui, int top, int bottom, int rowLeft, int rowRight) {
		gui.enableScissor(rowLeft, top, rowRight, bottom);
		var now = Util.getMillis();
		// 各列位置：竖条 / 变量名 / 竖条 / 值 / 竖条 / 类型，值的宽度吃掉剩余空间。
		// 每格紧贴着它左边那条竖条（只有这里不留缝），列与列之间留 GAP。
		// 1、2 两列之间原来没留，变量名格就顶到分隔竖条上了；文字的内边距在 renderRow 里单独留
		var nameX = rowLeft + STUB;
		var stubMid = nameX + NAME_W + GAP;
		var valueX = stubMid + STUB;
		var stubType = rowRight - TYPE_W - GAP - STUB;
		var typeX = stubType + STUB;
		var valueW = stubType - valueX - GAP;
		var cursor = top - (int) Math.round(scrollbar.scroll());
		for (var entry : entries) {
			var h = rowHeight(entry.value(), valueW);
			renderRow(gui, entry, cursor, h, now, rowLeft, nameX, stubMid, valueX, valueW, stubType, typeX);
			cursor += h + GAP;
		}
		gui.disableScissor();
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
		// 前两条竖条是灰的，对齐 Mindustry 的 Pal.gray.cpy().mul(0.5f)；
		// 只有类型那条跟着类型走，类型一变它也跟着换色。名字格与类型块各占半行高居中，行一高就跟着长
		var midY = rowY + rowH / 2;
		gui.fill(rowLeft, rowY, rowLeft + STUB, rowY + rowH, STUB_DIM);
		gui.fill(stubMid, rowY, stubMid + STUB, rowY + rowH, STUB_DIM);
		gui.fill(stubType, rowY, stubType + STUB, rowY + rowH, dim(typeColor));
		// 变量名铺灰底，只铺本格、不越过右侧那道列间距；文字在里面留一个左边距
		gui.fill(nameX, midY - ROW_H / 2, nameX + NAME_W, midY + ROW_H / 2, STUB_CELL);
		var nameY = midY - 4;
		LogicFont.draw(gui, LogicFont.clipped(entry.name(), NAME_W - GAP), nameX + GAP, nameY, ACCENT);
		// 值装在面板纹理里，对齐 Mindustry 的 table(Tex.pane)。文字过长会换行
		LogicGuiTextures.PANE_SOLID.render(gui, valueX, rowY, valueW, rowH);
		// 文字再往里让一个面板边框的宽度，别压在边框上
		var textX = valueX + GAP + panelInset();
		var lines = mc.font.split(LogicFont.text(entry.value()), valueW - GAP * 2 - panelInset());
		var lineY = midY - lines.size() * 9 / 2;
		for (var line : lines) {
			LogicFont.draw(gui, line, textX, lineY, valueColor(entry, now));
			lineY += 9;
		}
		// 类型标签是整格类型色实心块 + 深色文字
		gui.fill(typeX, rowY, typeX + TYPE_W, rowY + rowH, typeColor);
		var typeY = midY - 4;
		LogicFont.draw(gui, LogicFont.literal(typeName(entry.type())), typeX + GAP, typeY, HEADER_TEXT);
	}
	/** 类型色，对应 Mindustry 的 {@code typeColor}。 */
	private static int colorOf(int type) {
		return switch (type) {
			case MicroProcessorBlockEntity.TYPE_NUMBER -> PLACE;
			case MicroProcessorBlockEntity.TYPE_NULL -> TEXT_DIM;
			case MicroProcessorBlockEntity.TYPE_STRING -> AMMO;
			case MicroProcessorBlockEntity.TYPE_BLOCK, MicroProcessorBlockEntity.TYPE_LINK -> BLOCKS;
			case MicroProcessorBlockEntity.TYPE_ITEM -> OPERATIONS;
			case MicroProcessorBlockEntity.TYPE_ENUM -> IO;
			default -> TEXT;
		};
	}
	/** @return 压暗一档的颜色，对齐 Mindustry 里给竖条用的 {@code color.cpy().mul(0.5f)}。 */
	private static int dim(int color) {
		return 0xFF000000 | (color >> 16 & 0xFF) / 2 << 16 | (color >> 8 & 0xFF) / 2 << 8 | (color & 0xFF) / 2;
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
		scrollbar.wheel(-scrollY);
		return true;
	}
	private record Entry(String name, String value, int type) {}
}
