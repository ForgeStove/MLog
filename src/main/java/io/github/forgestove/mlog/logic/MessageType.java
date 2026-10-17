package io.github.forgestove.mlog.logic;
import java.util.*;
/** {@code message} 的显示位置。 */
public enum MessageType {
	chat,
	actionbar,
	;
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	public String display() {
		return "messagetype.label.mlog." + name();
	}
}
