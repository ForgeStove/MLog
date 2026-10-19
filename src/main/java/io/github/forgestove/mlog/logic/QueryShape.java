package io.github.forgestove.mlog.logic;
import java.util.*;
/** {@code query} 要查的区域形状；坐标含义跟着形状走：{@code circle} 给中心与半径，{@code rect} 给最小角与三边。 */
public enum QueryShape {
	circle,
	rect,
	;
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	/** 界面显示用的本地化键，走 {@code name.token} 词表。 */
	public String display() {
		return "mlog.enum.queryshape." + name();
	}
}
