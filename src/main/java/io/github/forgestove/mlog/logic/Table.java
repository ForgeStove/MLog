package io.github.forgestove.mlog.logic;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.*;
/** 语句参数区的布局声明：界面层按调用顺序将参数元素自左向右、自上而下排列。 */
public interface Table {
	/** {@code field} 的宽度取值：撑满所在行的剩余空间。 */
	int STRETCH = -1;
	/** 纯文本片段，如 {@code " = "}。 */
	void label(String text);
	/** 需要本地化的文本片段，如「于」「如果」。 */
	void labelKey(String key);
	/** 可编辑的文本参数。 */
	void field(Supplier<String> get, Consumer<String> set, int width);
	/**
	 * 仅可从列表选取的参数：控件为按钮，无输入框。
	 *
	 * @param cols 选项列表每行的按钮数，{@code jump} 的条件为三列。
	 */
	void option(
		Supplier<String> get,
		Consumer<String> set,
		Supplier<List<String>> options,
		@Nullable Function<String, String> display,
		int width,
		int cols
	);
	/** 分组取值的参数：弹出列表顶部为分组按钮，切换后显示对应分组。 */
	void grouped(
		Supplier<String> get,
		Consumer<String> set,
		List<OptionGroup> groups,
		@Nullable Function<String, String> display,
		int width
	);
	/** {@code jump} 的跳转节点，可拖拽连线。 */
	void node(Supplier<MLogStatement> get, Consumer<MLogStatement> set);
	/** 占满剩余宽度，将其后的元素推至右侧。 */
	void spacer();
	/** 参数区的一段文本，供 {@link LStatement#param} 附加悬停提示。 */
	interface Label {
		/** @return 词表里的 token（{@code name.token.mlog.<token>}）。 */
		String token();
		/** @param key 悬停提示的 key；语言文件中不存在时不显示。 */
		void setTipKey(String key);
	}
	/**
	 * 参数选项的一组。
	 *
	 * @param icon    分组图标名（如 {@code "box"}），由界面层映射；
	 *                服务端也会加载本接口，故不能引用图标枚举
	 * @param options 该组的选项
	 * @param cols    该组每行的按钮数
	 */
	record OptionGroup(String icon, Supplier<List<String>> options, int cols) {}
}
