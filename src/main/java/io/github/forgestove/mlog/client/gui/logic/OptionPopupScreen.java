package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Picker;
import io.github.forgestove.mlog.logic.LAccess;
import io.github.forgestove.mlog.logic.Table.OptionGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 参数控件点开后弹出的选项列表；独立界面使父界面自绘为背景、事件由 MC 隔离，紧贴参数框弹出。 */
public class OptionPopupScreen extends Screen {
	/** 选项行高，按 0.4 折算自 40。 */
	private static final int ROW_H = 16, PAD = 2;
	/** 分组按钮行的高度，按 0.4 折算自 50。 */
	private static final int GROUP_H = 20;
	/** 物品/流体按钮中图标的边长，同时也是这两组的按钮宽度。 */
	private static final int ICON_W = 16;
	/** 分组按钮选中时高亮边框的粗细。按 0.4 折算自 4 得 1.6，取 2。 */
	private static final int GROUP_BORDER = 2;
	/** 搜索行留白须大于 {@link #PAD}（{@code PANE_SOLID} 灰边宽 2）。 */
	private static final int SEARCH_PAD = 4;
	/** 选项名到小写本地化名的缓存，见 {@link #localized}。 */
	private static final Map<String, String> LOCALIZED = new HashMap<>();
	private static final int CELL_PAD = 6;
	/** {@link #LOCALIZED} 对应的语言，空串表示尚未填充；语言变化时整体清空重填。 */
	private static String localizedLanguage = "";
	private final ProcessorScreen parent;
	private final Picker picker;
	/** 选中后的回调；算子会改变参数个数，须重建卡片控件。 */
	private final Runnable onSelect;
	private final List<OptionGroup> groups;
	/** 每组的选项，构造时取好：{@code Supplier} 开销大（物品表上千条），不得放入每帧渲染。 */
	private final List<List<String>> groupOptions;
	/** 是否显示搜索框，取自 {@link Picker#searchable()}。 */
	private final boolean searchable;
	private final ScrollBar scrollbar = new ScrollBar();
	/** 当前分组过滤搜索词后的选项；随搜索词变化重算，不放在每帧渲染里。 */
	private List<String> filtered;
	private @Nullable LogicSearchBox search;
	/** 尺寸与位置随分组重算：各组选项数与最宽项数量级相差很大，弹窗长宽随之变化。 */
	private int colW;
	private int x, y, width, height, visible;
	/** 选项区的可视高度，滚动条按它算滑块。 */
	private int viewH;
	/** 选项超出一屏时显示滚动条并在右侧为它留位。 */
	private boolean scrollable;
	private int selected;
	public OptionPopupScreen(ProcessorScreen parent, Picker picker, Runnable onSelect) {
		super(Component.empty());
		this.parent = parent;
		this.picker = picker;
		this.onSelect = onSelect;
		groups = picker.groups;
		var cached = new ArrayList<List<String>>();
		if (groups.isEmpty()) cached.add(picker.options.get());
		else for (var group : groups) cached.add(group.options().get());
		groupOptions = List.copyOf(cached);
		searchable = picker.searchable();
		// 恢复上次的位置：分组与滚动位置都存在 Picker 上，同一参数控件关闭再打开时保持原状
		selected = groups.isEmpty() ? 0 : Math.clamp(picker.lastGroup, 0, groups.size() - 1);
		filtered = groupOptions.get(selected);
		// 搜索框须在 relayout 之前建立，否则停在 (0,0)、光标落在屏幕左上角
		if (searchable) {
			search = new LogicSearchBox();
			search.setResponder(text -> {
				refilter();
				scrollbar.reset();
				relayout();
			});
			// 恢复上次的搜索词。setValue 会走一遍 responder，filtered 随之算好
			search.setValue(picker.lastQuery);
		}
		relayout();
		// 恢复滚动位置必须在 relayout 之后，此时可视区高度已知
		scrollbar.seek(picker.lastScroll);
	}
	/** 不是注册项时返回空串；注册表查询开销大（上千条）故做缓存，进入时先校验语言是否变化。 */
	private static String localized(String option) {
		var language = mc.getLanguageManager().getSelected();
		if (!language.equals(localizedLanguage)) {
			localizedLanguage = language;
			LOCALIZED.clear();
		}
		return LOCALIZED.computeIfAbsent(
			option, key -> {
				// control 的属性名不带 @，其本地化名同样参与搜索
				if (LAccess.isControl(key)) return Component.translatable(LAccess.controlKey(key)).getString().toLowerCase(Locale.ROOT);
				if (!key.startsWith("@")) return "";
				var name = key.substring(1);
				// 内置属性同样匹配本地化名
				if (LAccess.byName(name) instanceof LAccess access)
					return Component.translatable(access.key()).getString().toLowerCase(Locale.ROOT);
				var id = ResourceLocation.tryParse(name);
				if (id == null) return "";
				if (BuiltInRegistries.ITEM.containsKey(id))
					return new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString().toLowerCase(Locale.ROOT);
				if (BuiltInRegistries.FLUID.containsKey(id))
					return new FluidStack(BuiltInRegistries.FLUID.get(id), 1).getHoverName().getString().toLowerCase(Locale.ROOT);
				return "";
			}
		);
	}
	/** 改用界面字体，保留其余样式（物品名自带的颜色）。 */
	private static Component onLogicFont(Component text) {
		return text.copy().withStyle(style -> style.withFont(LogicFont.ID));
	}
	private static @Nullable LogicIcons iconOf(String name) {
		return switch (name) {
			case "box" -> LogicIcons.BOX;
			case "liquid" -> LogicIcons.LIQUID;
			case "tree" -> LogicIcons.TREE;
			default -> null;
		};
	}
	/** 画一圈 {@code border} 像素粗的轮廓。 */
	private static void outline(GuiGraphics gui, int x, int y, int w, int h, int border, int color) {
		gui.fill(x, y, x + w, y + border, color);
		gui.fill(x, y + h - border, x + w, y + h, color);
		gui.fill(x, y + border, x + border, y + h - border, color);
		gui.fill(x + w - border, y + border, x + w, y + h - border, color);
	}
	private void refilter() {
		var all = groupOptions.get(selected);
		var query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		if (query.isEmpty()) {
			filtered = all;
			return;
		}
		// 注册名与本地化名均可匹配
		filtered = all.stream()
			.filter(option -> option.toLowerCase(Locale.ROOT).contains(query) || localized(option).contains(query))
			.toList();
	}
	/** 切换分组时须重算弹窗长宽，否则选项少的组会留出空白、滚动条比例失真。 */
	private void relayout() {
		// 分组按钮行与搜索行占的高度
		var extra = headerH() + (searchable ? LogicSearchBox.HEIGHT + PAD : 0);
		// 图标组宽度按图标算；文字组按整组选项算，不受过滤影响，否则搜出短名后弹窗收缩
		if (iconGroup()) colW = ICON_W;
		else {
			var textW = 0;
			for (var option : groupOptions.get(selected)) textW = Math.max(textW, LogicFont.width(LogicFont.text(picker.display(option))));
			colW = textW + CELL_PAD * 2;
		}
		// 至少显示一行；屏幕太矮时 Math.clamp 会因为上界小于下界而抛异常
		visible = Math.clamp(Math.max(1, rows()), 1, Math.max(1, (parent.height - PAD * 2 - extra) / ROW_H));
		viewH = visible * ROW_H;
		// 滚动条的留位按整组计算，不受过滤结果影响，否则宽度仍会跳动
		scrollable = rowsFull() > visible;
		scrollbar.step(viewH * ScrollBar.WHEEL_RATIO);
		// 不滚动时不为滚动条留位，否则右侧会多出一条空档
		width = Math.min(colW * cols() + PAD * 2 + (scrollable ? ScrollBar.WIDTH : 0), parent.width);
		height = viewH + PAD * 2 + extra;
		// 以触发它的按钮为中心；越出屏幕时钳回屏内
		var centerX = picker.anchorCenter();
		var centerY = picker.y + ParamElement.SIZE / 2;
		x = Math.clamp(centerX - width / 2, 0, Math.max(0, parent.width - width));
		y = Math.clamp(centerY - height / 2, 0, Math.max(0, parent.height - height));
		scrollbar.area(barX(), listTop(), viewH, rows() * ROW_H);
		if (search == null) return;
		search.layout(x + SEARCH_PAD, searchY(), x + width - SEARCH_PAD);
	}
	private int headerH() {
		return groups.size() <= 1 ? 0 : GROUP_H;
	}
	private boolean iconGroup() {
		if (groups.isEmpty()) return false;
		return switch (groups.get(selected).icon()) {
			case "box", "liquid", "char" -> true;
			default -> false;
		};
	}
	private int rows() {
		return (filtered.size() + cols() - 1) / cols();
	}
	/** 按过滤后的结果决定滚动条留位会使弹窗宽度跳动，故用整组不过滤时的行数。 */
	private int rowsFull() {
		return (groupOptions.get(selected).size() + cols() - 1) / cols();
	}
	/** 分组时由组自定，否则用 {@link Picker#cols()}；上限须按整组选项数钳制。 */
	private int cols() {
		var want = groups.isEmpty() ? picker.cols() : groups.get(selected).cols();
		return Math.clamp(want, 1, Math.max(1, groupOptions.get(selected).size()));
	}
	private int searchY() {
		return y + PAD + headerH();
	}
	/** 流体贴图整块不透明，铺在底层的高亮会被完全遮盖，故高亮画法与其余组不同。 */
	private boolean liquidGroup() {
		return !groups.isEmpty() && "liquid".equals(groups.get(selected).icon());
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(0, 0, -100);
		var previous = mc.screen;
		mc.screen = parent;
		try {
			LogicTooltip.whileRenderingParent(() -> parent.render(gui, -1, -1, partialTick));
		} finally {
			// 父界面渲染若抛异常，mc.screen 会永久停在父界面上
			mc.screen = previous;
		}
		pose.popPose();
		scrollbar.update();
		// 必须是不透明实心底，否则会透出后方的卡片与世界
		LogicGuiTextures.PANE_SOLID.render(gui, x, y, width, height);
		if (!groups.isEmpty()) renderGroups(gui, mouseX, mouseY);
		if (search != null) search.render(gui, mouseX, mouseY, partialTick);
		var current = picker.get.get();
		var top = listTop();
		var contentX = x + PAD;
		var contentW = width - PAD * 2 - (scrollable ? ScrollBar.WIDTH : 0);
		var first = (int) (scrollbar.scroll() / ROW_H);
		// 分组在整轮渲染中不变，提前取出以免逐格判断
		var liquid = liquidGroup();
		Component tooltip = null;
		var clip = LogicClip.begin(gui, contentX, top, contentX + contentW, top + viewH);
		var mx = clip.mouseX(mouseX);
		var my = clip.mouseY(mouseY);
		for (var i = 0; i < visible * cols(); i++) {
			var index = first * cols() + i;
			if (index >= filtered.size()) break;
			var option = filtered.get(index);
			var ox = contentX + i % cols() * colW;
			var oy = top + i / cols() * ROW_H;
			var hovered = mx >= ox && mx < ox + colW && my >= oy && my < oy + ROW_H;
			if (hovered) {
				LogicCursor.setHand();
				tooltip = hoverName(option);
			}
			// 选中铺强调色底、悬停铺灰底，文字始终为白色；流体组例外，高亮改画在图标之上，见循环之后
			var isCurrent = option.equals(current);
			var highlight = isCurrent ? ACCENT : HOVER;
			if (!liquid && (isCurrent || hovered)) gui.fill(ox, oy, ox + colW, oy + ROW_H, highlight);
			// 物品/流体只铺图标，其余组画文字
			if (!renderIcon(gui, option, ox + (colW - ICON_W) / 2, oy))
				LogicFont.drawOutlinedCentered(gui, LogicFont.text(picker.display(option)), ox + colW / 2, oy + (ROW_H - 8) / 2, TEXT);
				// 流体贴图整块不透明，铺在底层的高亮会被完全遮盖，改为叠在其上的一圈边框
			else if (liquid && (isCurrent || hovered)) outline(gui, ox, oy, colW, ROW_H, 1, highlight);
		}
		clip.end();
		scrollbar.render(gui);
		LogicTooltip.render(gui, tooltip, mouseX, mouseY, parent.width, parent.height);
	}
	/** 关闭后回到编辑器，不走 {@code Screen} 默认的弹出界面栈。 */
	@Override
	public void onClose() {
		// 记录本次的浏览状态，下次打开时恢复
		picker.lastGroup = selected;
		picker.lastScroll = scrollbar.scroll();
		picker.lastQuery = search == null ? "" : search.getValue();
		mc.setScreen(parent);
	}
	/** 窗口尺寸变化会重建控件，故搜索框在此登记而非构造器中。 */
	@Override
	protected void init() {
		super.init();
		if (search == null) return;
		addRenderableWidget(search);
		setInitialFocus(search);
	}
	@Override
	public void resize(Minecraft minecraft, int width, int height) {
		super.resize(minecraft, width, height);
		parent.resize(minecraft, width, height);
		relayout();
	}
	/** 顶部的分组按钮行，各占等宽的一段，图标居中。 */
	private void renderGroups(GuiGraphics gui, int mouseX, int mouseY) {
		// 只有一组时不绘制分组按钮，该行无可切换的内容
		if (groups.size() <= 1) return;
		var gy = y + PAD;
		var gw = width / groups.size();
		for (var i = 0; i < groups.size(); i++) {
			var gx = x + i * gw;
			var hovered = mouseX >= gx && mouseX < gx + gw && mouseY >= gy && mouseY < gy + GROUP_H;
			if (hovered) LogicCursor.setHand();
			// 选中描边框、悬停平铺灰底；不铺 WHITE_PANE：其边距 12 在 20 高按钮上仅 0.33 倍，白边不可见
			if (i == selected) outline(gui, gx, gy, gw, GROUP_H, GROUP_BORDER, ACCENT);
			else if (hovered) gui.fill(gx, gy, gx + gw, gy + GROUP_H, HOVER);
			var icon = iconOf(groups.get(i).icon());
			if (icon == null) continue;
			icon.render(gui, gx + (gw - icon.width()) / 2, LogicIcons.centerY(gy, GROUP_H), TEXT);
		}
	}
	/** 在 {@code (x,y)} 铺一个物品或流体图标；未铺时由调用方退回画文字。 */
	// @NotNull 在这条调用链上不成立：未注册客户端扩展的流体，getStillTexture 返回 null
	private boolean renderIcon(GuiGraphics gui, String option, int x, int y) {
		if (!iconGroup() || !option.startsWith("@")) return false;
		var id = ResourceLocation.tryParse(option.substring(1));
		if (id == null) return false;
		if ("box".equals(groups.get(selected).icon())) {
			if (!BuiltInRegistries.ITEM.containsKey(id)) return false;
			gui.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(id)), x, y);
			return true;
		}
		if (!BuiltInRegistries.FLUID.containsKey(id)) return false;
		// 流体没有物品那样的模型，取静止贴图自行铺设
		var stack = new FluidStack(BuiltInRegistries.FLUID.get(id), 1);
		var ext = IClientFluidTypeExtensions.of(stack.getFluid());
		var still = ext.getStillTexture(stack);
		var sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
		var tint = ext.getTintColor(stack);
		gui.blit(
			x,
			y,
			0,
			ICON_W,
			ICON_W,
			sprite,
			ARGB32.red(tint) / 255F,
			ARGB32.green(tint) / 255F,
			ARGB32.blue(tint) / 255F,
			ARGB32.alpha(tint) / 255F
		);
		return true;
	}
	private @Nullable Component hoverName(String option) {
		var tip = enumTip(option);
		if (tip != null) return tip;
		// control 的属性名不带 @，属于白名单键
		if (LAccess.isControl(option)) return LogicFont.text(LAccess.controlTipKey(option));
		if (!option.startsWith("@")) return null;
		var name = option.substring(1);
		// 内置属性返回说明文案而非列表中的名字，后者已显示在按钮上
		if (LAccess.byName(name) instanceof LAccess access) return LogicFont.text(access.tipKey());
		if (!iconGroup()) return null;
		var id = ResourceLocation.tryParse(name);
		if (id == null) return null;
		if (BuiltInRegistries.ITEM.containsKey(id)) return onLogicFont(new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName());
		if (BuiltInRegistries.FLUID.containsKey(id)) return onLogicFont(new FluidStack(BuiltInRegistries.FLUID.get(id), 1).getHoverName());
		return null;
	}
	/** 归属由字段指明（见 {@code Table#option}）；未编写说明者不显示提示。 */
	private @Nullable Component enumTip(String option) {
		var key = picker.tipKey(option);
		return key == null ? null : LogicFont.tip(key);
	}
	private int barX() {
		return x + width - PAD - ScrollBar.WIDTH;
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!inBounds(mouseX, mouseY)) {
			onClose();
			return true;
		}
		// 搜索框优先处理，它需自行定位光标；命中即结束，未命中说明点击在别处，收起焦点
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		if (search != null) search.setFocused(false);
		// 分组按钮与搜索框都在选项区之外，判定先于滚动条
		if (!groups.isEmpty() && mouseY < listTop()) {
			var index = (int) ((mouseX - x) / (width / (double) groups.size()));
			if (index >= 0 && index < groups.size() && index != selected) {
				selected = index;
				refilter();
				scrollbar.reset();
				relayout();
				LogicSounds.button();
			}
			return true;
		}
		// 再交由滚动条处理：点击滚动条不应视为选择选项
		if (scrollbar.mousePressed(mouseX, mouseY)) return true;
		var col = (int) ((mouseX - (x + PAD)) / colW);
		var row = (int) ((mouseY - listTop()) / ROW_H) + (int) (scrollbar.scroll() / ROW_H);
		if (col < 0 || col >= cols() || row < 0) return true;
		var index = row * cols() + col;
		if (index >= filtered.size()) return true;
		LogicSounds.button();
		picker.set.accept(filtered.get(index));
		onClose();
		onSelect.run();
		return true;
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
		if (!inBounds(mouseX, mouseY)) return true;
		scrollbar.wheel(-scrollY);
		return true;
	}
	private int listTop() {
		return searchY() + (searchable ? LogicSearchBox.HEIGHT + PAD : 0);
	}
	private boolean inBounds(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
