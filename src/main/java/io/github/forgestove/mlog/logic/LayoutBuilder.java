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
	/**
	 * 只能从列表里挑的参数：整个控件是个按钮，没有输入框。
	 *
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
	/**
	 * 分组的固定取值参数：弹出的列表顶部多一排分组按钮，切一组显示一组。
	 * <p>对齐 Mindustry 的 {@code showSelectTable}——获取数据的铅笔按钮弹出后，
	 * 上面是物品 / 液体 / 属性三组。
	 */
	void grouped(
		Supplier<String> get,
		Consumer<String> set,
		List<OptionGroup> groups,
		@Nullable Function<String, String> display,
		int width
	);
	/** {@code jump} 的跳转节点，可拖拽连线。 */
	void node(Supplier<LStatement> get, Consumer<LStatement> set);
	/** 撑开剩余宽度，把它后面的元素推到右边。 */
	void spacer();
	/**
	 * 参数选项的一组。
	 *
	 * @param icon    分组图标的 Mindustry css 名（如 {@code "box"}），界面层映射成具体图标。
	 *                图标在客户端，而这个接口服务端也会加载，所以这里不能直接引用图标枚举
	 * @param options 该组的选项
	 * @param cols    该组每行放几个。各组的列数不一样——对面的物品与流体表是
	 *                {@code if(++c % 6 == 0) i.row()}，属性表则是一列一条
	 */
	record OptionGroup(String icon, Supplier<List<String>> options, int cols) {}
}
