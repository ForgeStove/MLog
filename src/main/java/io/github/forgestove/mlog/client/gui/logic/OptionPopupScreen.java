package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.logic.ParamElement.Picker;
import io.github.forgestove.mlog.logic.LayoutBuilder.OptionGroup;
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
 * <p>做成独立界面，对齐 CCG 的 {@code EnumDropdownScreen}：父界面自己画在下方当背景，
 * 鼠标与键盘由 MC 隔离，父界面不必再为它转发任何事件。
 * <p>列表紧贴触发它的参数框弹出，所以既不居中、也没有标题与按钮栏。
 * <p>选项按 {@link Picker#cols()} 分列铺开，对齐 Mindustry 的 {@code showSelect(..., cols, ...)}：
 * {@code jump} 的条件是三列。带分组的参数（如获取数据）在顶上多一排分组按钮，
 * 对应 Mindustry 的 {@code showSelectTable}。
 */
@OnlyIn(Dist.CLIENT)
public class OptionPopupScreen extends Screen {
	/** 选项行高，对齐 Mindustry 里那批 {@code size(40f)} 的按钮。 */
	private static final int ROW_H = 16, PAD = 2;
	/** 分组按钮行的高度。对齐 Mindustry 的 {@code .height(50f)}，按 0.4 折过来是 20。 */
	private static final int GROUP_H = 20;
	/** 物品/流体按钮里图标的边长，也是那两组的按钮宽度。 */
	private static final int ICON_W = 16;
	/** 分组按钮选中时那圈高亮的粗细。按 Mindustry 的 4 单位折过来约 1.6，取 2。 */
	private static final int GROUP_BORDER = 2;
	/**
	 * 每个选项按钮在文字两侧留出的宽度。
	 * <p>和面板内边距 {@link #PAD} 分开：那个管的是面板边缘到内容的距离，这个只影响按钮本身，
	 * 按钮比面板的留白宽松些才不显得挤。
	 */
	private static final int CELL_PAD = 6;
	private final MicroProcessorScreen parent;
	private final Picker picker;
	/** 选中后的回调，用于重建卡片控件（算子会改变参数个数）。 */
	private final Runnable onSelect;
	/** 选项分组；空表示只有一组，此时不画顶上那排按钮。 */
	private final List<OptionGroup> groups;
	/** 每组的选项，构造时取好——{@code Supplier} 可能要现算（物品表上千条），不能放在每帧的渲染里。 */
	private final List<List<String>> groupOptions;
	/** 滚动量、滑块、拖动、翻页与平滑都由它管，和主界面画布用的是同一套。 */
	private final ScrollBar scrollbar = new ScrollBar();
	/** 尺寸与位置。这些都要随分组重算——各组的选项数和最宽项差着数量级，弹窗得跟着长宽。 */
	private int colW;
	private int x, y, width, height, visible;
	/** 选项区的可视高度，滚动条按它算滑块。 */
	private int viewH;
	/** 选项是否超出一屏，也就是要不要显示滚动条、要不要给它在右侧留位。 */
	private boolean scrollable;
	/** 当前显示的分组。 */
	private int selected;
	public OptionPopupScreen(MicroProcessorScreen parent, Picker picker, Runnable onSelect) {
		super(Component.empty());
		this.parent = parent;
		this.picker = picker;
		this.onSelect = onSelect;
		groups = picker.groups;
		var cached = new ArrayList<List<String>>();
		if (groups.isEmpty()) cached.add(picker.options.get());
		else for (var group : groups) cached.add(group.options().get());
		groupOptions = List.copyOf(cached);
		relayout();
	}
	/**
	 * 按当前分组重算尺寸与位置。
	 * <p>对齐 Mindustry 的 {@code pack()}：那边切组会把弹窗重新打包，长宽跟着组走。
	 * 各组的选项数差着数量级，共用一套尺寸的话，短组会拖一大片空白、滚动条比例也不对。
	 */
	private void relayout() {
		// 分组按钮行占的高度，没有分组就是 0
		var headerH = groups.isEmpty() ? 0 : GROUP_H;
		var options = groupOptions.get(selected);
		// 物品/流体是纯图标按钮，宽度就按图标算——和 Mindustry 的 size(40f) 一样，不带文字
		if (iconGroup()) colW = ICON_W;
		else {
			var textW = 0;
			for (var option : options) textW = Math.max(textW, LogicFont.width(LogicFont.text(picker.display(option))));
			colW = textW + CELL_PAD * 2;
		}
		// 至少显示一行；屏幕太矮时 Math.clamp 会因为上界小于下界而抛异常
		visible = Math.clamp(Math.max(1, rows()), 1, Math.max(1, (parent.height - PAD * 2 - headerH) / ROW_H));
		viewH = visible * ROW_H;
		scrollable = rows() > visible;
		scrollbar.step(viewH * ScrollBar.WHEEL_RATIO);
		// 不滚动就不给滚动条留位，否则右边平白多出一条空档
		width = Math.min(colW * cols() + PAD * 2 + (scrollable ? ScrollBar.WIDTH : 0), parent.width);
		height = viewH + PAD * 2 + headerH;
		// 居中到触发它的那个按钮上，对齐 Mindustry 的 setPosition(..., Align.center)；
		// 越出屏幕就顺着推回来，相当于那边的 keepInStage()
		var centerX = picker.anchorCenter();
		var centerY = picker.y + ParamElement.SIZE / 2;
		x = Math.clamp(centerX - width / 2, 0, Math.max(0, parent.width - width));
		y = Math.clamp(centerY - height / 2, 0, Math.max(0, parent.height - height));
	}
	/** @return 当前分组是否用图标按钮，对应 Mindustry 里物品与流体那两张表。 */
	private boolean iconGroup() {
		if (groups.isEmpty()) return false;
		return switch (groups.get(selected).icon()) {
			case "box", "liquid" -> true;
			default -> false;
		};
	}
	/**
	 * @return 当前分组的行数。
	 * 	<p>各组的选项数差着数量级（属性几十条、物品上千条），滚动条的比例得按当前组算。
	 * 	沿用最长的那组，切到短组时滑块会缩成一小截、一滚就到底。
	 */
	private int rows() {
		return (groupOptions.get(selected).size() + cols() - 1) / cols();
	}
	/**
	 * @return 当前每行放几个。
	 * 	<p>分组时由组自己定——物品与流体是六列的图标墙，属性一列一条；不分组时沿用
	 *    {@link Picker#cols()}。最后钳到不超过选项数，免得只有两三条时空出一排。
	 */
	private int cols() {
		var want = groups.isEmpty() ? picker.cols() : groups.get(selected).cols();
		return Math.clamp(want, 1, Math.max(1, groupOptions.get(selected).size()));
	}
	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		var pose = gui.pose();
		pose.pushPose();
		pose.translate(0, 0, -100);
		var previous = mc.screen;
		mc.screen = parent;
		try {
			parent.render(gui, -1, -1, partialTick);
		} finally {
			// 父界面渲染若抛异常，mc.screen 会永久停在父界面上，那是很难排查的状态
			mc.screen = previous;
		}
		pose.popPose();
		scrollbar.update(viewH, rows() * ROW_H);
		// 必须是不透明实心底，否则会透出后面的卡片与世界
		LogicGuiTextures.PANE_SOLID.render(gui, x, y, width, height);
		if (!groups.isEmpty()) renderGroups(gui, mouseX, mouseY);
		var options = groupOptions.get(selected);
		var current = picker.get.get();
		var top = listTop();
		var contentX = x + PAD;
		var contentW = width - PAD * 2 - (scrollable ? ScrollBar.WIDTH : 0);
		var first = (int) (scrollbar.scroll() / ROW_H);
		gui.enableScissor(contentX, top, contentX + contentW, top + viewH);
		for (var i = 0; i < visible * cols(); i++) {
			var index = first * cols() + i;
			if (index >= options.size()) break;
			var option = options.get(index);
			var ox = contentX + i % cols() * colW;
			var oy = top + i / cols() * ROW_H;
			var hovered = mouseX >= ox && mouseX < ox + colW && mouseY >= oy && mouseY < oy + ROW_H;
			if (hovered) LogicCursor.setHand();
			// 对齐 Mindustry 的 Styles.logicTogglet：选中铺强调色底、悬停铺灰底，文字始终是白的
			if (option.equals(current)) gui.fill(ox, oy, ox + colW, oy + ROW_H, ACCENT);
			else if (hovered) gui.fill(ox, oy, ox + colW, oy + ROW_H, HOVER);
			// 物品/流体和 Mindustry 一样只铺图标，其余组画文字
			if (!renderIcon(gui, option, ox + (colW - ICON_W) / 2, oy))
				LogicFont.drawOutlinedCentered(gui, LogicFont.text(picker.display(option)), ox + colW / 2, oy + (ROW_H - 8) / 2, TEXT);
		}
		gui.disableScissor();
		scrollbar.render(gui, barX(), top, viewH, rows() * ROW_H);
	}
	/**
	 * 顶上那排分组按钮，各占等宽的一段。
	 * <p>对齐 Mindustry 的 {@code Styles.squareTogglei}：选中铺强调色底、悬停铺灰底，图标居中。
	 */
	private void renderGroups(GuiGraphics gui, int mouseX, int mouseY) {
		var gy = y + PAD;
		var gw = width / groups.size();
		for (var i = 0; i < groups.size(); i++) {
			var gx = x + i * gw;
			var hovered = mouseX >= gx && mouseX < gx + gw && mouseY >= gy && mouseY < gy + GROUP_H;
			if (hovered) LogicCursor.setHand();
			// 选中是一圈边框而不是整块底色，对齐 Mindustry 的 Styles.squareTogglei（checked = flatDown，
			// 那是张九宫格，只有边上有颜色）。悬停仍旧是平铺的灰底（over = flatOver）。
			// 手动描边而不是铺 WHITE_PANE：那张的九宫格边距是 12，压到 20 高的按钮上只剩 0.33 倍，
			// 纹理里那道白边会细到看不见
			if (i == selected) outline(gui, gx, gy, gw);
			else if (hovered) gui.fill(gx, gy, gx + gw, gy + GROUP_H, HOVER);
			var icon = iconOf(groups.get(i).icon());
			if (icon == null) continue;
			icon.render(gui, gx + (gw - icon.width()) / 2, LogicIcons.centerY(gy, GROUP_H), TEXT);
		}
	}
	/** @return 选项区的顶端，分组按钮行之下。 */
	private int listTop() {
		return y + PAD + (groups.isEmpty() ? 0 : GROUP_H);
	}
	/**
	 * 在 {@code (x,y)} 铺一个物品或流体图标。
	 *
	 * @return 是否铺了图标；没铺就由调用方退回画文字
	 */
	// @NotNull 在这条链上是假的：没注册客户端扩展的流体，getStillTexture 确实返回 null
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
		// 流体没有物品那样的模型，取它的静止贴图自己铺。和 CCG 的
		// ClientFluidListTooltipComponent.renderFluid 是同一套做法
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
	/** @return 滚动条的左边缘。 */
	private int barX() {
		return x + width - PAD - ScrollBar.WIDTH;
	}
	/** @return 分组图标，对应 Mindustry 的 {@code Icon.box / liquid / tree}。 */
	private static @Nullable LogicIcons iconOf(String name) {
		return switch (name) {
			case "box" -> LogicIcons.BOX;
			case "liquid" -> LogicIcons.LIQUID;
			case "tree" -> LogicIcons.TREE;
			default -> null;
		};
	}
	/** 在分组按钮的位置画一圈高亮轮廓，就是它的选中态。 */
	private static void outline(GuiGraphics gui, int x, int y, int w) {
		gui.fill(x, y, x + w, y + GROUP_BORDER, ACCENT);
		gui.fill(x, y + GROUP_H - GROUP_BORDER, x + w, y + GROUP_H, ACCENT);
		gui.fill(x, y + GROUP_BORDER, x + GROUP_BORDER, y + GROUP_H - GROUP_BORDER, ACCENT);
		gui.fill(x + w - GROUP_BORDER, y + GROUP_BORDER, x + w, y + GROUP_H - GROUP_BORDER, ACCENT);
	}
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!inBounds(mouseX, mouseY)) {
			onClose();
			return true;
		}
		// 顶上那排分组按钮在选项区之外，先判它
		if (!groups.isEmpty() && mouseY < listTop()) {
			var index = (int) ((mouseX - x) / (width / (double) groups.size()));
			if (index >= 0 && index < groups.size() && index != selected) {
				selected = index;
				scrollbar.reset();
				relayout();
				LogicSounds.button();
			}
			return true;
		}
		// 再给滚动条：点在它上面不该被当成选选项
		if (scrollbar.mousePressed(mouseX, mouseY, barX(), listTop(), viewH, rows() * ROW_H)) return true;
		var options = groupOptions.get(selected);
		var col = (int) ((mouseX - (x + PAD)) / colW);
		var row = (int) ((mouseY - listTop()) / ROW_H) + (int) (scrollbar.scroll() / ROW_H);
		if (col < 0 || col >= cols() || row < 0) return true;
		var index = row * cols() + col;
		if (index >= options.size()) return true;
		LogicSounds.button();
		picker.set.accept(options.get(index));
		onClose();
		onSelect.run();
		return true;
	}
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		return scrollbar.mouseDragged(mouseY, listTop(), viewH, rows() * ROW_H);
	}
	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		scrollbar.release();
		return super.mouseReleased(mouseX, mouseY, button);
	}
	/** 关闭后回到编辑器，而不是走 {@code Screen} 默认的弹出界面栈。 */
	@Override
	public void onClose() {
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
