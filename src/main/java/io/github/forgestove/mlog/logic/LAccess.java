package io.github.forgestove.mlog.logic;
import net.neoforged.fml.ModList;

import java.util.*;
import java.util.stream.Stream;
/**
 * {@code sensor} 可读取的属性。
 * <p>这里的条目是内置属性，用于界面下拉列表；{@code sensor} 同样接受任意方块状态属性名
 * （如 {@code @facing}、{@code @powered}），按同名属性读取，方块没有该属性时返回 0。
 * <p>Mindustry 里没有对应概念、MC 又实现不了的属性（电力网络余量等）一律不保留。
 * <p>末尾那批动能属性是给 Create 加的：读数由 {@code CreateSenseables} 提供，没装 Create 时
 * 既不列进下拉框、也读不出东西（见 {@link #names()}）。
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
	speed(true),
	stressImpact(true),
	stressCapacity(true),
	networkStress(true),
	networkCapacity(true),
	overstressed(true),
	;
	/** 是不是只有装了 Create 才有意义。界面下拉列表按它过滤。 */
	private final boolean createOnly;
	LAccess() {
		this(false);
	}
	LAccess(boolean createOnly) {
		this.createOnly = createOnly;
	}
	public static final LAccess[] all = values();
	/** 全部属性名，带 {@code @} 前缀。 */
	public static final List<String> NAMES = Arrays.stream(all).map(access -> "@" + access.name()).toList();
	/** 不带模组就能用的那批，没装 Create 时给界面用。 */
	private static final List<String> BASE_NAMES = Arrays.stream(all)
		.filter(access -> !access.createOnly)
		.map(access -> "@" + access.name())
		.toList();
	/** @return 供界面下拉选择的属性名：没装 Create 时滤掉动能那批。 */
	public static List<String> names() {
		return createLoaded() ? NAMES : BASE_NAMES;
	}
	private static final List<String> CONTROL_BASE = List.of(
		"power", "open", "extended",
		// 朝向与形态
		"facing", "rotation", "axis", "orientation", "face", "attachment", "vertical_direction", "half"
	);
	private static final List<String> CONTROL_CREATE = List.of(
		// 朝向与贴附面
		"axis_along_first", "target", "double_face", "vertical", "backwards", "ceiling", "wall", "flipped", "pointing",
		// 部件与外观，扳手或放置时定下
		"extracting", "casing", "top_shaft", "bottom_shaft", "size", "rail_type"
	);
	/** 两批合起来就是装了 Create 时的白名单。 */
	private static final List<String> CONTROL_ALL = Stream.concat(CONTROL_BASE.stream(), CONTROL_CREATE.stream()).toList();
	private static final Map<String, LAccess> byName = new HashMap<>();
	static {
		for (var access : all) byName.put(access.name(), access);
	}
	/** @return {@code control} 属性说明的本地化键。 */
	public static String controlTipKey(String access) {
		return controlKey(access) + ".tip";
	}
	/** @return {@code control} 属性名对应的本地化名键。 */
	public static String controlKey(String access) {
		return "lcontrol.mlog." + access;
	}
	/** @return 这个属性名在不在当前白名单里。界面拿它决定要不要按本地化显示、给不给提示。 */
	public static boolean isControl(String access) {
		return controlAllowed().contains(access);
	}
	/**
	 * @return {@code control} 属性的白名单。
	 * 	世界处理器无视该白名单。
	 * 	按名字扫描方块状态属性。
	 */
	public static List<String> controlAllowed() {
		return createLoaded() ? CONTROL_ALL : CONTROL_BASE;
	}
	/** @return 装了 Create 没有。Create 兼容那几批（控制白名单、动能读数）都按它取舍。 */
	public static boolean createLoaded() {
		return ModList.get().isLoaded("create");
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
