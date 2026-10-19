package io.github.forgestove.mlog.logic;
import java.util.*;
/** {@code message} 的显示位置，按 MC 自己的叫法命名。 */
public enum MessageType {
	chat,
	actionbar,
	title,
	bossbar,
	;
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	public String display() {
		return "mlog.enum.messagetype." + name();
	}
}
