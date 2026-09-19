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
	// 实体（query 查出来的单位）
	health,
	maxHealth,
	dead,
	// query 写进 @queries 的那个列表
	size,
	;
	/**
	 * {@code control} 下拉列表里的常用属性。
	 * <p>和 {@link #NAMES} 一样只是给界面备的快捷项：{@code control} 本身接受任意方块状态属性名，
	 * 写不进去才失败。红石相关的 {@code powered} / {@code lit} / {@code power} 都列在这儿。
	 * <p>注意 {@code powered} / {@code lit} 这几个是被红石驱动的状态，只在附近没有红石源时才保持得住
	 * ——红石一更新就会被算回去。{@code power} 不一样，它不走方块状态，而是往
	 * {@link RedstoneSources} 里登记一个虚拟源，不会被算回去；写法上后面固定跟两个值，
	 * 指定从哪一面接源供电、要不要连强充能一起给。
	 */
	public static final List<String> CONTROLS = List.of("open", "enabled", "lit", "powered", "power", "extended", "facing", "rotation");
	/**
	 * {@code control} 拒绝写入的属性。
	 * <p>只挡那些写进去必然和世界对不上的：含水状态改成 {@code true} 而位置上并没有水，
	 * 方块和水体会各说各话，方块自己也不会去补。
	 */
	public static final List<String> CONTROL_DENIED = List.of("waterlogged", "age", "level");
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
	/** @return 悬停提示用的本地化键，一句说明这个属性读到的是什么。 */
	public String tipKey() {
		return key() + ".tip";
	}
	/** @return 界面显示用的本地化键。翻译在界面层做，这里只是给个约定的键名。 */
	public String key() {
		return "laccess.mlog." + name();
	}
}
