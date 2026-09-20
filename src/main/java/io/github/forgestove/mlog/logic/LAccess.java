package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.core.MLogMods;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/**
 * {@code sensor} 可读取的属性。
 * <p>这里的条目是内置属性，用于界面下拉列表；{@code sensor} 同样接受任意方块状态属性名
 * （如 {@code @facing}、{@code @powered}），按同名属性读取，方块没有该属性时返回 0。
 * <p>Mindustry 里没有对应概念、MC 又实现不了的属性（电力网络余量等）一律不保留。
 * <p>属性可挂一个模组（{@link MLogMods}）：该模组未安装时不算数，既不列入也读不到，
 * 读数由对应的 compat 适配器提供。
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
	// 容器里指定的一格，Mindustry 没有对应属性
	slotItem,
	slotFluid,
	// Create 的过滤槽
	filter(MLogMods.create),
	// Create 的值设置（扳手滚轮那种）：当前的值，以及当前在哪一行
	value(MLogMods.create),
	valueRow(MLogMods.create),
	// 内存
	memoryCapacity,
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
	// Create（动能）：读数由 CreateSenseables 按 Create 的公开 API 提供
	speed(MLogMods.create),
	stressImpact(MLogMods.create),
	stressCapacity(MLogMods.create),
	networkStress(MLogMods.create),
	networkCapacity(MLogMods.create),
	overstressed(MLogMods.create),
	;
	public static final LAccess[] all = values();
	/** 不依赖模组的控制属性。 */
	private static final List<String> CONTROL_BASE = List.of(
		"power", "open", "extended",
		// 朝向与形态
		"facing", "rotation", "axis", "orientation", "face", "attachment", "vertical_direction", "half"
	);
	/** 各模组自己的控制属性。 */
	private static final Map<MLogMods, List<String>> CONTROL_BY_MOD = Map.of(
		MLogMods.create, List.of(
			// 朝向与贴附面
			"axis_along_first", "target", "double_face", "vertical", "backwards", "ceiling", "wall", "flipped", "pointing",
			// 部件与外观，扳手或放置时定下
			"extracting", "casing", "top_shaft", "bottom_shaft", "size", "rail_type",
			// 值设置：把第几行设成多少，行号跟在值后面（照 power 的朝向那样按位置认）
			"value",
			// 过滤槽：把值设成这个物品，给 null 就清掉
			"filter"
		)
	);
	private static final Map<String, LAccess> byName = new HashMap<>();
	/**
	 * 属性名（带 {@code @} 前缀）的缓存。
	 */
	private static List<String> names;
	/** 白名单缓存，同 {@link #names}。 */
	private static List<String> controlAllowed;
	static {
		for (var access : all) byName.put(access.name(), access);
	}
	/** 所属模组，{@code null} 表示不依赖模组。 */
	private final @Nullable MLogMods mod;
	LAccess() {
		this(null);
	}
	LAccess(@Nullable MLogMods mod) {
		this.mod = mod;
	}
	/** @return 供界面下拉选择的属性名。 */
	public static List<String> names() {
		if (names == null) names = Arrays.stream(all).filter(LAccess::available).map(access -> "@" + access.name()).toList();
		return names;
	}
	/** @return 是否可用：不依赖模组，或所属模组已加载 */
	public boolean available() {
		return mod == null || mod.isLoaded();
	}
	/** @return {@code control} 属性说明的本地化键。 */
	public static String controlTipKey(String access) {
		return controlKey(access) + ".tip";
	}
	/** @return {@code control} 属性名对应的本地化名键。 */
	public static String controlKey(String access) {
		return "lcontrol.mlog." + access;
	}
	/** @return 这个属性名是否在当前白名单里，界面据此决定是否本地化显示、是否给提示 */
	public static boolean isControl(String access) {
		return controlAllowed().contains(access);
	}
	/**
	 * @return {@code control} 属性的白名单：不依赖模组那批，加上已加载模组各自那批。
	 * 	世界处理器无视该白名单。
	 * 	按名字扫描方块状态属性。
	 */
	public static List<String> controlAllowed() {
		if (controlAllowed == null) {
			var list = new ArrayList<>(CONTROL_BASE);
			CONTROL_BY_MOD.forEach((mod, allowed) -> {
				if (mod.isLoaded()) list.addAll(allowed);
			});
			controlAllowed = List.copyOf(list);
		}
		return controlAllowed;
	}
	/** @return 这个属性后面还跟一个序号（第几格物品、第几罐流体）。 */
	public static boolean usesSlot(@Nullable LAccess access) {
		return access == slotItem || access == slotFluid;
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
