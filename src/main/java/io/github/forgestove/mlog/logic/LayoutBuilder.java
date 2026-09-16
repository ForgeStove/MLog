package io.github.forgestove.mlog.logic;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.*;
/**
 * 语句参数区的布局描述。语句只声明「这里放什么」，具体控件由界面实现。
 * <p>调用顺序即从左到右、从上到下的排列顺序，与 Mindustry 的 {@code LStatement#build(Table)} 对应。
 */
public interface LayoutBuilder {
	/** {@code field} 的宽度取值：撑满所在行的剩余空间。 */
	int STRETCH = -1;
	/** 纯文本片段，如 {@code " = "}。 */
	void label(String text);
	/** 需要本地化的文本片段，如「于」「如果」。 */
	void labelKey(String key);
	/** 可编辑的文本参数。 */
	void field(Supplier<String> get, Consumer<String> set, int width);
	/** 固定取值的参数，点击弹出选项列表，直接显示取值。 */
	default void select(Supplier<String> get, Consumer<String> set, Supplier<List<String>> options, int width) {
		select(get, set, options, null, width);
	}
	/** 固定取值的参数。{@code display} 用于把取值映射成显示名（如 {@code lessThan} -> {@code <=}），为 {@code null} 时直接显示取值。 */
	void select(
		Supplier<String> get,
		Consumer<String> set,
		Supplier<List<String>> options,
		@Nullable Function<String, String> display,
		int width
	);
	/**
	 * 只能从列表里挑的参数：整个控件是个按钮，没有输入框。
	 * @param cols 弹出的选项列表每行放几个。对齐 Mindustry 的 {@code showSelect(..., cols, ...)}，
	 *             {@code jump} 的条件是三列。
	 */
	void option(
		Supplier<String> get,
		Consumer<String> set,
		Supplier<List<String>> options,
		@Nullable Function<String, String> display,
		int width,
		int cols
	);
	/** {@code jump} 的跳转节点，可拖拽连线。 */
	void node(Supplier<LStatement> get, Consumer<LStatement> set);
	/** 撑开剩余宽度，把它后面的元素推到右边。 */
	void spacer();
}
