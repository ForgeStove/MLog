package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/**
 * 逻辑变量中对象值与 NBT 的互转，供内存方块等需要将值写入存档之处使用。
 * <p>类别见 {@link VarType}。
 * <p>无法识别的第三方对象记为空值，为明确的丢数据边界。
 */
public final class LVarIO {
	/** 槽位标签的三个键：分类、载荷、补充名（只有枚举和实体用得上）。 */
	private static final String NBT_TYPE = "t";
	private static final String NBT_VALUE = "v";
	private static final String NBT_CLASS = "c";
	private static final String NBT_POS = "pos";
	private static final String NBT_NAME = "name";
	private static final String NBT_OUTSIDE = "outside";
	private static final String NBT_VALID = "valid";
	/** 能还原的枚举类。会进入变量的目前只有 {@link LAccess}，留表以便扩展。 */
	private static final Map<String, Class<? extends Enum<?>>> ENUMS = Map.of(LAccess.class.getName(), LAccess.class);
	/** @return 槽位标签里的分类。 */
	public static VarType type(CompoundTag tag) {
		return VarType.byId(tag.getByte(NBT_TYPE));
	}
	/**
	 * 把一个值编码成槽位标签。
	 * <p>方块、物品、流体、实体类型都存注册名而不是注册表编号：编号跨存档、跨模组组合都不稳定。
	 * 枚举存常量名而不是序号：枚举中插入一项不会使旧存档整体错位。
	 */
	public static CompoundTag write(@Nullable Object value) {
		return switch (value) {
			case null -> slot(VarType.NULL, null);
			case Number number -> slot(VarType.NUMBER, DoubleTag.valueOf(number.doubleValue()));
			case String text -> slot(VarType.STRING, StringTag.valueOf(text));
			case Block block -> content(VarType.BLOCK, BuiltInRegistries.BLOCK.getKey(block));
			case Item item -> content(VarType.ITEM, BuiltInRegistries.ITEM.getKey(item));
			case Fluid fluid -> content(VarType.FLUID, BuiltInRegistries.FLUID.getKey(fluid));
			case EntityType<?> type -> content(VarType.ENTITY_TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(type));
			case Entity entity -> entity(entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
			case EntityRef ref -> entity(ref.uuid(), ref.type());
			// 方块实体存坐标：读回时经 MLogSenseables.at 仍能取到同一方块，优于直接丢弃。
			// @this 即处理器自身，进入此分支
			case BlockEntity be -> slot(VarType.BLOCK_POS, LongTag.valueOf(be.getBlockPos().asLong()));
			case BlockPos pos -> slot(VarType.BLOCK_POS, LongTag.valueOf(pos.asLong()));
			case LogicLink link -> {
				var body = new CompoundTag();
				body.putLong(NBT_POS, link.pos().asLong());
				body.putString(NBT_NAME, link.name());
				if (link.outside()) body.putBoolean(NBT_OUTSIDE, true);
				body.putBoolean(NBT_VALID, link.valid());
				yield slot(VarType.LINK, body);
			}
			case Enum<?> constant -> {
				var tag = slot(VarType.ENUM, StringTag.valueOf(constant.name()));
				tag.putString(NBT_CLASS, constant.getDeclaringClass().getName());
				yield tag;
			}
			case List<?> list -> {
				var body = new ListTag();
				for (var element : list) body.add(write(element));
				yield slot(VarType.LIST, body);
			}
			// 无法识别的对象没有存档形式，读回即为空值
			default -> slot(VarType.NULL, null);
		};
	}
	/** @return 解码出来的值；注册项已经没了、类名对不上时返回 {@code null}。 */
	public static @Nullable Object read(CompoundTag tag) {
		return switch (type(tag)) {
			case NUMBER -> tag.getDouble(NBT_VALUE);
			case STRING -> tag.getString(NBT_VALUE);
			case BLOCK -> content(BuiltInRegistries.BLOCK, tag);
			case ITEM -> content(BuiltInRegistries.ITEM, tag);
			case FLUID -> content(BuiltInRegistries.FLUID, tag);
			case ENTITY_TYPE -> content(BuiltInRegistries.ENTITY_TYPE, tag);
			// 此时实体通常尚未进入世界，仅记录 UUID 与类型，取用时再查询
			case ENTITY ->
				tag.hasUUID(NBT_VALUE) ? new EntityRef(tag.getUUID(NBT_VALUE), ResourceLocation.tryParse(tag.getString(NBT_CLASS))) : null;
			case BLOCK_POS -> BlockPos.of(tag.getLong(NBT_VALUE));
			case LINK -> {
				var body = tag.getCompound(NBT_VALUE);
				var pos = body.getLong(NBT_POS);
				yield new LogicLink(
					BlockPos.of(pos),
					body.getString(NBT_NAME),
					body.getBoolean(NBT_OUTSIDE),
					body.getBoolean(NBT_VALID)
				);
			}
			case ENUM -> constant(tag.getString(NBT_CLASS), tag.getString(NBT_VALUE));
			case LIST -> {
				var values = new ArrayList<>();
				for (var element : tag.getList(NBT_VALUE, Tag.TAG_COMPOUND)) values.add(read((CompoundTag) element));
				yield values;
			}
			default -> null;
		};
	}
	/** @return 只带分类和载荷的槽位标签。 */
	private static CompoundTag slot(VarType type, @Nullable Tag value) {
		var tag = new CompoundTag();
		tag.putByte(NBT_TYPE, type.id());
		if (value != null) tag.put(NBT_VALUE, value);
		return tag;
	}
	/** @return 载荷写注册名的槽位标签。 */
	private static CompoundTag content(VarType type, ResourceLocation id) {
		return slot(type, StringTag.valueOf(id.toString()));
	}
	/** @return 载荷写 UUID、补充名写类型的槽位标签。 */
	private static CompoundTag entity(UUID uuid, ResourceLocation type) {
		var tag = new CompoundTag();
		tag.putByte(NBT_TYPE, VarType.ENTITY.id());
		tag.putUUID(NBT_VALUE, uuid);
		tag.putString(NBT_CLASS, type.toString());
		return tag;
	}
	/** @return 存档里写着的注册项；注册名认不出来、或者这一项已经没了时返回 {@code null}。 */
	private static <T> @Nullable T content(Registry<T> registry, CompoundTag tag) {
		var id = ResourceLocation.tryParse(tag.getString(NBT_VALUE));
		return id == null ? null : registry.getOptional(id).orElse(null);
	}
	/** @return 枚举常量；类名不在表里、常量名对不上时返回 {@code null}。 */
	private static @Nullable Object constant(String className, String name) {
		var type = ENUMS.get(className);
		if (type == null) return null;
		for (var value : type.getEnumConstants()) if (value.name().equals(name)) return value;
		return null;
	}
	/**
	 * 存档中的实体值：仅存 UUID 与类型，取用时才在世界中查找。
	 * <p>区块加载顺序不保证实体已进入世界，若直接存为实体，该槽位读档后即为空。
	 */
	public record EntityRef(UUID uuid, ResourceLocation type) {
		public EntityRef(Entity entity) {
			this(entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
		}
		/** @return 还活着的那个实体；已经没了、或者同 UUID 换成了别的类型时返回 {@code null}。 */
		public @Nullable Entity resolve(@Nullable Level level) {
			if (!(level instanceof ServerLevel serverLevel) || type == null) return null;
			var entity = serverLevel.getEntity(uuid);
			return entity != null && type.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())) ? entity : null;
		}
	}
}
