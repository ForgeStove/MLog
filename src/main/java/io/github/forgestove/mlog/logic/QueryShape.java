package io.github.forgestove.mlog.logic;
import java.util.*;
/**
 * {@code query} 要查的区域形状，对齐 Mindustry 的 {@code QueryShape}。两边的坐标含义都跟着形状走：
 * {@code circle} 给的是中心与半径，{@code rect} 给的是最小角与三边。
 */
public enum QueryShape {
	circle,
	rect,
	;
	/** 供界面下拉选择的全部形状。 */
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	/** @return 界面显示用的本地化键。形状是词，和 {@code op} 的 {@code and} / {@code flip} 一样走 {@code name.token} 词表。 */
	public String display() {
		return "name.token.mlog." + name();
	}
}
