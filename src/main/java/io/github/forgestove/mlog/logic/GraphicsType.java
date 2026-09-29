package io.github.forgestove.mlog.logic;
import java.util.*;
/** {@code draw} 指令的绘制类型。 */
public enum GraphicsType {
	clear,
	color,
	/**
	 * 与 {@code color} 同义，但吃的是 {@code packcolor} 那种打包好的颜色值。
	 * <p>只在指令层存在：{@code DrawI} 会当场把它换成一条 {@code color} 再进缓冲区，显示端见不到它。
	 */
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
	/** 供界面下拉选择的全部类型名。 */
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	/** 名字到类型的表，供 {@link #byName(String)} 查。 */
	private static final Map<String, GraphicsType> byName = new HashMap<>();
	static {
		for (var type : values()) byName.put(type.name(), type);
	}
	/** {@code scale} 的换算步长：缩放量按它折算成整数值。 */
	public static final float SCALE_STEP = 0.05F;
	/** 正多边形与多边形轮廓的边数上限。 */
	public static final int MAX_SIDES = 25;
	/** @return 对应的类型，名字不认识时返回 {@code null}。 */
	public static GraphicsType byName(String name) {
		return byName.get(name);
	}
	/** @return 类型名的 lang key。 */
	public String display() {
		return "graphicstype.label.mlog." + name();
	}
	/** @return 悬停提示用的 lang key；语言文件里没有这条键时不出提示。 */
	public String tipKey() {
		return "graphicstype.tip.mlog." + name();
	}
}
