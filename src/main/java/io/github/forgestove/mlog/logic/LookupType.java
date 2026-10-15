package io.github.forgestove.mlog.logic;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;
/** 可查 block / unit / item / liquid / team，顺序保持不变；队伍无对应注册表，故不出现于下拉；液体按本项目的叫法写作 {@code fluid}。 */
public enum LookupType {
	block(BuiltInRegistries.BLOCK),
	unit(BuiltInRegistries.ENTITY_TYPE),
	item(BuiltInRegistries.ITEM),
	fluid(BuiltInRegistries.FLUID),
	;
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	/** {@code lookup} 的编号就是这里面的下标。 */
	public final Registry<?> registry;
	LookupType(Registry<?> registry) {
		this.registry = registry;
	}
	public String display() {
		return "contenttype.label.mlog." + name();
	}
}
