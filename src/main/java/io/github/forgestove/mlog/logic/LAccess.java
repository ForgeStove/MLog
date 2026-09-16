package io.github.forgestove.mlog.logic;
import java.util.*;
/**
 * {@code sensor} 可读取的属性。
 * <p>这里的条目是内置属性，用于界面下拉列表；{@code sensor} 同样接受任意方块状态属性名
 * （如 {@code @facing}、{@code @powered}），按同名属性读取，方块没有该属性时返回 0。
 * <p>Mindustry 里没有对应概念、MC 又实现不了的属性（电力网络余量等）一律不保留。
 */
public enum LAccess {
	// 位置
	x,
	y,
	z,
	// 方块本体
	type,
	name,
	id,
	solid,
	air,
	hardness,
	hasBlockEntity,
	raining,
	// 光照
	light,
	blockLight,
	skyLight,
	// 红石
	redstone,
	emittedRedstone,
	comparator,
	// 方块状态属性
	facing,
	rotation,
	powered,
	lit,
	waterlogged,
	open,
	age,
	level,
	// 容器
	totalItems,
	itemCapacity,
	firstItem,
	emptySlots,
	// 流体
	fluidLevel,
	hasFluid,
	// 能量
	energy,
	energyCapacity,
	// 熔炉烧炼进度
	progress,
	;
	public static final LAccess[] all = values();
	/** 供界面下拉选择的全部属性名，带 {@code @} 前缀。 */
	public static final List<String> NAMES = Arrays.stream(all).map(access -> "@" + access.name()).toList();
	private static final Map<String, LAccess> byName = new HashMap<>();
	static {
		for (var access : all) byName.put(access.name(), access);
	}
	/** @return 对应的内置属性，不是内置的则返回 {@code null}。 */
	public static LAccess byName(String name) {
		return byName.get(name);
	}
}
