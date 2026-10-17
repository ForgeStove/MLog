package io.github.forgestove.mlog.logic;
import java.util.*;
/** {@code ucontrol} 的动作。 */
public enum UnitControlType {
	idle,
	stop,
	move,
	approach,
	pathfind,
	boost,
	targetp,
	itemDrop,
	itemTake,
	flag,
	within,
	unbind,
	;
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	public String display() {
		return "unitcontrol.label.mlog." + name();
	}
}
