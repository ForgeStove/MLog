package io.github.forgestove.mlog.logic;
import java.util.*;
import org.jetbrains.annotations.Nullable;
/** 原版提供的属性条目。 */
public enum VanillaAccess implements LAccess {
	x,
	y,
	z,
	velocityX,
	velocityY,
	velocityZ,
	type,
	name,
	id,
	solid,
	air,
	hardness,
	hasBlockEntity,
	raining,
	flag,
	armor,
	shield,
	flying,
	boosting,
	controlled,
	light,
	blockLight,
	skyLight,
	redstone,
	emittedRedstone,
	comparator,
	facing(AccessFlags.BLOCK_PROPERTY),
	rotation(AccessFlags.BLOCK_PROPERTY),
	powered(AccessFlags.BLOCK_PROPERTY),
	lit(AccessFlags.BLOCK_PROPERTY),
	waterlogged(AccessFlags.BLOCK_PROPERTY),
	open(AccessFlags.BLOCK_PROPERTY),
	age(AccessFlags.BLOCK_PROPERTY),
	level(AccessFlags.BLOCK_PROPERTY),
	axis(AccessFlags.BLOCK_PROPERTY),
	orientation(AccessFlags.BLOCK_PROPERTY),
	face(AccessFlags.BLOCK_PROPERTY),
	attachment(AccessFlags.BLOCK_PROPERTY),
	vertical_direction(AccessFlags.BLOCK_PROPERTY),
	half(AccessFlags.BLOCK_PROPERTY),
	extended(AccessFlags.BLOCK_PROPERTY),
	totalItems,
	totalLiquids,
	itemCapacity,
	firstItem,
	emptySlots,
	slotItem(AccessFlags.SLOT_ITEM),
	slotFluid(AccessFlags.SLOT_FLUID),
	memoryCapacity,
	displayWidth,
	displayHeight,
	bufferSize,
	operations,
	hasFluid,
	liquidCapacity,
	totalPower,
	powerCapacity,
	progress,
	health,
	maxHealth,
	dead,
	/** {@code @queries} 的列表大小 */
	size(AccessFlags.LIST_SIZE),
	;
	private static final Map<String, VanillaAccess> BY_NAME = new HashMap<>();
	static {
		for (var access : values()) BY_NAME.put(access.name(), access);
	}
	/** 可写的方块状态属性，顺序即控制下拉里的顺序。 */
	private static final List<String> CONTROLS = List.of(
		redstone.name(),
		powered.name(),
		open.name(),
		extended.name(),
		facing.name(),
		rotation.name(),
		axis.name(),
		orientation.name(),
		face.name(),
		attachment.name(),
		vertical_direction.name(),
		half.name()
	);
	private final int flags;
	VanillaAccess() {
		this(0);
	}
	VanillaAccess(int flags) {
		this.flags = flags;
	}
	public static @Nullable VanillaAccess byName(String name) {
		return BY_NAME.get(name);
	}
	public static List<String> controls() {
		return CONTROLS;
	}
	@Override
	public boolean blockProperty() {
		return (flags & AccessFlags.BLOCK_PROPERTY) != 0;
	}
	@Override
	public boolean listSize() {
		return (flags & AccessFlags.LIST_SIZE) != 0;
	}
	@Override
	public Slot slot() {
		if ((flags & AccessFlags.SLOT_ITEM) != 0) return Slot.ITEM;
		if ((flags & AccessFlags.SLOT_FLUID) != 0) return Slot.FLUID;
		return Slot.NONE;
	}
}
