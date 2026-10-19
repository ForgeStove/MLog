package io.github.forgestove.mlog.logic;
import java.util.*;
public enum GraphicsType implements Tipped {
	clear,
	color,
	/** {@code packcolor} 的打包颜色值；仅在指令层存在，{@code DrawI} 会转换为 {@code color} 命令，显示端不会收到。 */
	col,
	stroke,
	line,
	rect,
	lineRect,
	poly,
	linePoly,
	triangle,
	image,
	print,
	translate,
	scale,
	rotate,
	reset,
	;
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	/** {@code scale} 的换算步长：缩放量按它折算成整数值。 */
	public static final float SCALE_STEP = 0.05F;
	/** 正多边形与多边形轮廓的边数上限。 */
	public static final int MAX_SIDES = 25;
	private static final Map<String, GraphicsType> byName = new HashMap<>();
	static {
		for (var type : values()) byName.put(type.name(), type);
	}
	public static GraphicsType byName(String name) {
		return byName.get(name);
	}
	public String display() {
		return "mlog.enum.graphicstype." + name().toLowerCase(Locale.ROOT);
	}
	/** 悬停提示的 lang key；语言文件里没有这条键时不出提示。 */
	@Override
	public String tipKey() {
		return display() + ".tip";
	}
}
