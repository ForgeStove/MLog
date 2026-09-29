package io.github.forgestove.mlog.logic;
import java.util.*;
/** {@code draw print} 的文本对齐方式，决定锚点落在文本块的哪一角。 */
public enum DrawAlign {
	bottomLeft,
	bottom,
	bottomRight,
	left,
	center,
	right,
	topLeft,
	top,
	topRight,
	;
	/** 供界面下拉选择的全部对齐名。 */
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	/**
	 * @return 水平偏移系数：0 表示锚点在文本左侧，0.5 居中，1 靠右。
	 */
	public float horizontal() {
		return switch (this) {
			case bottomLeft, left, topLeft -> 0F;
			case center, top, bottom -> 0.5F;
			case bottomRight, right, topRight -> 1F;
		};
	}
	/** @return 垂直偏移系数：0 表示锚点在文本上边，0.5 居中，1 靠下。 */
	public float vertical() {
		return switch (this) {
			case topLeft, top, topRight -> 0F;
			case left, center, right -> 0.5F;
			case bottomLeft, bottom, bottomRight -> 1F;
		};
	}
	/** @return 对齐名的 lang key。 */
	public String display() {
		return "name.token.mlog." + name();
	}
}
