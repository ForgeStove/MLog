package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.core.MLogMods;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/** {@code sensor} 可读取的属性：内置条目供界面下拉，另接受任意方块状态属性名，方块没有该属性时返回 0；关联模组的属性在该模组未安装时既不列入也无法读取，读数由对应的 compat 适配器提供。 */
public enum LAccess {
	// 位置与速度
	x,
	y,
	z,
	velocityX,
	velocityY,
	velocityZ,
	// 方块本体
	type,
	name,
	id,
	solid,
	air,
	hardness,
	hasBlockEntity,
	raining,
	// 单位
	flag,
	armor,
	shield,
	flying,
	boosting,
	controlled,
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
	totalLiquids,
	itemCapacity,
	firstItem,
	emptySlots,
	// 容器里指定的一格
	slotItem,
	slotFluid,
	// Create 的过滤槽
	filter(MLogMods.create),
	// Create 的值设置（扳手滚轮操作）：当前值及其所在行
	value(MLogMods.create),
	valueRow(MLogMods.create),
	// 内存
	memoryCapacity,
	// 显示屏
	displayWidth,
	displayHeight,
	bufferSize,
	operations,
	// 流体
	hasFluid,
	liquidCapacity,
	// 能量
	totalPower,
	powerCapacity,
	// 熔炉烧炼进度
	progress,
	// 实体（query 查出的单位）
	health,
	maxHealth,
	dead,
	// query 写入 @queries 的列表
	size,
	// Create（动能）：读数由 CreateSenseables 按 Create 的公开 API 提供
	speed(MLogMods.create),
	stressImpact(MLogMods.create),
	stressCapacity(MLogMods.create),
	networkStress(MLogMods.create),
	networkCapacity(MLogMods.create),
	// Create 的传动网络检测
	hasNetwork(MLogMods.create),
	networkSize(MLogMods.create),
	networkSources(MLogMods.create),
	overstressed(MLogMods.create),
	;
	public static final LAccess[] all = values();
	private static final List<String> CONTROL_BASE = List.of(
		"redstone", "open", "extended",
		// 朝向与形态
		"facing", "rotation", "axis", "orientation", "face", "attachment", "vertical_direction", "half"
	);
	private static final Map<MLogMods, List<String>> CONTROL_BY_MOD = Map.of(
		MLogMods.create, List.of(
			// 朝向与贴附面
			"axis_along_first", "target", "double_face", "vertical", "backwards", "ceiling", "wall", "flipped", "pointing",
			// 部件与外观，扳手或放置时定下
			"extracting", "casing", "top_shaft", "bottom_shaft", "size",
			// 值设置：把第几行设成多少，行号跟在值后面，按位置识别
			"value",
			// 过滤槽：将值设为该物品，传 null 即清除
			"filter"
		)
	);
	/** 方块状态属性：名字与 MC 的属性名一致，不做本地化，以免译名和实际可读写的属性对不上。 */
	private static final Set<LAccess> BLOCK_PROPERTIES = EnumSet.of(facing, rotation, powered, lit, waterlogged, open, age, level);
	private static final Map<String, LAccess> byName = new HashMap<>();
	private static List<String> names;
	private static List<String> controlAllowed;
	static {
		for (var access : all) byName.put(access.name(), access);
	}
	private final @Nullable MLogMods mod;
	LAccess() {
		this(null);
	}
	LAccess(@Nullable MLogMods mod) {
		this.mod = mod;
	}
	public static List<String> names() {
		if (names == null) names = Arrays.stream(all).filter(LAccess::available).map(access -> "@" + access.name()).toList();
		return names;
	}
	public static String controlTipKey(String access) {
		return controlKey(access) + ".tip";
	}
	public static String controlKey(String access) {
		return "mlog.control.prop." + access;
	}
	public static boolean isControl(String access) {
		return controlAllowed().contains(access);
	}
	/** 属性所属模组的命名空间；内置属性返回 minecraft。 */
	public static String namespace(String name) {
		var access = byName.get(name);
		return access != null && access.mod != null ? access.mod.id() : "minecraft";
	}
	/** 按名字扫描方块状态属性用；世界处理器无视该白名单。 */
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
	/** 可写属性按模组分组：minecraft 在前，其余已加载模组按 id 排序。 */
	public static Map<String, List<String>> controlGroups() {
		var map = new LinkedHashMap<String, List<String>>();
		map.put("minecraft", CONTROL_BASE);
		CONTROL_BY_MOD.entrySet()
			.stream()
			.filter(entry -> entry.getKey().isLoaded())
			.sorted(Comparator.comparing(entry -> entry.getKey().id()))
			.forEach(entry -> map.put(entry.getKey().id(), entry.getValue()));
		return map;
	}
	/** 属性名后还跟一个序号（第几格物品或流体）。 */
	public static boolean usesSlot(@Nullable LAccess access) {
		return access == slotItem || access == slotFluid;
	}
	public static LAccess byName(String name) {
		return byName.get(name);
	}
	public boolean available() {
		return mod == null || mod.isLoaded();
	}
	/** 方块状态属性直接显示原名，不查翻译，也没有说明文字。 */
	public boolean blockProperty() {
		return BLOCK_PROPERTIES.contains(this);
	}
	public String tipKey() {
		return key() + ".tip";
	}
	/** 翻译在界面层做，这里只给出约定的键名。 */
	public String key() {
		return "mlog.access." + name().toLowerCase(Locale.ROOT);
	}
}
