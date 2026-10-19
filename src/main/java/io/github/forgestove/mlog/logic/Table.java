package io.github.forgestove.mlog.logic;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.*;
/** 语句参数区的布局声明：界面层按调用顺序将参数元素自左向右、自上而下排列。 */
public interface Table {
	/** {@code field} 的宽度取值：撑满所在行的剩余空间。 */
	int STRETCH = -1;
	void label(String text);
	void labelKey(String key);
	void field(Supplier<String> get, Consumer<String> set, int width);
	void color(Supplier<String> get, Consumer<String> set, int width);
	/** 仅可从列表选取的参数：控件为按钮，无输入框；{@code cols} 为每行按钮数，{@code jump} 的条件为三列。 */
	default void option(
		Supplier<String> get,
		Consumer<String> set,
		Supplier<List<String>> options,
		@Nullable Function<String, String> display,
		int width,
		int cols
	) {
		option(get, set, options, display, null, width, cols, false);
	}
	default void option(
		Supplier<String> get,
		Consumer<String> set,
		Supplier<List<String>> options,
		@Nullable Function<String, String> display,
		int width,
		int cols,
		boolean search
	) {
		option(get, set, options, display, null, width, cols, search);
	}
	/** {@code tipKey} 把选项名映射为提示的本地化键，返回 {@code null} 的选项不出提示；整个字段不给提示时传 {@code null}。 */
	void option(
		Supplier<String> get,
		Consumer<String> set,
		Supplier<List<String>> options,
		@Nullable Function<String, String> display,
		@Nullable Function<String, String> tipKey,
		int width,
		int cols,
		boolean search
	);
	/** {@code sidebar} 为真时分组改为左侧竖排（分类列），选项在右侧。 */
	default void grouped(
		Supplier<String> get,
		Consumer<String> set,
		List<OptionGroup> groups,
		@Nullable Function<String, String> display,
		int width
	) {
		grouped(get, set, groups, display, width, false);
	}
	default void grouped(
		Supplier<String> get,
		Consumer<String> set,
		List<OptionGroup> groups,
		@Nullable Function<String, String> display,
		int width,
		boolean sidebar
	) {
		grouped(get, set, groups, display, width, sidebar, false);
	}
	/** {@code preview} 为真时在分类列与选项列之间加一列试听按钮。 */
	void grouped(
		Supplier<String> get,
		Consumer<String> set,
		List<OptionGroup> groups,
		@Nullable Function<String, String> display,
		int width,
		boolean sidebar,
		boolean preview
	);
	/** 分组但只能从列表选取：控件为整块按钮，无输入框。 */
	void groupedOption(
		Supplier<String> get,
		Consumer<String> set,
		List<OptionGroup> groups,
		@Nullable Function<String, String> display,
		int width,
		boolean sidebar,
		boolean preview
	);
	/** 顶部图标分类与左侧文字分类同时显示；选项取两组所选分类的交集。 */
	void grouped(
		Supplier<String> get,
		Consumer<String> set,
		List<OptionGroup> groups,
		List<OptionGroup> sideGroups,
		@Nullable Function<String, String> display,
		int width
	);
	/** 可作为单位的实体类型：由界面层按当前世界实例判定，包含模组实体。 */
	void unitTypes(Supplier<String> get, Consumer<String> set, int width, int cols);
	void node(Supplier<MLogStatement> get, Consumer<MLogStatement> set);
	void spacer();
	interface Label {
		/** 词表键为 {@code mlog.token.<token>}。 */
		String token();
		/** 语言文件中不存在时不显示。 */
		void setTipKey(String key);
	}
	/** {@code icon} 为界面层映射的分组图标名；服务端也会加载本接口，故不能引用图标枚举。 */
	record OptionGroup(String icon, Supplier<List<String>> options, int cols, String label) {
		public OptionGroup(String icon, Supplier<List<String>> options, int cols) {
			this(icon, options, cols, "");
		}
	}
}
