package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.logic.LStatement;
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
 * <p>布局与事件由 {@link LogicCanvas} 自己管理，不挂在 {@code Screen} 的控件列表上——
 * 这样滚动时能精确控制位置与 z 序，也不会出现控件跑到可滚动区外还在响应的问题。
 * <p>样式沿用 Mindustry：参数不是完整描边框，而是一条下划线加文字。
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
	/**
	 * 画参数框底部的类别色条，返回文字基线的 y。
	 * <p>对齐 Mindustry：就是一条语句类别色，聚焦与否都一样。两端都顶到控件边缘，不留空档。
	 */
	protected int renderUnderline(GuiGraphics gui, int w) {
		var h = LogicGuiTextures.UNDERLINE_H;
		LogicGuiTextures.UNDERLINE.renderTinted(gui, x, y + SIZE - h, w, h, color);
		return y + SIZE / 2 - 4;
	}
	/** 撑开剩余宽度的占位元素，把它后面的元素推到卡片右侧。 */
	public static class Spacer extends ParamElement {
		@Override
		public int width() {
			return 0;
		}
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {}
	}
	/** 不可编辑的文本片段，如 {@code " = "}。 */
	public static class Label extends ParamElement {
		private final Component text;
		public Label(Component text, int color) {
			this.text = text;
			this.color = color;
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
		/** 宽度为 {@code LayoutBuilder#STRETCH} 时由所在行的剩余空间决定。 */
		private final boolean stretch;
		private int width;
		public Field(Supplier<String> get, Consumer<String> set, int width, int color) {
			this.get = get;
			this.color = color;
			stretch = width < 0;
			this.width = stretch ? 0 : width;
			// 弹性字段的真正宽度要等布局才知道。先给个大值：setValue 会按当前宽度算显示起点，
			// 若按 0 宽算，EditBox 会把显示位置推到文本末尾，之后撑开也不会重算，前面的字就看不见了。
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
			// 文本光标由 LogicEditBox 自己处理
			renderBox(gui, mouseX, mouseY);
			renderUnderline(gui, width);
		}
		/** 只画输入框本身，不带底条。给 {@link Select} 用——它要和右侧按钮共用一条底条。 */
		public void renderBox(GuiGraphics gui, int mouseX, int mouseY) {
			box.render(gui, mouseX, mouseY, 0F);
		}
		public void focus() {
			box.setFocused(true);
		}
		/** 聚焦，并把光标移到点击的位置——对齐 Mindustry 的输入框，点哪就从哪编辑。 */
		public void focusAt(double mouseX, double mouseY) {
			box.setFocused(true);
			box.mouseClicked(mouseX, mouseY, 0);
		}
		/** 拖选文本。 */
		public void dragTo(double mouseX, double mouseY) {
			box.dragSelectTo(mouseX);
		}
	}
	/** 取值从固定列表里挑的参数，点击弹出选项列表。 */
	public abstract static class Picker extends ParamElement {
		public final Supplier<String> get;
		public final Consumer<String> set;
		public final Supplier<List<String>> options;
		/** 取值到显示名的映射，为 {@code null} 时直接显示取值。 */
		private final @Nullable Function<String, String> display;
		protected Picker(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			@Nullable Function<String, String> display
		) {
			this.get = get;
			this.set = set;
			this.options = options;
			this.display = display;
		}
		/** @return 取值用于显示的文字。 */
		public String display(String value) {
			return display == null ? value : display.apply(value);
		}
		/** @return 触发它的那个按钮的中心。选项列表按这里居中，对齐 Mindustry 的 {@code Align.center}。 */
		public abstract int anchorCenter();
		/** @return 弹出的选项列表每行放几个，默认单列。 */
		public int cols() {
			return 1;
		}
	}
	/**
	 * 固定取值的参数：左边是可自由输入的文本框，右边一个方形按钮点开选项列表。
	 * <p>对应 Mindustry 的 {@code field + button(Icon.pencilSmall)} 组合——
	 * 既能从列表里挑，也能手输列表之外的值（比如自定义的方块状态属性名）。
	 */
	public static class Select extends Picker {
		/** 左边的输入框。 */
		public final Field input;
		public Select(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			@Nullable Function<String, String> display,
			int width,
			int color
		) {
			super(get, set, options, display);
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
			// 右侧按钮给手型；左边输入区的文本光标由 LogicEditBox 处理
			if (onButton) LogicCursor.setHand();
			// 编辑按钮没有自己的底色，底条由整个控件统一画在下面；
			// 悬停底色对齐 Mindustry 的 Styles.logict（over = flatOver）
			if (onButton) gui.fill(bx, y, bx + SIZE, y + SIZE, FLAT_OVER);
			LogicIcons.PENCIL.render(gui, bx + (SIZE - LogicIcons.PENCIL.width()) / 2, LogicIcons.centerY(y, SIZE), TEXT);
			renderUnderline(gui, width());
		}
		/** @return 点是否落在右侧的编辑按钮上，落在左边则交给输入框。 */
		public boolean isOnButton(double mouseX, double mouseY) {
			return isOver(mouseX, mouseY) && mouseX >= buttonX();
		}
		@Override
		public int width() {
			return input.width() + SIZE;
		}
	}
	/**
	 * 只能从列表里挑的参数：整个控件就是一个按钮，没有输入框。
	 * <p>对应 Mindustry 的 {@code jump} 条件，点一下直接弹出 {@link OptionPopupScreen}。
	 */
	public static class Option extends Picker {
		private final int width, cols;
		public Option(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			@Nullable Function<String, String> display,
			int width,
			int color,
			int cols
		) {
			super(get, set, options, display);
			this.width = width;
			this.cols = cols;
			this.color = color;
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
			// 对齐 Mindustry 的 Styles.logict：常态就是一条下划线，和别处的参数一样，
			// 悬停才铺一层灰底
			if (isOver(mouseX, mouseY)) {
				LogicCursor.setHand();
				gui.fill(x, y, x + width, y + SIZE, FLAT_OVER);
			}
			var textY = renderUnderline(gui, width);
			LogicFont.drawCentered(gui, LogicFont.text(display(get.get())), x + width / 2, textY, TEXT);
		}
	}
	/** {@code jump} 的跳转节点，由画布负责拖拽连线。 */
	public static class Node extends ParamElement {
		/** 图标在参数行里的内边距，边长与三角尖端的位置都由它推出来。 */
		public static final int INSET = 2, ICON = SIZE - INSET * 2;
		/** 三角尖端相对图标边长的位置，取自纹理里那个尖角。 */
		public static final float TIP = 0.89F;
		/**
		 * 图标再向右探出的距离。节点是参数行的最后一个元素，自身右边距之外只剩卡片内边距
		 * （{@link StatementCard} 的 {@code PAD}），探出这么多正好让图标贴住卡片右边缘，
		 * 对齐 Mindustry 给节点按钮的 {@code padRight(-8f)}。
		 */
		private static final int OVERHANG = 6;
		/** 图标左边缘相对节点元素左边缘的偏移。 */
		public static final int ICON_X = SIZE + OVERHANG - ICON;
		public final Supplier<LStatement> get;
		public final Consumer<LStatement> set;
		public Node(Supplier<LStatement> get, Consumer<LStatement> set) {
			this.get = get;
			this.set = set;
		}
		@Override
		public int width() {
			return SIZE;
		}
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {
			// 节点恒为白色，悬停时才变强调色并给手型，对齐 Mindustry。
			// 图标是正方形，宽高得一样，否则会被压扁
			var over = isOver(mouseX, mouseY);
			if (over) LogicCursor.setHand();
			LogicGuiTextures.LOGIC_NODE.renderTinted(gui, x + ICON_X, y + INSET, ICON, ICON, over ? PLACE : TEXT);
		}
		/** 命中的是图标本身。它比节点元素向右探出去一截，按元素边界算会漏掉右半边。 */
		@Override
		public boolean isOver(double mouseX, double mouseY) {
			return mouseX >= x + ICON_X && mouseX < x + ICON_X + ICON && mouseY >= y + INSET && mouseY < y + INSET + ICON;
		}
	}
}
