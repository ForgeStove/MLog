package io.github.forgestove.mlog.logic;
import java.util.*;
/**
 * {@code query} 要查的对象类型。
 */
public enum QueryType {
	unit,
	building,
	;
	/** 供界面下拉选择的全部类型。 */
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	/** @return 界面显示用的本地化键。 */
	public String display() {
		return "querytype.label.mlog." + name();
	}
}
