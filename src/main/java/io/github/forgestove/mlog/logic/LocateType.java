package io.github.forgestove.mlog.logic;
import java.util.*;
/** {@code ulocate} 的寻找目标。 */
public enum LocateType {
	block,
	spawn,
	injured,
	;
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	public String display() {
		return "mlog.enum.locatetype." + name();
	}
}
