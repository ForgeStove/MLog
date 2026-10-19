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
	/** 物品/流体按钮中图标的边长，同时也是这两组的按钮宽度。 */
	private static final int ICON_W = 16;
	/** 试听按钮与选项行同高的正方形，对应 MDT 的 40×40。 */
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
	/** 是否显示搜索框，取自 {@link Picker#searchable()}。 */
	private final boolean searchable;
	/** 分组为左侧分类列；单组时无分类可列，仍按横排处理。 */
	private final boolean sidebar;
	/** 分类列与选项列之间显示试听列，仅侧栏模式下有意义。 */
	private final boolean preview;
	/** 分组按钮、试听按钮与选项格控件。 */
	private final List<LogicCellButton> groupButtons = new ArrayList<>(), previews = new ArrayList<>(), cells = new ArrayList<>();
	private final ClippedCellGrid grid = new ClippedCellGrid(scrollbar);
	/** 正在试听的实例及其对应的选项名；再次点击同一行即停止。 */
	private @Nullable SoundInstance previewing;
	private @Nullable String previewed;
	/** 当前分组过滤搜索词后的选项；随搜索词变化重算，不放在每帧渲染里。 */
	private List<String> filtered;
	private @Nullable LogicSearchBox search;
	/** 首次 init 之前创建的格控件不直接登记，由 init 统一登记；此后新建的即时登记。 */
	private boolean widgetsReady;
	/** 尺寸与位置随分组重算：各组选项数与最宽项数量级相差很大，弹窗长宽随之变化。 */
	private int colW, sidebarW, groupH = ROW_H;
	private int x, y, width, height, visible;
	private int selected;
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
		searchable = picker.searchable();
		sidebar = picker.sidebar && groups.size() > 1;
		preview = picker.preview && sidebar;
		// 恢复上次的位置：分组与滚动位置都存在 Picker 上，同一参数控件关闭再打开时保持原状
		selected = groups.isEmpty() ? 0 : Math.clamp(picker.lastGroup, 0, groups.size() - 1);
		filtered = groupOptions.get(selected);
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
	/** 选项所属分类，规则与 {@code SenseNames.groups} 相同。 */
	private static String categoryOf(String option) {
		var name = option.startsWith("@") ? option.substring(1) : option;
		var id = ResourceLocation.tryParse(name);
		if (id == null) return "other";
		if (!ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace())) return id.getNamespace();
		var path = id.getPath();
		var dot = path.indexOf('.');
		return dot < 0 ? "other" : path.substring(0, dot);
	}
	/** 分类名依次查 {@code soundcategory.mlog}、ModMenu 的模组名翻译、模组显示名，最后原样显示。 */
	private static String groupLabel(OptionGroup group) {
		var label = group.label();
		if (label.isEmpty()) return "";
		var language = Language.getInstance();
		var key = "mlog.soundcategory." + label;
		if (language.has(key)) return Component.translatable(key).getString();
		// NeoForge 的模组显示名不带本地化，ModMenu 的键是模组界面通用的翻译约定
		var modmenu = "modmenu.nameTranslation." + label;
		if (language.has(modmenu)) return Component.translatable(modmenu).getString();
		return ModList.get().getModContainerById(label).map(mod -> mod.getModInfo().getDisplayName()).orElse(label);
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
	/** 具体分类只显示条目名；「全部」按 MDT 的格式显示为「分类/条目」，分类名走本地化。 */
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
			for (var option : groupOptions.get(selected)) textW = Math.max(textW, LogicFont.width(LogicFont.text(display(option))));
			colW = textW + CELL_PAD * 2;
		}
		// 分类列宽取最长的本地化组名
		if (sidebar) {
			sidebarW = 0;
			for (var group : groups)
				sidebarW = Math.max(sidebarW, LogicFont.width(LogicFont.text(groupLabel(group))));
			sidebarW += CELL_PAD * 2;
		}
		// 至少显示一行；屏幕太矮时 Math.clamp 会因为上界小于下界而抛异常
		var maxRows = Math.max(1, (parent.height - PAD * 2 - extra) / ROW_H);
		visible = Math.clamp(Math.max(1, rows()), 1, maxRows);
		var viewH = visible * ROW_H;
		// 分类列可能比选项列表长；放不下时压缩行高，避免分类按钮画出弹窗
		if (sidebar) {
			groupH = Math.clamp(maxRows * ROW_H / groups.size(), 8, ROW_H);
			viewH = Math.max(viewH, groups.size() * groupH);
		}
		// 滚动条的留位按整组计算，不受过滤结果影响，否则宽度仍会跳动
		var scrollable = rowsFull() > visible;
		var sideW = sidebar ? sidebarW + PAD : 0;
		// 试听列夹在分类列与选项列之间，滚动条仍按整组计算
		var previewW = preview ? PREVIEW_W + PAD : 0;
		// 不滚动时不为滚动条留位，否则右侧会多出一条空档
		width = Math.min(colW * cols() + sideW + previewW + PAD * 2 + (scrollable ? ScrollBar.WIDTH : 0), parent.width);
		height = viewH + PAD * 2 + extra;
		// 以触发它的按钮为中心；越出屏幕时钳回屏内
		var centerX = picker.anchorCenter();
		var centerY = picker.y + ParamElement.SIZE / 2;
		x = Math.clamp(centerX - width / 2, 0, Math.max(0, parent.width - width));
		y = Math.clamp(centerY - height / 2, 0, Math.max(0, parent.height - height));
		var contentW = width - PAD * 2 - sideW - previewW - (scrollable ? ScrollBar.WIDTH : 0);
		grid.area(x + PAD + sideW + previewW, listTop(), contentW, viewH, barX(), colW, rows() * ROW_H);
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
	/** 试听列与选项行一一对应，滚动时按当前可见行取选项；高亮与 MDT 的 {@code cleari} 一致：悬停灰底、按下强调色内边框。 */
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
		var gw = groups.isEmpty() ? 0 : width / groups.size();
		for (var i = 0; i < groupButtons.size(); i++) {
			var button = groupButtons.get(i);
			var show = groups.size() > 1;
			button.visible = show;
			button.active = show;
			if (sidebar) {
				button.setX(x + PAD);
				button.setY(listTop() + i * groupH);
				button.setWidth(sidebarW);
				button.setHeight(groupH);
				continue;
			}
			button.setX(x + i * gw);
			button.setY(y + PAD);
			button.setWidth(gw);
			button.setHeight(GROUP_H);
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
			button.setX(x + PAD + sidebarW + PAD);
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
			LogicFont.drawOutlined(gui, LogicFont.text(groupLabel(groups.get(index))), gx + CELL_PAD, gy + (gh - 8) / 2, TEXT);
			return;
		}
		// 不铺 WHITE_PANE：其边距 12 在 20 高按钮上仅 0.33 倍，白边不可见
		if (active) outline(gui, gx, gy, gw, GROUP_H, GROUP_BORDER, ACCENT);
		else if (hovered) gui.fill(gx, gy, gx + gw, gy + GROUP_H, HOVER);
		var icon = iconOf(groups.get(index).icon());
		if (icon != null) icon.render(gui, gx + (gw - icon.width()) / 2, LogicIcons.centerY(gy, GROUP_H), TEXT);
	}
	private int headerH() {
		return sidebar || groups.size() <= 1 ? 0 : GROUP_H;
	}
	private boolean iconGroup() {
		if (groups.isEmpty()) return false;
		return switch (groups.get(selected).icon()) {
			case "box", "block", "liquid", "char" -> true;
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
		for (var button : groupButtons) if (button.visible) button.render(gui, mouseX, mouseY, partialTick);
		for (var button : previews) if (button.visible) button.render(gui, mouseX, mouseY, partialTick);
		if (search != null) search.render(gui, mouseX, mouseY, partialTick);
		hoveredTooltip = null;
		grid.render(gui, mouseX, mouseY, partialTick);
		scrollbar.render(gui, mouseX, mouseY, partialTick);
		LogicTooltip.render(gui, hoveredTooltip, mouseX, mouseY, parent.width, parent.height);
	}
	/** 关闭后回到编辑器，不走 {@code Screen} 默认的弹出界面栈。 */
	@Override
	public void onClose() {
		// 记录本次的浏览状态，下次打开时恢复
		picker.lastGroup = selected;
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
		// 内置属性返回说明文案而非列表中的名字，后者已显示在按钮上
		if (LAccess.byName(name) instanceof LAccess access) return LogicFont.tip(access.tipKey());
		if (!iconGroup()) return null;
		var id = ResourceLocation.tryParse(name);
		if (id == null) return null;
		if ("block".equals(groups.get(selected).icon()))
			return BuiltInRegistries.BLOCK.containsKey(id) ? onLogicFont(BuiltInRegistries.BLOCK.get(id).getName()) : null;
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
		// 收起搜索框焦点，点击控件时由容器重新聚焦
		if (search != null && !search.isMouseOver(mouseX, mouseY)) search.setFocused(false);
		return super.mouseClicked(mouseX, mouseY, button);
	}
	private void switchGroup(int group) {
		stopPreview();
		selected = group;
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
