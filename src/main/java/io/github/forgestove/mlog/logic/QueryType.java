package io.github.forgestove.mlog.logic;
import java.util.*;
public enum QueryType {
	unit,
	building,
	;
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	public String display() {
		return "mlog.enum.querytype." + name();
	}
}
