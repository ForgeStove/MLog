package io.github.forgestove.mlog.logic;
import org.jetbrains.annotations.Nullable;
/**
 * 带悬停说明的枚举选项。
 * <p>说明按常量名查词表；同名常量在不同枚举里各是各的条目，归属须由字段指明。
 */
public interface Tipped {
	/** @return 悬停提示的本地化键。 */
	String tipKey();
	/** @return {@code type} 里名为 {@code name} 的常量的提示键；名字认不出时为 {@code null}。 */
	static <E extends Enum<E> & Tipped> @Nullable String tipKeyOf(Class<E> type, String name) {
		for (var value : type.getEnumConstants()) if (value.name().equals(name)) return value.tipKey();
		return null;
	}
}
