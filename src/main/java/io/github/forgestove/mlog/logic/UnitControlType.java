package io.github.forgestove.mlog.logic;
import java.util.*;
/** {@code ucontrol} 的动作。 */
public enum UnitControlType implements Tipped {
	idle,
	stop,
	move,
	approach,
	pathfind,
	target,
	targetp,
	itemDrop,
	itemTake,
	flag,
	getBlock,
	within,
	unbind,
	;
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	public String display() {
		return "mlog.enum.unitcontroltype." + name().toLowerCase(Locale.ROOT);
	}
	/** 悬停提示的 lang key；语言文件里没有这条键时不出提示。 */
	@Override
	public String tipKey() {
		return display() + ".tip";
	}
}
