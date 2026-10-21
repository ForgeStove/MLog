package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.component.*;
import io.github.forgestove.mlog.client.gui.logic.ClippedCellGrid.*;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Picker;
import io.github.forgestove.mlog.logic.LAccess;
import io.github.forgestove.mlog.logic.Table.OptionGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 参数控件点开后弹出的选项列表；独立界面使父界面自绘为背景、事件由 MC 隔离，紧贴参数框弹出。 */
public class OptionPopupScreen extends ScrollableScreen {
	/** 选项行高，按 0.4 折算自 40。 */
	private static final int ROW_H = 16, PAD = 2;
	/** 分组按钮行的高度，按 0.4 折算自 50。 */
	private static final int GROUP_H = 20;
	/** 横排分类按钮的最小宽度：弹窗最小宽度为其总和。 */
	private static final int GROUP_BTN_W = 24;
	/** 物品/流体按钮中图标的边长，同时也是这两组的按钮宽度。 */
	private static final int ICON_W = 16;
	/** 试听按钮与选项行同高的正方形，边长 16。 */
	private static final int PREVIEW_W = 16;
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
	/** 第二维：左侧文字分类；为空表示只有顶部一维。 */
	private final List<OptionGroup> sideGroups;
	private final List<List<String>> sideGroupOptions;
	/** 是否显示搜索框，取自 {@link Picker#searchable()}。 */
	private final boolean searchable;
	/** 分组为左侧分类列；单组时无分类可列，仍按横排处理。 */
	private final boolean sidebar;
	/** 分类列与选项列之间显示试听列，仅侧栏模式下有意义。 */
	private final boolean preview;
	/** 分组按钮、试听按钮与选项格控件。 */
	private final List<LogicCellButton> groupButtons = new ArrayList<>(), sideButtons = new ArrayList<>(), previews = new ArrayList<>(),
		cells = new ArrayList<>();
	/** 选项列与分类列各绑定一条滚动条（第 0/1 条），偏移、裁剪与命中都由网格处理。 */
	private final ClippedCellGrid grid = new ClippedCellGrid(bar(0));
	private final ClippedCellGrid groupGrid = new ClippedCellGrid(bar(1));
	/** 每个选项所属的分类 label，供「全部」显示「分类/条目」。 */
	private final Map<String, String> categoryByOption = new HashMap<>();
	/** 正在试听的实例及其对应的选项名；再次点击同一行即停止。 */
	private @Nullable SoundInstance previewing;
	private @Nullable String previewed;
	/** 当前分组过滤搜索词后的选项；随搜索词变化重算，不放在每帧渲染里。 */
	private List<String> filtered;
	private @Nullable LogicSearchBox search;
	/** 首次 init 之前创建的格控件不直接登记，由 init 统一登记；此后新建的即时登记。 */
	private boolean widgetsReady;
	/** 尺寸与位置随分组重算：各组选项数与最宽项数量级相差很大，弹窗长宽随之变化。 */
	private int colW, sidebarW, groupH = ROW_H, groupBarW;
	private int x, y, width, height, visible;
	private int selected, selectedSide;
	/** 顶部与左侧所选分类的交集，搜索在它之上再做过滤。 */
	private List<String> base;
	/** 当前顶部种类下仍有内容的左侧分类下标；切换种类时重算。 */
	private List<Integer> visibleSides = List.of();
	private @Nullable Component hoveredTooltip;
	public OptionPopupScreen(ProcessorScreen parent, Picker picker, Runnable onSelect) {
		super(Component.empty());
		this.parent = parent;
		this.picker = picker;
		this.onSelect = onSelect;
		var source = picker.groups;
		var cached = new ArrayList<List<String>>();
		if (source.isEmpty()) cached.add(playable(picker.options.get()));
		else for (var group : source) cached.add(playable(group.options().get()));
		// 过滤后为空的分组不再列出，空组会让侧栏出现点不出东西的分类
		if (source.isEmpty()) {
			groups = List.of();
			groupOptions = List.copyOf(cached);
		} else {
			var keptGroups = new ArrayList<OptionGroup>();
			var keptOptions = new ArrayList<List<String>>();
			for (var i = 0; i < source.size(); i++) {
				if (cached.get(i).isEmpty()) continue;
				keptGroups.add(source.get(i));
				keptOptions.add(cached.get(i));
			}
			// 全部被过滤时保留一个空组，避免后续按下标取组
			groups = List.copyOf(keptGroups);
			groupOptions = keptOptions.isEmpty() ? List.of(List.of()) : List.copyOf(keptOptions);
		}
		for (var i = 0; i < groups.size(); i++) {
			var label = groups.get(i).label();
			if (label.isEmpty() || "all".equals(label)) continue;
			for (var option : groupOptions.get(i)) categoryByOption.put(option, label);
		}
		// 第二维的选项已由调用方分好，单独缓存；空组不列出
		var sideSources = new ArrayList<OptionGroup>();
		var sideOptions = new ArrayList<List<String>>();
		for (var group : picker.sideGroups) {
			var options = group.options().get();
			if (options.isEmpty()) continue;
			sideSources.add(group);
			sideOptions.add(options);
		}
		// 只有一个分类时没有可切换的内容，退化为单维
		if (sideSources.size() > 1) {
			sideGroups = List.copyOf(sideSources);
			sideGroupOptions = List.copyOf(sideOptions);
		} else {
			sideGroups = List.of();
			sideGroupOptions = List.of();
		}
		searchable = picker.searchable();
		sidebar = picker.sidebar && groups.size() > 1;
		preview = picker.preview && sidebar;
		// 恢复上次的位置：分组与滚动位置都存在 Picker 上，同一参数控件关闭再打开时保持原状
		selected = groups.isEmpty() ? 0 : Math.clamp(picker.lastGroup, 0, groups.size() - 1);
		selectedSide = sideGroups.isEmpty() ? 0 : Math.clamp(picker.lastSideGroup, 0, sideGroups.size() - 1);
		refreshBase();
		filtered = base;
		// 搜索框须在 relayout 之前建立，否则停在 (0,0)、光标落在屏幕左上角
		if (searchable) {
			search = new LogicSearchBox();
			search.setResponder(text -> {
				stopPreview();
				refilter();
				scrollbar.reset();
				relayout();
			});
			// 恢复上次的搜索词。setValue 会走一遍 responder，filtered 随之算好
			search.setValue(picker.lastQuery);
		}
		for (var i = 0; i < groups.size(); i++) addGroupButton(i);
		for (var i = 0; i < sideGroups.size(); i++) addSideButton(i);
		grid.windowed(true);
		relayout();
		// 恢复滚动位置必须在 relayout 之后，此时可视区高度已知
		scrollbar.seek(picker.lastScroll);
	}
	/** 注册表里存在、但 sounds.json 没定义的音效（如 {@code item.goat_horn.play}）不会出声，选项里直接剔除。 */
	private static List<String> playable(List<String> options) {
		var manager = mc.getSoundManager();
		return options.stream().filter(option -> {
			if (!option.startsWith("@")) return true;
			var id = ResourceLocation.tryParse(option.substring(1));
			// 物品/流体等非声音选项不受影响
			if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) return true;
			var event = manager.getSoundEvent(id);
			return event != null && event.getWeight() > 0;
		}).toList();
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
				// control 的属性名不带 @；未本地化的方块属性用原名参与搜索
				if (LAccess.isControl(key)) {
					var controlKey = LAccess.controlKey(key);
					return Language.getInstance().has(controlKey)
						? Component.translatable(controlKey).getString().toLowerCase(Locale.ROOT)
						: key.toLowerCase(Locale.ROOT);
				}
				if (!key.startsWith("@")) return "";
				var name = key.substring(1);
				// 内置属性同样匹配本地化名；方块属性只有原名
				if (LAccess.byName(name) instanceof LAccess access) return access.blockProperty()
					? name.toLowerCase(Locale.ROOT)
					: Component.translatable(access.key()).getString().toLowerCase(Locale.ROOT);
				var id = ResourceLocation.tryParse(name);
				if (id == null) return "";
				if (BuiltInRegistries.ITEM.containsKey(id))
					return new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString().toLowerCase(Locale.ROOT);
				if (BuiltInRegistries.FLUID.containsKey(id))
					return new FluidStack(BuiltInRegistries.FLUID.get(id), 1).getHoverName().getString().toLowerCase(Locale.ROOT);
				var effect = BuiltInRegistries.MOB_EFFECT.get(id);
				if (effect != null) return effect.getDisplayName().getString().toLowerCase(Locale.ROOT);
				if (BuiltInRegistries.ENTITY_TYPE.containsKey(id))
					return BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString().toLowerCase(Locale.ROOT);
				if (BuiltInRegistries.BLOCK.containsKey(id))
					return BuiltInRegistries.BLOCK.get(id).getName().getString().toLowerCase(Locale.ROOT);
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
			case "effect" -> LogicIcons.EFFECT;
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
	/** 去掉分类前缀：模组是命名空间（"create:"），原版是路径首段（"block."）。 */
	private static String stripCategory(String text, String label) {
		if (label.isEmpty()) return text;
		if (text.startsWith(label + ":") || text.startsWith(label + ".")) return text.substring(label.length() + 1);
		return text;
	}
	/** 分类名依次查选项分类、{@code soundcategory.mlog}、ModMenu 的模组名翻译、模组显示名，最后原样显示。 */
	private static String groupLabel(OptionGroup group) {
		var label = group.label();
		if (label.isEmpty()) return "";
		var language = Language.getInstance();
		var key = "mlog.option.group." + label;
		if (language.has(key)) return Component.translatable(key).getString();
		key = "mlog.soundcategory." + label;
		if (language.has(key)) return Component.translatable(key).getString();
		// NeoForge 的模组显示名不带本地化，ModMenu 的键是模组界面通用的翻译约定
		var modmenu = "modmenu.nameTranslation." + label;
		if (language.has(modmenu)) return Component.translatable(modmenu).getString();
		return ModList.get().getModContainerById(label).map(mod -> mod.getModInfo().getDisplayName()).orElse(label);
	}
	/** 选项所属分类：分组本身已划分好归属，直接反查。 */
	private String categoryOf(String option) {
		return categoryByOption.getOrDefault(option, "other");
	}
	/** 顶部与左侧分类都选定后的选项；没有左侧列时就是顶部分组本身。 */
	private void refreshBase() {
		var top = groupOptions.get(selected);
		if (sideGroups.isEmpty()) {
			base = top;
			return;
		}
		// 只保留在当前种类下取得到内容的分类，避免点进去是一片空白
		var topSet = new HashSet<>(top);
		var visible = new ArrayList<Integer>();
		for (var i = 0; i < sideGroupOptions.size(); i++)
			if (sideGroupOptions.get(i).stream().anyMatch(topSet::contains)) visible.add(i);
		// 原选中分类在新种类下没有内容时，退回第一个有内容的分类
		if (!visible.contains(selectedSide)) selectedSide = visible.isEmpty() ? 0 : visible.getFirst();
		visibleSides = List.copyOf(visible);
		if (visible.isEmpty()) {
			base = List.of();
			return;
		}
		var side = new HashSet<>(sideGroupOptions.get(selectedSide));
		base = top.stream().filter(side::contains).toList();
	}
	private void refilter() {
		var query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		if (query.isEmpty()) {
			filtered = base;
			return;
		}
		// 注册名与本地化名均可匹配
		filtered = base.stream()
			.filter(option -> option.toLowerCase(Locale.ROOT).contains(query) || localized(option).contains(query))
			.toList();
	}
	/** 具体分类只显示条目名；「全部」显示为「分类/条目」，分类名走本地化。 */
	private String display(String option) {
		var text = picker.display(option);
		if (!sidebar) return text;
		var label = groups.get(selected).label();
		if (label.isEmpty()) return text;
		if (!"all".equals(label)) return stripCategory(text, label);
		var category = categoryOf(option);
		var prefix = groups.stream()
			.filter(group -> category.equals(group.label()))
			.findFirst()
			.map(OptionPopupScreen::groupLabel)
			.orElse(category);
		return prefix + "/" + stripCategory(text, category);
	}
	private void addGroupButton(int index) {
		var button = new LogicCellButton(
			0,
			0,
			(self, gui, mouseX, mouseY, hovered, pressed) -> renderGroup(self, index, gui, hovered, pressed)
		);
		button.setAction(() -> {
			if (index != selected) switchGroup(index);
		});
		groupButtons.add(widgetsReady ? addWidget(button) : button);
	}
	private void addSideButton(int index) {
		var button = new LogicCellButton(
			0,
			0,
			(self, gui, mouseX, mouseY, hovered, pressed) -> renderSide(self, index, gui, hovered, pressed)
		);
		button.setAction(() -> {
			if (index != selectedSide) switchSide(index);
		});
		sideButtons.add(widgetsReady ? addWidget(button) : button);
	}
	/** 有左侧列：第二维在当前种类下至少还剩两个分类，且未被单维侧栏模式占用。 */
	private boolean hasSide() {
		return !sidebar && visibleSides.size() > 1;
	}
	/** 侧列行数：单维侧栏是顶部分组数，双维是当前可见的分类数。 */
	private int sideCount() {
		return sidebar ? groups.size() : visibleSides.size();
	}
	private void addCell(int slot) {
		var cell = new LogicCellButton(
			0,
			0,
			(self, gui, mouseX, mouseY, hovered, pressed) -> renderCell(slot, self, gui, hovered, pressed)
		);
		cell.setAction(() -> pressCell(slot));
		cells.add(widgetsReady ? addWidget(cell) : cell);
	}
	/** 切换分组时须重算弹窗长宽，否则选项少的组会留出空白、滚动条比例失真。 */
	private void relayout() {
		// 分组按钮行与搜索行占的高度
		var extra = headerH() + (searchable ? LogicSearchBox.HEIGHT + PAD : 0);
		// 图标组宽度按图标算；文字组按整组选项算，不受过滤影响，否则搜出短名后弹窗收缩
		if (iconGroup()) colW = ICON_W;
		else {
			var textW = 0;
			for (var option : base) textW = Math.max(textW, LogicFont.width(LogicFont.text(display(option))));
			colW = textW + CELL_PAD * 2;
		}
		// 分类列宽取最长的本地化组名
		if (sidebar) {
			sidebarW = 0;
			for (var group : groups) sidebarW = Math.max(sidebarW, LogicFont.width(LogicFont.text(groupLabel(group))));
			sidebarW += CELL_PAD * 2;
		} else if (hasSide()) {
			sidebarW = 0;
			for (var index : visibleSides)
				sidebarW = Math.max(sidebarW, LogicFont.width(LogicFont.text(groupLabel(sideGroups.get(index)))));
			sidebarW += CELL_PAD * 2;
		}
		// 至少显示一行；屏幕太矮时 Math.clamp 会因为上界小于下界而抛异常
		var maxRows = Math.max(1, (parent.height - PAD * 2 - extra) / ROW_H);
		visible = Math.clamp(Math.max(1, rows()), 1, maxRows);
		var viewH = visible * ROW_H;
		// 分类列放得下就撑到全部分类；超出屏幕高度时才启用独立滚动条
		groupBarW = 0;
		var sideColumn = sidebar || hasSide();
		if (sideColumn) {
			groupH = ROW_H;
			var count = sideCount();
			var available = maxRows * ROW_H;
			if (count * groupH <= available) viewH = Math.max(viewH, count * groupH);
			else {
				viewH = Math.max(viewH, available);
				groupBarW = ScrollBar.WIDTH;
			}
		}
		// 滚动条的留位按整组计算，不受过滤结果影响，否则宽度仍会跳动
		var scrollable = rowsFull() > visible;
		var sideW = sideColumn ? sidebarW + groupBarW + PAD : 0;
		// 试听列夹在分类列与选项列之间，滚动条仍按整组计算
		var previewW = preview ? PREVIEW_W + PAD : 0;
		var baseW = colW * cols() + sideW + previewW + PAD * 2 + (scrollable ? ScrollBar.WIDTH : 0);
		// 顶部横排的分类按钮各占一份；文字分类按名字宽度留位
		var minW = 0;
		if (!sidebar && groups.size() > 1) {
			for (var group : groups)
				minW += Math.max(GROUP_BTN_W, LogicFont.width(LogicFont.text(groupLabel(group))) + CELL_PAD * 2);
			minW += PAD * 2;
		}
		// 不滚动时不为滚动条留位，否则右侧会多出一条空档
		width = Math.clamp(baseW, Math.min(minW, parent.width), parent.width);
		height = viewH + PAD * 2 + extra;
		// 以触发它的按钮为中心；越出屏幕时钳回屏内
		var centerX = picker.anchorCenter();
		var centerY = picker.y + ParamElement.SIZE / 2;
		x = Math.clamp(centerX - width / 2, 0, Math.max(0, parent.width - width));
		y = Math.clamp(centerY - height / 2, 0, Math.max(0, parent.height - height));
		var contentW = width - PAD * 2 - sideW - previewW - (scrollable ? ScrollBar.WIDTH : 0);
		// 文字组按内容区宽度均分列宽，任意列数都能铺满，不在右侧留空
		if (!sidebar && !iconGroup()) colW = Math.max(colW, contentW / cols());
		grid.area(x + PAD + sideW + previewW, listTop(), contentW, viewH, barX(), colW, rows() * ROW_H);
		if (sideColumn) {
			groupGrid.area(x + PAD, listTop(), sidebarW, viewH, x + PAD + sidebarW, sidebarW, sideCount() * groupH);
			// 滚轮在整列分类上都能滚，不必对着细条
			bar(1).wheelArea(x + PAD, listTop(), sidebarW + groupBarW, viewH);
			bar(1).step(viewH * ScrollBar.WHEEL_RATIO);
		}
		if (search != null) search.layout(x + SEARCH_PAD, searchY(), x + width - SEARCH_PAD);
		syncWidgets();
	}
	private void addPreview(int row) {
		var button = new LogicCellButton(
			0,
			0,
			(self, gui, mouseX, mouseY, hovered, pressed) -> renderPreview(row, self, gui, hovered, pressed)
		);
		button.setAction(() -> pressPreview(row));
		previews.add(widgetsReady ? addWidget(button) : button);
	}
	/** 试听列与选项行一一对应，滚动时按当前可见行取选项；悬停灰底、按下强调色内边框。 */
	private void renderPreview(int row, LogicCellButton self, GuiGraphics gui, boolean hovered, boolean pressed) {
		var option = optionAt(row * cols());
		if (option == null) return;
		if (hovered) LogicCursor.setHand();
		var px = self.getX();
		var py = self.getY();
		var pw = self.getWidth();
		var ph = self.getHeight();
		if (pressed) outline(gui, px + 1, py + 1, pw - 2, ph - 2, 1, ACCENT);
		else if (hovered) gui.fill(px, py, px + pw, py + ph, HOVER);
		var playing = option.equals(previewed) && previewing != null && mc.getSoundManager().isActive(previewing);
		var icon = playing ? LogicIcons.PAUSE : LogicIcons.PLAY;
		// 整数除会截断，宽高差为奇数时向左偏一像素；按中心取整
		icon.render(gui, Math.round(px + pw / 2F - icon.width() / 2F), LogicIcons.centerY(py, ph), TEXT);
	}
	/** 点同一行停止，点别的行换曲；试听走 UI 通道，不影响 {@code playmusic} 的音乐状态。 */
	private void pressPreview(int row) {
		var option = optionAt(row * cols());
		if (option == null) return;
		var manager = mc.getSoundManager();
		if (option.equals(previewed) && previewing != null && manager.isActive(previewing)) {
			stopPreview();
			return;
		}
		stopPreview();
		var id = ResourceLocation.tryParse(option.startsWith("@") ? option.substring(1) : option);
		var event = id == null ? null : BuiltInRegistries.SOUND_EVENT.get(id);
		if (event == null) return;
		previewed = option;
		previewing = SimpleSoundInstance.forUI(event, 1f, 1f);
		manager.play(previewing);
	}
	private void stopPreview() {
		if (previewing != null) mc.getSoundManager().stop(previewing);
		previewing = null;
		previewed = null;
	}
	private @Nullable String optionAt(int slot) {
		var index = firstRow() * cols() + slot;
		return index < filtered.size() ? filtered.get(index) : null;
	}
	/** 首可见行；行高固定，直接由滚动量算出。 */
	private int firstRow() {
		return Math.clamp((int) (scrollbar.scroll() / ROW_H), 0, Math.max(0, rows() - visible));
	}
	private void pressCell(int slot) {
		var option = optionAt(slot);
		if (option == null) return;
		LogicSounds.button();
		picker.set.accept(option);
		onClose();
		onSelect.run();
	}
	private void renderCell(int slot, LogicCellButton cell, GuiGraphics gui, boolean hovered, boolean pressed) {
		var option = optionAt(slot);
		if (option == null) return;
		var ox = cell.getX();
		var oy = cell.getY();
		if (hovered) {
			LogicCursor.setHand();
			hoveredTooltip = hoverName(option);
		}
		// 选中与按下均铺强调色底，悬停铺灰底；流体组例外，高亮改画在图标之上
		var isCurrent = option.equals(picker.get.get());
		var active = isCurrent || pressed;
		var highlight = active ? ACCENT : HOVER;
		var liquid = liquidGroup();
		if (!liquid && (active || hovered)) gui.fill(ox, oy, ox + colW, oy + ROW_H, highlight);
		// 物品/流体只铺图标，其余组画文字
		if (!renderIcon(gui, option, ox + (colW - ICON_W) / 2, oy))
			LogicFont.drawOutlinedCentered(gui, LogicFont.text(display(option)), ox + colW / 2, oy + (ROW_H - 8) / 2, TEXT);
			// 流体贴图整块不透明，铺在底层的高亮会被完全遮盖，改为叠在其上的一圈边框
		else if (liquid && (active || hovered)) outline(gui, ox, oy, colW, ROW_H, 1, highlight);
	}
	/** 按当前尺寸定位分组按钮，并为可见行补足选项格。 */
	private void syncWidgets() {
		// 分类按钮留出左右内边距；按比例取整分配，余数不会全堆在右侧
		var avail = Math.max(0, width - PAD * 2);
		var count = groups.size();
		for (var i = 0; i < groupButtons.size(); i++) {
			var button = groupButtons.get(i);
			var show = groups.size() > 1;
			button.visible = show;
			button.active = show;
			// 侧栏的定位、偏移与裁剪都交给 groupGrid
			if (sidebar) continue;
			var left = Math.round(i * (float) avail / count);
			var right = Math.round((i + 1) * (float) avail / count);
			button.setX(x + PAD + left);
			button.setY(y + PAD);
			button.setWidth(right - left);
			button.setHeight(GROUP_H);
		}
		if (sidebar) {
			var rows = new ArrayList<Row>();
			for (var button : groupButtons) rows.add(new CellRow(ROW_H, List.of(button)));
			groupGrid.rows(rows);
		} else if (!sideGroups.isEmpty()) {
			// 内容为空分类的按钮直接隐藏，也不进网格
			var rows = new ArrayList<Row>();
			for (var i = 0; i < sideButtons.size(); i++) {
				var button = sideButtons.get(i);
				var show = visibleSides.contains(i);
				button.visible = show;
				button.active = show;
				if (show) rows.add(new CellRow(ROW_H, List.of(button)));
			}
			groupGrid.rows(rows);
		}
		var need = visible * cols();
		while (cells.size() < need) addCell(cells.size());
		for (var i = 0; i < cells.size(); i++) {
			var cell = cells.get(i);
			var show = i < need;
			cell.visible = show;
			cell.active = show;
		}
		var gridRows = new ArrayList<Row>();
		for (var i = 0; i < need; i += cols()) gridRows.add(new CellRow(ROW_H, cells.subList(i, Math.min(i + cols(), need))));
		grid.rows(gridRows);
		if (!preview) return;
		while (previews.size() < visible) addPreview(previews.size());
		for (var i = 0; i < previews.size(); i++) {
			var button = previews.get(i);
			var show = i < visible;
			button.visible = show;
			button.active = show;
			button.setX(x + PAD + sidebarW + groupBarW + PAD);
			button.setY(listTop() + i * ROW_H);
			button.setWidth(PREVIEW_W);
			button.setHeight(ROW_H);
		}
	}
	/** 分组按钮：侧栏为整行分类名，横排为图标；选中或按下均高亮，悬停铺灰底。 */
	private void renderGroup(LogicCellButton cell, int index, GuiGraphics gui, boolean hovered, boolean pressed) {
		var gx = cell.getX();
		var gy = cell.getY();
		var gw = cell.getWidth();
		var gh = cell.getHeight();
		if (hovered) LogicCursor.setHand();
		var active = index == selected || pressed;
		if (sidebar) {
			if (active) gui.fill(gx, gy, gx + gw, gy + gh, ACCENT);
			else if (hovered) gui.fill(gx, gy, gx + gw, gy + gh, HOVER);
			LogicFont.drawOutlinedCentered(gui, LogicFont.text(groupLabel(groups.get(index))), gx + gw / 2, gy + (gh - 8) / 2, TEXT);
			return;
		}
		// 不铺 WHITE_PANE：其边距 12 在 20 高按钮上仅 0.33 倍，白边不可见
		if (active) outline(gui, gx, gy, gw, GROUP_H, GROUP_BORDER, ACCENT);
		else if (hovered) gui.fill(gx, gy, gx + gw, gy + GROUP_H, HOVER);
		var icon = iconOf(groups.get(index).icon());
		if (icon != null) icon.render(gui, Math.round(gx + gw / 2F - icon.width() / 2F), LogicIcons.centerY(gy, GROUP_H), TEXT);
		else LogicFont.drawOutlinedCentered(gui, LogicFont.text(groupLabel(groups.get(index))), gx + gw / 2, gy + (GROUP_H - 8) / 2, TEXT);
	}
	/** 双维的左侧分类：整行分类名，选中或按下均高亮，悬停铺灰底。 */
	private void renderSide(LogicCellButton cell, int index, GuiGraphics gui, boolean hovered, boolean pressed) {
		var gx = cell.getX();
		var gy = cell.getY();
		var gw = cell.getWidth();
		var gh = cell.getHeight();
		if (hovered) LogicCursor.setHand();
		var active = index == selectedSide || pressed;
		if (active) gui.fill(gx, gy, gx + gw, gy + gh, ACCENT);
		else if (hovered) gui.fill(gx, gy, gx + gw, gy + gh, HOVER);
		LogicFont.drawOutlinedCentered(gui, LogicFont.text(groupLabel(sideGroups.get(index))), gx + gw / 2, gy + (gh - 8) / 2, TEXT);
	}
	private int headerH() {
		return sidebar || groups.size() <= 1 ? 0 : GROUP_H;
	}
	private boolean iconGroup() {
		if (groups.isEmpty()) return false;
		return switch (groups.get(selected).icon()) {
			case "box", "block", "liquid", "effect", "char" -> true;
			default -> false;
		};
	}
	private int rows() {
		return (filtered.size() + cols() - 1) / cols();
	}
	/** 按过滤后的结果决定滚动条留位会使弹窗宽度跳动，故用整组不过滤时的行数。 */
	private int rowsFull() {
		return (base.size() + cols() - 1) / cols();
	}
	/** 分组时由组自定，否则用 {@link Picker#cols()}；上限须按整组选项数钳制。 */
	private int cols() {
		var want = groups.isEmpty() ? picker.cols() : groups.get(selected).cols();
		return Math.clamp(want, 1, Math.max(1, base.size()));
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
		updateScrollbars();
		// 必须是不透明实心底，否则会透出后方的卡片与世界
		LogicGuiTextures.PANE_SOLID.render(gui, x, y, width, height);
		if (!sidebar) for (var button : groupButtons) if (button.visible) button.render(gui, mouseX, mouseY, partialTick);
		if (sidebar || hasSide()) groupGrid.render(gui, mouseX, mouseY, partialTick);
		for (var button : previews) if (button.visible) button.render(gui, mouseX, mouseY, partialTick);
		if (search != null) search.render(gui, mouseX, mouseY, partialTick);
		hoveredTooltip = null;
		grid.render(gui, mouseX, mouseY, partialTick);
		renderScrollbars(gui, mouseX, mouseY, partialTick);
		LogicTooltip.render(gui, hoveredTooltip, mouseX, mouseY, parent.width, parent.height);
	}
	/** 关闭后回到编辑器，不走 {@code Screen} 默认的弹出界面栈。 */
	@Override
	public void onClose() {
		// 记录本次的浏览状态，下次打开时恢复
		picker.lastGroup = selected;
		picker.lastSideGroup = selectedSide;
		picker.lastScroll = scrollbar.scroll();
		picker.lastQuery = search == null ? "" : search.getValue();
		stopPreview();
		mc.setScreen(parent);
	}
	@Override
	public void tick() {
		super.tick();
		// 编辑界面在单人游戏里按暂停界面处理，声音引擎的 tick 不会推进，
		// 已播完的试听实例会一直被视为活跃；试听期间手动推进一次
		if (previewing != null && mc.isPaused()) mc.getSoundManager().tick(false);
	}
	@Override
	public void removed() {
		super.removed();
		stopPreview();
	}
	@Override
	public void resize(Minecraft minecraft, int width, int height) {
		super.resize(minecraft, width, height);
		parent.resize(minecraft, width, height);
		relayout();
	}
	/** 窗口尺寸变化会重建控件，故搜索框在此登记而非构造器中。 */
	@Override
	protected void init() {
		super.init();
		// 窗口尺寸变化会清空控件表，构造期创建的格控件在此重新登记
		for (var button : groupButtons) addWidget(button);
		for (var button : sideButtons) addWidget(button);
		for (var button : previews) addWidget(button);
		for (var cell : cells) addWidget(cell);
		widgetsReady = true;
		if (search == null) return;
		addRenderableWidget(search);
		setInitialFocus(search);
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!inBounds(mouseX, mouseY)) return true;
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
	/** 在 {@code (x,y)} 铺一个物品或流体图标；未铺时由调用方退回画文字。 */
	private boolean renderIcon(GuiGraphics gui, String option, int x, int y) {
		if (!iconGroup() || !option.startsWith("@")) return false;
		var id = ResourceLocation.tryParse(option.substring(1));
		if (id == null) return false;
		if ("box".equals(groups.get(selected).icon())) {
			if (!BuiltInRegistries.ITEM.containsKey(id)) return false;
			gui.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(id)), x, y);
			return true;
		}
		if ("block".equals(groups.get(selected).icon())) {
			if (!BuiltInRegistries.BLOCK.containsKey(id)) return false;
			var item = BuiltInRegistries.BLOCK.get(id).asItem();
			if (item != Items.AIR) {
				gui.renderItem(new ItemStack(item), x, y);
				return true;
			}
			// 没有物品形态的方块（水、火焰等）用模型粒子贴图，模型缺失时退回文字
			var model = mc.getBlockRenderer().getBlockModel(BuiltInRegistries.BLOCK.get(id).defaultBlockState());
			if (model == mc.getModelManager().getMissingModel()) return false;
			gui.blit(x, y, 0, ICON_W, ICON_W, model.getParticleIcon(ModelData.EMPTY));
			return true;
		}
		if ("effect".equals(groups.get(selected).icon())) {
			if (!BuiltInRegistries.MOB_EFFECT.containsKey(id)) return false;
			// 状态效果图标是独立贴图，不在图集里；缺失时退回文字
			var texture = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/mob_effect/" + id.getPath() + ".png");
			if (mc.getResourceManager().getResource(texture).isEmpty()) return false;
			gui.blit(texture, x, y, 0, 0, ICON_W, ICON_W, ICON_W, ICON_W);
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
		if (LAccess.isControl(option)) return LogicFont.tip(LAccess.controlTipKey(option));
		if (!option.startsWith("@")) return null;
		var name = option.substring(1);
		// 内置属性返回说明文案而非列表中的名字，后者已显示在按钮上；方块属性没有说明
		if (LAccess.byName(name) instanceof LAccess access) return access.blockProperty() ? null : LogicFont.tip(access.tipKey());
		if (!iconGroup()) return null;
		var id = ResourceLocation.tryParse(name);
		if (id == null) return null;
		if ("block".equals(groups.get(selected).icon()))
			return BuiltInRegistries.BLOCK.containsKey(id) ? onLogicFont(BuiltInRegistries.BLOCK.get(id).getName()) : null;
		if ("effect".equals(groups.get(selected).icon())) {
			var effect = BuiltInRegistries.MOB_EFFECT.get(id);
			return effect == null ? null : onLogicFont(effect.getDisplayName());
		}
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
		return super.mouseClicked(mouseX, mouseY, button);
	}
	private void switchGroup(int group) {
		stopPreview();
		selected = group;
		refreshBase();
		refilter();
		scrollbar.reset();
		bar(1).reset();
		relayout();
		LogicSounds.button();
	}
	/** 切换左侧分类只影响交集，行数与宽度都要重算。 */
	private void switchSide(int side) {
		if (!visibleSides.contains(side)) return;
		stopPreview();
		selectedSide = side;
		refreshBase();
		refilter();
		scrollbar.reset();
		relayout();
		LogicSounds.button();
	}
	private int listTop() {
		return searchY() + (searchable ? LogicSearchBox.HEIGHT + PAD : 0);
	}
	private boolean inBounds(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
