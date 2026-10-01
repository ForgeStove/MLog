package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.Table.OptionGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/**
 * 卡片参数区的一个元素。
 * <p>布局与事件由 {@link LogicCanvas} 自行管理，不挂在 {@code Screen} 的控件列表上，
 * 这样滚动时能精确控制位置与 z 序，也不会出现控件移出可滚动区后仍响应的问题。
 * <p>参数不是完整描边框，而是一条下划线加文字。
 */
@OnlyIn(Dist.CLIENT)
public abstract class ParamElement {
	/** 参数行统一高度。 */
	public static final int SIZE = 16;
	/** 框内文字与两端的水平间距。 */
	public static final int PAD = 4;
	public int x, y;
	/** 底条用的语句类别色。 */
	protected int color = TEXT;
	/** @return 宽度是否由所在行的剩余空间决定，而不是自身固定。 */
	public boolean stretch() {
		return false;
	}
	/** 设定弹性元素的实际宽度，非弹性元素忽略。 */
	public void setWidth(int width) {}
	public abstract void render(GuiGraphics gui, int mouseX, int mouseY);
	/** 同步外部改动的值，被聚焦的输入框除外。 */
	public void sync() {}
	/** 失去焦点。 */
	public void unfocus() {}
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return false;
	}
	public boolean charTyped(char codePoint, int modifiers) {
		return false;
	}
	public void setPosition(int x, int y) {
		this.x = x;
		this.y = y;
	}
	public boolean isOver(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width() && mouseY >= y && mouseY < y + SIZE;
	}
	public abstract int width();
	/** @return 悬停提示的 key，无则为 {@code null}。 */
	public @Nullable String tipKey() {
		return null;
	}
	/**
	 * 绘制参数框底部的类别色条，返回文字基线的 y。
	 * <p>色条即为语句类别色，与非聚焦状态相同。两端均顶到控件边缘，不留空档。
	 */
	protected int renderUnderline(GuiGraphics gui, int w) {
		var h = LogicGuiTextures.UNDERLINE_H;
		LogicGuiTextures.UNDERLINE.renderTinted(gui, x, y + SIZE - h, w, h, color);
		return y + SIZE / 2 - 4;
	}
	/** 撑开剩余宽度的占位元素，将其后的元素推到卡片右侧。 */
	public static class Spacer extends ParamElement {
		@Override
		public int width() {
			return 0;
		}
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {}
	}
	/** 不可编辑的文本片段，如 {@code " = "}。 */
	public static class Label extends ParamElement implements Table.Label {
		private final Component text;
		/** 词表里的 token，悬停提示按它拼 key。 */
		private final String token;
		private @Nullable String tipKey;
		public Label(Component text, int color, String token) {
			this.text = text;
			this.color = color;
			this.token = token;
		}
		@Override
		public String token() {
			return token;
		}
		@Override
		public void setTipKey(String key) {
			tipKey = key;
		}
		@Override
		public @Nullable String tipKey() {
			return tipKey;
		}
		@Override
		public int width() {
			return mc.font.width(text) + PAD * 2;
		}
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {
			LogicFont.draw(gui, text, x + PAD, y + SIZE / 2 - 4, color);
		}
	}
	/** 可编辑的文本参数，内嵌原版输入框，只画下划线。 */
	public static class Field extends ParamElement {
		/** 弹性字段布局前的占位宽度，见构造器里的说明。 */
		private static final int PLACEHOLDER_W = 1024;
		private final Supplier<String> get;
		private final LogicEditBox box;
		/** 宽度为 {@code Table#STRETCH} 时由所在行的剩余空间决定。 */
		private final boolean stretch;
		private int width;
		public Field(Supplier<String> get, Consumer<String> set, int width, int color) {
			this.get = get;
			this.color = color;
			stretch = width < 0;
			this.width = stretch ? 0 : width;
			// 弹性字段的真实宽度要等布局后才确定。此处先给一个大值：setValue 会按当前宽度计算显示起点，
			// 若按 0 宽计算，EditBox 会把显示位置推到文本末尾，之后撑开也不会重算，前面的字便会看不见。
			box = new LogicEditBox(stretch ? PLACEHOLDER_W : this.width - PAD * 2, SIZE, Component.empty());
			box.setBordered(false);
			box.setMaxLength(64);
			box.setAccent(color);
			box.setValue(get.get());
			box.setResponder(set);
		}
		@Override
		public int width() {
			return width;
		}
		@Override
		public boolean stretch() {
			return stretch;
		}
		@Override
		public void setWidth(int width) {
			if (!stretch) return;
			this.width = width;
			box.setWidth(Math.max(0, width - PAD * 2));
		}
		@Override
		public void sync() {
			if (box.isFocused()) return;
			var value = get.get();
			if (!value.equals(box.getValue())) box.setValue(value);
		}
		@Override
		public void unfocus() {
			box.setFocused(false);
		}
		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			return box.isFocused() && box.keyPressed(keyCode, scanCode, modifiers);
		}
		@Override
		public boolean charTyped(char codePoint, int modifiers) {
			return box.isFocused() && box.charTyped(codePoint, modifiers);
		}
		@Override
		public void setPosition(int x, int y) {
			super.setPosition(x, y);
			box.setX(x + PAD);
			box.setY(y + SIZE / 2 - 4);
		}
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {
			// 文本光标由 LogicEditBox 自行处理
			renderBox(gui, mouseX, mouseY);
			renderUnderline(gui, width);
		}
		/** 只绘制输入框本身，不含底条。供 {@link Select} 使用，它要与右侧按钮共用一条底条。 */
		public void renderBox(GuiGraphics gui, int mouseX, int mouseY) {
			box.render(gui, mouseX, mouseY, 0F);
		}
		/** 聚焦并将光标移到点击位置，从该处开始编辑。 */
		public void focusAt(double mouseX, double mouseY) {
			box.setFocused(true);
			box.mouseClicked(mouseX, mouseY, 0);
		}
		/** 拖拽选择文本，纵向不参与选择。 */
		public void dragTo(double mouseX) {
			box.dragSelectTo(mouseX);
		}
	}
	/** 取值从固定列表里挑的参数，点击弹出选项列表。 */
	public abstract static class Picker extends ParamElement {
		public final Supplier<String> get;
		public final Consumer<String> set;
		public final Supplier<List<String>> options;
		/** 选项分组；为空表示只有一组，即 {@link #options}。 */
		public final List<OptionGroup> groups;
		/** 取值到显示名的映射，为 {@code null} 时直接显示取值。 */
		private final @Nullable Function<String, String> display;
		/** 上次弹出时的分组、滚动位置与搜索词，再次打开时恢复。 */
		public int lastGroup;
		public double lastScroll;
		public String lastQuery = "";
		protected Picker(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			List<OptionGroup> groups,
			@Nullable Function<String, String> display
		) {
			this.get = get;
			this.set = set;
			this.options = options;
			this.groups = groups;
			this.display = display;
		}
		/**
		 * 接过另一个控件的记忆。
		 * <p>选中一个值后卡片会重建全部控件（算子等可能改变参数个数），
		 * 新控件须接过这份记忆，否则每次选完再打开都会回到第一组、滚回顶部。
		 */
		public void adopt(Picker other) {
			lastGroup = other.lastGroup;
			lastScroll = other.lastScroll;
			lastQuery = other.lastQuery;
		}
		/** @return 取值用于显示的文字。 */
		public String display(String value) {
			return display == null ? value : display.apply(value);
		}
		/** @return 触发它的按钮中心。选项列表以此居中。 */
		public abstract int anchorCenter();
		/** @return 弹出的选项列表每行的列数，默认单列。 */
		public int cols() {
			return 1;
		}
		/** @return 弹窗顶部是否显示搜索框；分组列表默认为是，长列表由语句声明。 */
		public boolean searchable() {
			return !groups.isEmpty();
		}
		/** @return 选项的悬停提示 key，没有提示时返回 {@code null}。 */
		public @Nullable String tipKey(String name) {
			return null;
		}
	}
	/**
	 * 固定取值的参数：左边是可自由输入的文本框，右边一个方形按钮点开选项列表。
	 * <p>既能从列表中选择，也能手动输入列表之外的值（如自定义的方块状态属性名）。
	 */
	public static class Select extends FieldButton {
		public Select(
			Supplier<String> get,
			Consumer<String> set,
			List<OptionGroup> groups,
			@Nullable Function<String, String> display,
			int width,
			int color
		) {
			// 选项按组取，options 仅为占位，指向第一组
			super(get, set, groups.getFirst().options(), groups, display, width, color);
		}
	}
	/**
	 * 输入框与方形编辑按钮的公共部分。
	 * <p>按钮打开什么由子类决定：{@link Select} 弹出选项列表，{@link Color} 弹出取色器；
	 * 铅笔的绘制与「左半为输入、右半为按钮」的判定只在此处实现一次。
	 */
	public abstract static class FieldButton extends Picker {
		/**
		 * 铅笔图标的宽度，由图标字体的 30 单位折算得 12。
		 * <p>图标字体的字号是全局的（{@code icons.json} 的 {@code size}），修改它会影响所有图标，
		 * 因此这里单独缩放铅笔。
		 */
		private static final float PENCIL_W = 6;
		/** 左边的输入框。 */
		public final Field input;
		protected FieldButton(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			List<OptionGroup> groups,
			@Nullable Function<String, String> display,
			int width,
			int color
		) {
			super(get, set, options, groups, display);
			this.color = color;
			input = new Field(get, set, width - SIZE, color);
		}
		@Override
		public int anchorCenter() {
			return buttonX() + SIZE / 2;
		}
		/** @return 右侧编辑按钮的左边缘。 */
		public int buttonX() {
			return x + input.width();
		}
		@Override
		public void setPosition(int x, int y) {
			super.setPosition(x, y);
			input.setPosition(x, y);
		}
		@Override
		public void sync() {
			input.sync();
		}
		@Override
		public void unfocus() {
			input.unfocus();
		}
		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			return input.keyPressed(keyCode, scanCode, modifiers);
		}
		@Override
		public boolean charTyped(char codePoint, int modifiers) {
			return input.charTyped(codePoint, modifiers);
		}
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {
			input.renderBox(gui, mouseX, mouseY);
			var bx = buttonX();
			var onButton = isOnButton(mouseX, mouseY);
			// 右侧按钮显示手型；左侧输入区的文本光标由 LogicEditBox 处理
			if (onButton) LogicCursor.setHand();
			// 先绘底条，再叠上悬停底色：按钮绘制在其后，会连同底条一并遮挡
			renderUnderline(gui, width());
			if (onButton) gui.fill(bx, y, bx + SIZE, y + SIZE, flatOver(color));
			// 围绕控件中心缩放：缩放定点与居中基准为同一点，缩小后字形中心不会偏移
			var scale = PENCIL_W / LogicIcons.PENCIL.width();
			var pose = gui.pose();
			pose.pushPose();
			pose.translate(bx + SIZE / 2F, y + SIZE / 2F, 0F);
			pose.scale(scale, scale, 1F);
			pose.translate(-(bx + SIZE / 2F), -(y + SIZE / 2F), 0F);
			LogicIcons.PENCIL.render(gui, bx + (SIZE - LogicIcons.PENCIL.width()) / 2, LogicIcons.centerY(y, SIZE), TEXT);
			pose.popPose();
		}
		/** @return 点是否落在右侧编辑按钮上，落在左侧则交由输入框。 */
		public boolean isOnButton(double mouseX, double mouseY) {
			return isOver(mouseX, mouseY) && mouseX >= buttonX();
		}
		@Override
		public int width() {
			return input.width() + SIZE;
		}
	}
	/** 颜色参数：输入框与铅笔，铅笔打开 {@link ColorPickerDialog} 取色器。 */
	public static class Color extends FieldButton {
		public Color(Supplier<String> get, Consumer<String> set, int width, int color) {
			super(get, set, List::of, List.of(), null, width, color);
		}
	}
	/**
	 * 只能从列表中选择的参数：整个控件即一个按钮，没有输入框。
	 * <p>点击直接弹出 {@link OptionPopupScreen}。
	 */
	public static class Option extends Picker {
		private final int width, cols;
		/** 弹窗顶部的搜索框，见 {@link Picker#searchable()}。 */
		private final boolean search;
		/** 选项名到提示 key，整个字段都不给提示时为 {@code null}。 */
		private final @Nullable Function<String, String> tipKey;
		public Option(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			@Nullable Function<String, String> display,
			@Nullable Function<String, String> tipKey,
			int width,
			int color,
			int cols,
			boolean search
		) {
			super(get, set, options, List.of(), display);
			this.width = width;
			this.cols = cols;
			this.search = search;
			this.tipKey = tipKey;
			this.color = color;
		}
		@Override
		public @Nullable String tipKey(String name) {
			return tipKey == null ? null : tipKey.apply(name);
		}
		@Override
		public boolean searchable() {
			return search;
		}
		@Override
		public int width() {
			return width;
		}
		@Override
		public int anchorCenter() {
			return x + width / 2;
		}
		@Override
		public int cols() {
			return cols;
		}
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {
			// 常态为一条下划线，悬停时才铺底色。
			// 先绘底条，再叠上悬停底色，会连同底条一并遮挡
			var textY = renderUnderline(gui, width);
			if (isOver(mouseX, mouseY)) {
				LogicCursor.setHand();
				gui.fill(x, y, x + width, y + SIZE, flatOver(color));
			}
			LogicFont.drawCentered(gui, LogicFont.text(display(get.get())), x + width / 2, textY, TEXT);
		}
	}
	/** {@code jump} 的跳转节点，由画布负责拖拽连线。 */
	public static class Node extends ParamElement {
		/** 三角尖端相对图标边长的位置，取自纹理中的尖角。 */
		public static final float TIP = 0.89F;
		/**
		 * 图标再向右探出的距离。节点是参数行的最后一个元素，其右边距之外只剩卡片内边距
		 * （{@link StatementCard} 的 {@code PAD}），探出这么多恰好使图标贴住卡片右边缘。
		 */
		private static final int OVERHANG = 6;		/** 图标在参数行中的内边距，边长与三角尖端的位置都由它推出。 */
		public static final int INSET = 2, ICON = SIZE - INSET * 2;
		/** 图标左边缘相对节点元素左边缘的偏移。 */
		public static final int ICON_X = SIZE + OVERHANG - ICON;
		public final Supplier<MLogStatement> get;
		public final Consumer<MLogStatement> set;
		public Node(Supplier<MLogStatement> get, Consumer<MLogStatement> set) {
			this.get = get;
			this.set = set;
		}
		@Override
		public int width() {
			return SIZE;
		}
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {
			// 节点恒为白色，悬停时才变为强调色并显示手型。
			// 图标为正方形，宽高须一致，否则会被压扁
			var over = isOver(mouseX, mouseY);
			if (over) LogicCursor.setHand();
			LogicGuiTextures.LOGIC_NODE.renderTinted(gui, x + ICON_X, y + INSET, ICON, ICON, over ? PLACE : TEXT);
		}
		/** 命中的是图标本身。它比节点元素向右探出一截，按元素边界判定会漏掉右半部分。 */
		@Override
		public boolean isOver(double mouseX, double mouseY) {
			return mouseX >= x + ICON_X && mouseX < x + ICON_X + ICON && mouseY >= y + INSET && mouseY < y + INSET + ICON;
		}

	}
}
