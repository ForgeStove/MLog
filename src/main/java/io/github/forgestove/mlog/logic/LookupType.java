package io.github.forgestove.mlog.logic;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;
/**
 * {@code lookup} 能查的注册表，对齐 Mindustry 的 {@code ContentType}。
 * <p>那边能查的是 {@code block / unit / item / liquid / team}，这边去掉队伍（MC 没有），
 * 顺序也照那边排；液体按本项目的叫法写成 {@code fluid}。
 */
public enum LookupType {
	block(BuiltInRegistries.BLOCK),
	unit(BuiltInRegistries.ENTITY_TYPE),
	item(BuiltInRegistries.ITEM),
	fluid(BuiltInRegistries.FLUID),
	;
	/** 供界面下拉选择的全部类型。 */
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	/** 类型对应的注册表，{@code lookup} 的编号就是这里面的下标。 */
	public final Registry<?> registry;
	LookupType(Registry<?> registry) {
		this.registry = registry;
	}
	/** @return 界面显示用的本地化键。对齐 Mindustry 的 {@code contenttype.label.<类型>}。 */
	public String display() {
		return "contenttype.label.mlog." + name();
	}
}
