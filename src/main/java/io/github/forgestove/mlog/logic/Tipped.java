package io.github.forgestove.mlog.logic;
import org.jetbrains.annotations.Nullable;
/** 带悬停说明的枚举选项：说明按常量名查词表，同名常量须由所在枚举区分。 */
public interface Tipped {
	static <E extends Enum<E> & Tipped> @Nullable String tipKeyOf(Class<E> type, String name) {
		for (var value : type.getEnumConstants()) if (value.name().equals(name)) return value.tipKey();
		return null;
	}
	String tipKey();
}
