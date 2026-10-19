package io.github.forgestove.mlog.logic;
import java.util.*;
/** {@code uradar} 的排序依据。 */
public enum RadarSort {
	distance,
	health,
	;
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	public String display() {
		return "mlog.enum.radarsort." + name();
	}
}
