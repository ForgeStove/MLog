package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.client.gui.*;
import io.github.forgestove.mlog.client.gui.component.*;
import io.github.forgestove.mlog.client.gui.component.LogicDragHandle.Handler;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.Table.OptionGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.*;

import static io.github.forgestove.mlog.client.gui.LogicColors.*;
import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 布局与事件由 {@link LogicCanvas} 自行管理，以便滚动时控制位置与 z 序，并避免控件移出可滚动区后仍响应。 */
public abstract class ParamElement {
	public static final int SIZE = 14;
	public static final int PAD = 4;
	public int x, y;
	protected int color = TEXT;
	/** 宽度由所在行剩余空间决定，而非自身固定。 */
	public boolean stretch() {
		return false;
	}
	public void setWidth(int width) {}
	public abstract void render(GuiGraphics gui, int mouseX, int mouseY);
	/** 同步外部改动的值，被聚焦的输入框除外。 */
	public void sync() {}
	public void unfocus() {}
	public void setPosition(int x, int y) {
		this.x = x;
		this.y = y;
	}
	public boolean isOver(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width() && mouseY >= y && mouseY < y + SIZE;
	}
	public abstract int width();
	public void collectWidgets(List<GuiEventListener> out) {}
	public @Nullable String tipKey() {
		return null;
	}
	protected int renderUnderline(GuiGraphics gui, int w) {
		var h = LogicGuiTextures.UNDERLINE_H;
		LogicGuiTextures.UNDERLINE.renderTinted(gui, x, y + SIZE - h, w, h, color);
		return y + SIZE / 2 - 4;
	}
	public static class Spacer extends ParamElement {
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {}
		@Override
		public int width() {
			return 0;
		}
	}
	public static class Label extends ParamElement implements Table.Label {
		private final Component text;
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
		public void render(GuiGraphics gui, int mouseX, int mouseY) {
			LogicFont.draw(gui, text, x + PAD, y + SIZE / 2 - 4, color);
		}
		@Override
		public int width() {
			return mc.font.width(text) + PAD * 2;
		}
		@Override
		public @Nullable String tipKey() {
			return tipKey;
		}
	}
	public static class Field extends ParamElement {
		private static final int PLACEHOLDER_W = 1024;
		private final Supplier<String> get;
		private final LogicEditBox box;
		private final boolean stretch;
		private int width;
		public Field(Supplier<String> get, Consumer<String> set, int width, int color) {
			this.get = get;
			this.color = color;
			stretch = width < 0;
			this.width = stretch ? 0 : width;
			// 弹性字段宽度布局后才定，故先给大值；按 0 宽算会把显示起点推到文本末尾且此后不再重算
			box = new LogicEditBox(stretch ? PLACEHOLDER_W : this.width, LogicFont.LINE_H, Component.empty());
			box.setBordered(false);
			box.setMaxLength(64);
			box.setAccent(color);
			// 下划线随输入框绘制并复用 accent 色
			box.setUnderlined(true);
			box.setValue(get.get());
			box.setResponder(set);
		}
		@Override
		public boolean stretch() {
			return stretch;
		}
		@Override
		public void setWidth(int width) {
			if (!stretch) return;
			this.width = width;
			box.setWidth(Math.max(0, width));
		}
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {
			renderBox(gui, mouseX, mouseY);
		}
		/** 供 {@link FieldButton} 关闭输入框自带的下划线，改由整个控件统一绘制。 */
		public void setUnderlined(boolean enabled) {
			box.setUnderlined(enabled);
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
		public void setPosition(int x, int y) {
			super.setPosition(x, y);
			box.setX(x);
			box.setY(y);
			box.setHeight(SIZE);
		}
		@Override
		public int width() {
			return width;
		}
		/** 只画输入框本身、不含底条，供与右侧按钮共用底条的 {@link Select} 使用。 */
		public void renderBox(GuiGraphics gui, int mouseX, int mouseY) {
			box.render(gui, mouseX, mouseY, 0F);
		}
		@Override
		public void collectWidgets(List<GuiEventListener> out) {
			out.add(box);
		}
	}
	public abstract static class Picker extends ParamElement {
		public final Supplier<String> get;
		public final Consumer<String> set;
		public final Supplier<List<String>> options;
		/** 为空表示只有一组，即 {@link #options}。 */
		public final List<OptionGroup> groups;
		/** 分组为左侧分类列而非顶部横排。 */
		public final boolean sidebar;
		/** 分类列与选项列之间补一列试听按钮。 */
		public final boolean preview;
		private final @Nullable Function<String, String> display;
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
			this(get, set, options, groups, display, false, false);
		}
		protected Picker(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			List<OptionGroup> groups,
			@Nullable Function<String, String> display,
			boolean sidebar,
			boolean preview
		) {
			this.get = get;
			this.set = set;
			this.options = options;
			this.groups = groups;
			this.display = display;
			this.sidebar = sidebar;
			this.preview = preview;
		}
		/** 选中后卡片重建全部控件，新控件须接过这份记忆，否则再打开会回到第一组、滚回顶部。 */
		public void adopt(Picker other) {
			lastGroup = other.lastGroup;
			lastScroll = other.lastScroll;
			lastQuery = other.lastQuery;
		}
		public String display(String value) {
			return display == null ? value : display.apply(value);
		}
		public abstract int anchorCenter();
		public int cols() {
			return 1;
		}
		/** 分组列表默认为真，长列表由语句声明。 */
		public boolean searchable() {
			return !groups.isEmpty();
		}
		public @Nullable String tipKey(String name) {
			return null;
		}
	}
	/** 既能从列表中选择，也能手动输入列表之外的值。 */
	public static class Select extends FieldButton {
		public Select(
			Supplier<String> get,
			Consumer<String> set,
			List<OptionGroup> groups,
			@Nullable Function<String, String> display,
			int width,
			int color,
			CanvasActions canvas,
			boolean sidebar,
			boolean preview
		) {
			super(get, set, groups.getFirst().options(), groups, display, width, color, canvas, sidebar, preview);
		}
	}
	/** 按钮行为由子类决定，铅笔绘制与左右半判定在此统一实现。 */
	public abstract static class FieldButton extends Picker {
		/** 图标字号为全局（{@code icons.json} 的 {@code size}），故铅笔单独缩放。 */
		private static final float PENCIL_W = 6;
		public final Field input;
		public final LogicCellButton button;
		protected FieldButton(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			List<OptionGroup> groups,
			@Nullable Function<String, String> display,
			int width,
			int color,
			CanvasActions canvas
		) {
			this(get, set, options, groups, display, width, color, canvas, false, false);
		}
		protected FieldButton(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			List<OptionGroup> groups,
			@Nullable Function<String, String> display,
			int width,
			int color,
			CanvasActions canvas,
			boolean sidebar,
			boolean preview
		) {
			super(get, set, options, groups, display, sidebar, preview);
			this.color = color;
			input = new Field(get, set, width - SIZE, color);
			// 下划线由整个控件共用一条，输入框自身不再绘制
			input.setUnderlined(false);
			button = new LogicCellButton(SIZE, SIZE, (self, gui, mouseX, mouseY, hovered, pressed) -> renderButton(gui, self, hovered));
			button.setAction(() -> canvas.openPicker(this));
		}
		@Override
		public int anchorCenter() {
			return buttonX() + SIZE / 2;
		}
		public int buttonX() {
			return x + input.width();
		}
		private void renderButton(GuiGraphics gui, LogicCellButton self, boolean hovered) {
			var bx = self.getX();
			var by = self.getY();
			if (hovered) {
				LogicCursor.setHand();
				gui.fill(bx, by, bx + SIZE, by + SIZE, flatOver(color));
			}
			// 缩放定点与居中基准为同一点，缩小后字形中心不会偏移
			var scale = PENCIL_W / LogicIcons.PENCIL.width();
			var pose = gui.pose();
			pose.pushPose();
			pose.translate(bx + SIZE / 2F, by + SIZE / 2F, 0F);
			pose.scale(scale, scale, 1F);
			pose.translate(-(bx + SIZE / 2F), -(by + SIZE / 2F), 0F);
			LogicIcons.PENCIL.render(gui, bx + (SIZE - LogicIcons.PENCIL.width()) / 2, LogicIcons.centerY(by, SIZE), TEXT);
			pose.popPose();
		}
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {
			input.renderBox(gui, mouseX, mouseY);
			// 先绘底条再叠悬停底色，铅笔绘制在其后，会连同底条一并遮挡
			renderUnderline(gui, width());
			button.render(gui, mouseX, mouseY, 0F);
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
		public void setPosition(int x, int y) {
			super.setPosition(x, y);
			input.setPosition(x, y);
			button.setX(buttonX());
			button.setY(y);
		}
		@Override
		public int width() {
			return input.width() + SIZE;
		}
		@Override
		public void collectWidgets(List<GuiEventListener> out) {
			input.collectWidgets(out);
			out.add(button);
		}
	}
	public static class Color extends FieldButton {
		public Color(Supplier<String> get, Consumer<String> set, int width, int color, CanvasActions canvas) {
			super(get, set, List::of, List.of(), null, width, color, canvas);
		}
	}
	public static class Option extends Picker {
		/** 按钮宽度上限，避免长注册名把卡片撑开。 */
		private static final int MAX_TEXT_W = 76;
		private final int width, cols;
		private final boolean search;
		private final @Nullable Function<String, String> tipKey;
		private final LogicCellButton button;
		public Option(
			Supplier<String> get,
			Consumer<String> set,
			Supplier<List<String>> options,
			@Nullable Function<String, String> display,
			@Nullable Function<String, String> tipKey,
			int width,
			int color,
			int cols,
			boolean search,
			CanvasActions canvas
		) {
			super(get, set, options, List.of(), display);
			this.width = fitWidth(width, options, display);
			this.cols = cols;
			this.search = search;
			this.tipKey = tipKey;
			this.color = color;
			button = new LogicCellButton(this.width, SIZE, (self, gui, mouseX, mouseY, hovered, pressed) -> renderButton(gui, hovered));
			button.setAction(() -> canvas.openPicker(this));
		}
		/** 按钮至少容纳最长选项，避免本地化长词溢出。 */
		private static int fitWidth(int declared, Supplier<List<String>> options, @Nullable Function<String, String> display) {
			var max = declared;
			for (var name : options.get()) {
				var text = display == null ? name : display.apply(name);
				max = Math.max(max, LogicFont.width(LogicFont.text(text)) + PAD * 2);
			}
			return Math.min(MAX_TEXT_W, max);
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
		public boolean searchable() {
			return search;
		}
		@Override
		public @Nullable String tipKey(String name) {
			return tipKey == null ? null : tipKey.apply(name);
		}
		private void renderButton(GuiGraphics gui, boolean hovered) {
			// 先绘底条再叠悬停底色，会连同底条一并遮挡
			var textY = renderUnderline(gui, width);
			if (hovered) {
				LogicCursor.setHand();
				gui.fill(x, y, x + width, y + SIZE, flatOver(color));
			}
			LogicFont.drawCentered(gui, LogicFont.text(display(get.get())), x + width / 2, textY, TEXT);
		}
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {
			button.render(gui, mouseX, mouseY, 0F);
		}
		@Override
		public void setPosition(int x, int y) {
			super.setPosition(x, y);
			button.setX(x);
			button.setY(y);
		}
		@Override
		public int width() {
			return width;
		}
		@Override
		public void collectWidgets(List<GuiEventListener> out) {
			out.add(button);
		}
	}
	/** 由画布负责拖拽连线。 */
	public static class Node extends ParamElement {
		/** 三角尖端相对图标边长的位置。 */
		public static final float TIP = 0.89F;
		/** 节点右边距之外只剩卡片内边距（{@link StatementCard} 的 {@code PAD}），探出此值使图标贴住卡片右边缘。 */
		public static final int OVERHANG = 6;
		public static final int INSET = 2;
		public static final int ICON = SIZE - INSET * 2;
		public static final int ICON_X = SIZE + OVERHANG - ICON;
		public final Supplier<MLogStatement> get;
		public final Consumer<MLogStatement> set;
		private final LogicDragHandle handle;
		public Node(Supplier<MLogStatement> get, Consumer<MLogStatement> set, StatementCard card, CanvasActions canvas) {
			this.get = get;
			this.set = set;
			handle = new LogicDragHandle(
				ICON, ICON, new Handler() {
				@Override
				public void press(double mouseX, double mouseY) {
					canvas.beginLink(Node.this, card, mouseX, mouseY);
				}
				@Override
				public void drag(double mouseX, double mouseY) {
					canvas.dragLink(mouseX, mouseY);
				}
				@Override
				public void release(double mouseX, double mouseY, int button) {
					canvas.endLink(mouseX, mouseY);
				}
			}
			);
		}
		@Override
		public void setPosition(int x, int y) {
			super.setPosition(x, y);
			handle.setX(x + ICON_X);
			handle.setY(y + INSET);
			handle.setWidth(ICON);
			handle.setHeight(ICON);
		}
		@Override
		public void collectWidgets(List<GuiEventListener> out) {
			out.add(handle);
		}
		@Override
		public void render(GuiGraphics gui, int mouseX, int mouseY) {
			// 图标为正方形，宽高须一致，否则会被压缩
			var over = isOver(mouseX, mouseY);
			if (over) LogicCursor.setHand();
			LogicGuiTextures.LOGIC_NODE.renderTinted(gui, x + ICON_X, y + INSET, ICON, ICON, over ? PLACE : TEXT);
		}
		/** 命中图标本身而非元素边界：图标比节点元素向右探出一截。 */
		@Override
		public boolean isOver(double mouseX, double mouseY) {
			return mouseX >= x + ICON_X && mouseX < x + ICON_X + ICON && mouseY >= y + INSET && mouseY < y + INSET + ICON;
		}
		@Override
		public int width() {
			return SIZE;
		}
	}
}
