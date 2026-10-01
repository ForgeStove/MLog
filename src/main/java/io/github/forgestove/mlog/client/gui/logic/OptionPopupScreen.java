package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Picker;
import io.github.forgestove.mlog.logic.*;
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
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 参数控件点开后弹出的选项列表。
 * <p>做成独立界面：父界面自行绘制在下方作为背景，
 * 鼠标与键盘由 MC 隔离，父界面不必再为它转发任何事件。
 * <p>列表紧贴触发它的参数框弹出，因此不居中，也没有标题栏与按钮栏。
 * <p>选项按 {@link Picker#cols()} 分列铺开，{@code jump} 的条件为三列。
 * 带分组的参数（如获取数据）在顶部多一行分组按钮。
 */
@OnlyIn(Dist.CLIENT)
public class OptionPopupScreen extends Screen {
	/** 选项行高，按 0.4 折算自 40。 */
	private static final int ROW_H = 16, PAD = 2;
	/** 分组按钮行的高度，按 0.4 折算自 50。 */
	private static final int GROUP_H = 20;
	/** 物品/流体按钮中图标的边长，同时也是这两组的按钮宽度。 */
	private static final int ICON_W = 16;
	/** 分组按钮选中时高亮边框的粗细。按 0.4 折算自 4 得 1.6，取 2。 */
	private static final int GROUP_BORDER = 2;
	/**
	 * 搜索行两侧的留白。
	 * <p>它比面板内边距 {@link #PAD} 大：那 2 像素是 {@code PANE_SOLID} 灰边的宽度，
	 * 贴边绘制放大镜会压在框线上。
	 */
	private static final int SEARCH_PAD = 4;
	/** 选项名到小写本地化名的缓存，见 {@link #localized}。 */
	private static final Map<String, String> LOCALIZED = new HashMap<>();
	/**
	 * 每个选项按钮在文字两侧留出的宽度。
	 * <p>与面板内边距 {@link #PAD} 分开：后者是面板边缘到内容的距离，前者只影响按钮本身，
	 * 按钮留白略宽于面板，避免拥挤。
	 */
	private static final int CELL_PAD = 6;
	/** {@link #LOCALIZED} 所对应的语言。语言变化时整体清空重填，空串表示尚未填充。 */
	private static String localizedLanguage = "";
	private final ProcessorScreen parent;
	private final Picker picker;
	/** 选中后的回调，用于重建卡片控件（算子会改变参数个数）。 */
	private final Runnable onSelect;
	/** 选项分组；只有一组时不绘制顶部分组按钮。 */
	private final List<OptionGroup> groups;
	/** 每组的选项，构造时即取好：{@code Supplier} 可能开销较大（物品表上千条），不能放在每帧的渲染里。 */
	private final List<List<String>> groupOptions;
	/** 弹窗顶部要不要搜索框，取参数的声明，见 {@link Picker#searchable()}。 */
	private final boolean searchable;
	/** 滚动量、滑块、拖动、翻页与平滑均由此处理，与主界面画布共用同一套。 */
	private final ScrollBar scrollbar = new ScrollBar();
	/** 当前分组过滤掉搜索词之后的选项。搜索词变化时重算，同样不放在每帧的渲染里。 */
	private List<String> filtered;
	private @Nullable LogicSearchBox search;
	/** 尺寸与位置。随分组重算：各组的选项数与最宽项数量级相差很大，弹窗长宽随之变化。 */
	private int colW;
	private int x, y, width, height, visible;
	/** 选项区的可视高度，滚动条按它算滑块。 */
	private int viewH;
	/** 选项是否超出一屏，即是否显示滚动条并在右侧为它留位。 */
	private boolean scrollable;
	/** 当前显示的分组。 */
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
		// 搜索框必须在 relayout 之前建立：relayout 按算出的弹窗位置摆放它，
		// 否则它会停在 (0,0)，光标落在屏幕左上角
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
	/**
	 * 搜索框在这里登记而不是构造器里。
	 * <p>窗口尺寸变化会重建控件。
	 */
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
	/** 按搜索框里的词过滤当前分组。空词表示不过滤。 */
	private void refilter() {
		var all = groupOptions.get(selected);
		var query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		if (query.isEmpty()) {
			filtered = all;
			return;
		}
		// 注册名与本地化名均可匹配：{@code iron} 与 {@code 铁锭} 都能搜到
		filtered = all.stream()
			.filter(option -> option.toLowerCase(Locale.ROOT).contains(query) || localized(option).contains(query))
			.toList();
	}
	/**
	 * 按当前分组重算尺寸与位置。
	 * <p>切换分组会重算弹窗长宽。
	 * 各组的选项数数量级相差很大，共用一套尺寸会使选项少的组留出大片空白、滚动条比例失真。
	 */
	private void relayout() {
		// 分组按钮行与搜索行占的高度
		var extra = headerH() + (searchable ? LogicSearchBox.HEIGHT + PAD : 0);
		// 物品/流体是纯图标按钮，宽度按图标计算，不含文字。
		// 文字组按整组的选项计算，不受搜索过滤影响，否则搜出一两个短名后弹窗会收缩
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
	/**
	 * @return 选项的小写本地化名，查不到（不是注册项）时返回空串。
	 * 	<p>物品与流体上千条，每次按键都查注册表开销过大，故做缓存。
	 * 	缓存是静态的、不随界面重建清空，因此每次进入先校验语言是否变化。
	 */
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
				// 内置属性同样匹配本地化名，如「总物品数」
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
	/** @return 分组按钮行占的高度，无分组时为 0。 */
	private int headerH() {
		return groups.size() <= 1 ? 0 : GROUP_H;
	}
	/** @return 当前分组是否用图标按钮。 */
	private boolean iconGroup() {
		if (groups.isEmpty()) return false;
		return switch (groups.get(selected).icon()) {
			case "box", "liquid", "char" -> true;
			default -> false;
		};
	}
	/**
	 * @return 当前分组的行数。
	 * 	<p>各组的选项数数量级相差很大（属性几十条、物品上千条），滚动条比例须按当前组计算。
	 * 	沿用最长的一组时，切到短组后滑块会缩成一小截，一滚即到底。
	 */
	private int rows() {
		return (filtered.size() + cols() - 1) / cols();
	}
	/**
	 * @return 整组不过滤时的行数，用于决定是否给滚动条留位。
	 * 	<p>与 {@link #cols()} 同理：若按过滤后的结果决定留位，弹窗宽度仍会跳动。
	 */
	private int rowsFull() {
		return (groupOptions.get(selected).size() + cols() - 1) / cols();
	}
	/**
	 * @return 当前每行的列数。
	 * 	<p>分组时由组自己定：物品与流体是六列的图标阵列，属性一列一条；不分组时沿用
	 *    {@link Picker#cols()}。上限按整组的选项数钳制，不受过滤结果影响，否则搜到两三条时
	 * 	列数下降，弹窗宽度随之收缩。
	 */
	private int cols() {
		var want = groups.isEmpty() ? picker.cols() : groups.get(selected).cols();
		return Math.clamp(want, 1, Math.max(1, groupOptions.get(selected).size()));
	}
	/** @return 搜索框的顶端。 */
	private int searchY() {
		return y + PAD + headerH();
	}
	/** @return 改用界面字体，其余样式（物品名自带的颜色）保留。 */
	private static Component onLogicFont(Component text) {
		return text.copy().withStyle(style -> style.withFont(LogicFont.ID));
	}
	/** @return 分组图标。 */
	private static @Nullable LogicIcons iconOf(String name) {
		return switch (name) {
			case "box" -> LogicIcons.BOX;
			case "liquid" -> LogicIcons.LIQUID;
			case "tree" -> LogicIcons.TREE;
			default -> null;
		};
	}
	/** 在指定矩形画一圈 {@code border} 像素粗的高亮轮廓。 */
	private static void outline(GuiGraphics gui, int x, int y, int w, int h, int border, int color) {
		gui.fill(x, y, x + w, y + border, color);
		gui.fill(x, y + h - border, x + w, y + h, color);
		gui.fill(x, y + border, x + border, y + h - border, color);
		gui.fill(x + w - border, y + border, x + w, y + h - border, color);
	}
	/**
	 * @return 当前分组是否为流体组。
	 * 	<p>单独成方法是因为其高亮画法与其余组不同：流体贴图整块不透明，
	 * 	铺在底层的高亮会被完全遮盖。
	 */
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
			// 选中铺强调色底、悬停铺灰底，文字始终为白色。
			// 流体组例外，高亮改画在图标之上，见循环之后
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
	/**
	 * 顶部的分组按钮行，各占等宽的一段。
	 * <p>选中铺强调色底、悬停铺灰底，图标居中。
	 */
	private void renderGroups(GuiGraphics gui, int mouseX, int mouseY) {
		// 只有一组时不绘制分组按钮，该行无可切换的内容
		if (groups.size() <= 1) return;
		var gy = y + PAD;
		var gw = width / groups.size();
		for (var i = 0; i < groups.size(); i++) {
			var gx = x + i * gw;
			var hovered = mouseX >= gx && mouseX < gx + gw && mouseY >= gy && mouseY < gy + GROUP_H;
			if (hovered) LogicCursor.setHand();
			// 选中为一圈边框而非整块底色（九宫格纹理仅边缘有着色），悬停仍为平铺灰底。
			// 手动描边而不铺 WHITE_PANE：其九宫格边距为 12，压到 20 高的按钮上只有 0.33 倍，
			// 纹理中的白边会细至不可见
			if (i == selected) outline(gui, gx, gy, gw, GROUP_H, GROUP_BORDER, ACCENT);
			else if (hovered) gui.fill(gx, gy, gx + gw, gy + GROUP_H, HOVER);
			var icon = iconOf(groups.get(i).icon());
			if (icon == null) continue;
			icon.render(gui, gx + (gw - icon.width()) / 2, LogicIcons.centerY(gy, GROUP_H), TEXT);
		}
	}
	/**
	 * 在 {@code (x,y)} 铺一个物品或流体图标。
	 *
	 * @return 是否铺了图标；没铺就由调用方退回画文字
	 */
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
	/**
	 * @return 选项的悬停提示。算子与跳转条件交由 {@link #enumTip}，其余按选项类型处理：
	 * 	物品/流体两组是纯图标按钮，列表不带文字，无提示时无法辨识。
	 */
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
	/**
	 * @return 选项的悬停提示，归属由字段指明（见 {@code Table#option}）。
	 * 	<p>提示按需提供：未编写说明的（加减乘、大小比较等）不显示提示。
	 */
	private @Nullable Component enumTip(String option) {
		var key = picker.tipKey(option);
		return key == null ? null : LogicFont.tip(key);
	}
	/** @return 滚动条的左边缘。 */
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
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (scrollbar.mouseDragged(mouseY)) return true;
		// 未拖动滚动条时转交控件，搜索框的框选由此接入
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}
	/** @return 选项区的顶端，分组按钮行与搜索行之下。 */
	private int listTop() {
		return searchY() + (searchable ? LogicSearchBox.HEIGHT + PAD : 0);
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		scrollbar.release();
		return super.mouseReleased(mouseX, mouseY, button);
	}
	/** 关闭后回到编辑器，而不是走 {@code Screen} 默认的弹出界面栈。 */
	@Override
	public void onClose() {
		// 记录本次的浏览状态，下次打开时恢复
		picker.lastGroup = selected;
		picker.lastScroll = scrollbar.scroll();
		picker.lastQuery = search == null ? "" : search.getValue();
		mc.setScreen(parent);
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!inBounds(mouseX, mouseY)) return true;
		scrollbar.wheel(-scrollY);
		return true;
	}
	/** @return 鼠标是否落在面板内。 */
	private boolean inBounds(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
