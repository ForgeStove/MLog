package io.github.forgestove.mlog.logic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/**
 * 逻辑变量里的对象值与 NBT 的互转，供内存方块这类要把值写进存档的地方用。
 * <p>分类和变量表里那套显示分类（{@code MicroProcessorBlockEntity#varType}）<b>不是一回事</b>：
 * 那边是给人看的（单位与它的类型合成一档、认不出来的都算「对象」），这边要能反过来还原，
 * 所以每一类都得单独解得出来。
 * <p>认不出来的第三方对象记成空值——这是明确的丢数据边界。
 */
public final class LVarIO {
	/** 值的分类。数字也占一类：列表元素里会出现数字。 */
	public static final byte TYPE_NULL = 0, TYPE_NUMBER = 1, TYPE_STRING = 2, TYPE_BLOCK = 3, TYPE_ITEM = 4, TYPE_FLUID = 5,
		TYPE_ENTITY_TYPE = 6, TYPE_ENTITY = 7, TYPE_BLOCK_POS = 8, TYPE_LINK = 9, TYPE_ENUM = 10, TYPE_LIST = 11;
	/** 槽位标签的三个键：分类、载荷、补充名（只有枚举和实体用得上）。 */
	private static final String NBT_TYPE = "t", NBT_VALUE = "v", NBT_CLASS = "c";
	private static final String NBT_OFFSET = "offset", NBT_NAME = "name";
	/** 能还原的枚举类。会进变量的目前只有 {@link LAccess}，留张表方便以后加。 */
	private static final Map<String, Class<? extends Enum<?>>> ENUMS = Map.of(
		LAccess.class.getName(),
		LAccess.class
	);
	/** @return 槽位标签里的分类。 */
	public static byte type(CompoundTag tag) {
		return tag.getByte(NBT_TYPE);
	}
	/**
	 * 把一个值编码成槽位标签。
	 * <p>方块、物品、流体、实体类型都存<b>注册名</b>而不是注册表编号：编号跨存档、跨模组组合都不稳定。
	 * 枚举存<b>常量名</b>而不是序号：枚举里插一项不会把老存档整个错位。
	 */
	public static CompoundTag write(@Nullable Object value) {
		return switch (value) {
			case null -> slot(TYPE_NULL, null);
			case Number number -> slot(TYPE_NUMBER, DoubleTag.valueOf(number.doubleValue()));
			case String text -> slot(TYPE_STRING, StringTag.valueOf(text));
			case Block block -> content(TYPE_BLOCK, BuiltInRegistries.BLOCK.getKey(block));
			case Item item -> content(TYPE_ITEM, BuiltInRegistries.ITEM.getKey(item));
			case Fluid fluid -> content(TYPE_FLUID, BuiltInRegistries.FLUID.getKey(fluid));
			case EntityType<?> type -> content(TYPE_ENTITY_TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(type));
			case Entity entity -> entity(entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
			case EntityRef ref -> entity(ref.uuid(), ref.type());
			// 方块实体存坐标：读回来经 MLogSenseables.at 还能取到同一个方块，比整只丢掉强。
			// @this 就是处理器自己，走的就是这一支
			case BlockEntity be -> slot(TYPE_BLOCK_POS, LongTag.valueOf(be.getBlockPos().asLong()));
			case BlockPos pos -> slot(TYPE_BLOCK_POS, LongTag.valueOf(pos.asLong()));
			case LogicLink link -> {
				var body = new CompoundTag();
				body.putLong(NBT_OFFSET, link.offset().asLong());
				body.putString(NBT_NAME, link.name());
				yield slot(TYPE_LINK, body);
			}
			case Enum<?> constant -> {
				var tag = slot(TYPE_ENUM, StringTag.valueOf(constant.name()));
				tag.putString(NBT_CLASS, constant.getDeclaringClass().getName());
				yield tag;
			}
			case List<?> list -> {
				var body = new ListTag();
				for (var element : list) body.add(write(element));
				yield slot(TYPE_LIST, body);
			}
			// 认不出来的对象没有存档形式，读回来就是空值
			default -> slot(TYPE_NULL, null);
		};
	}
	/** @return 解码出来的值；注册项已经没了、类名对不上时返回 {@code null}。 */
	public static @Nullable Object read(CompoundTag tag) {
		return switch (type(tag)) {
			case TYPE_NUMBER -> tag.getDouble(NBT_VALUE);
			case TYPE_STRING -> tag.getString(NBT_VALUE);
			case TYPE_BLOCK -> content(BuiltInRegistries.BLOCK, tag);
			case TYPE_ITEM -> content(BuiltInRegistries.ITEM, tag);
			case TYPE_FLUID -> content(BuiltInRegistries.FLUID, tag);
			case TYPE_ENTITY_TYPE -> content(BuiltInRegistries.ENTITY_TYPE, tag);
			// 实体这时候多半还没进世界，先只记下 UUID 和类型，真要用的时候再查
			case TYPE_ENTITY -> tag.hasUUID(NBT_VALUE)
				? new EntityRef(tag.getUUID(NBT_VALUE), ResourceLocation.tryParse(tag.getString(NBT_CLASS)))
				: null;
			case TYPE_BLOCK_POS -> BlockPos.of(tag.getLong(NBT_VALUE));
			case TYPE_LINK -> {
				var body = tag.getCompound(NBT_VALUE);
				yield new LogicLink(BlockPos.of(body.getLong(NBT_OFFSET)), body.getString(NBT_NAME));
			}
			case TYPE_ENUM -> constant(tag.getString(NBT_CLASS), tag.getString(NBT_VALUE));
			case TYPE_LIST -> {
				var values = new ArrayList<>();
				for (var element : tag.getList(NBT_VALUE, Tag.TAG_COMPOUND)) values.add(read((CompoundTag) element));
				yield values;
			}
			default -> null;
		};
	}
	/** @return 只带分类和载荷的槽位标签。 */
	private static CompoundTag slot(byte type, @Nullable Tag value) {
		var tag = new CompoundTag();
		tag.putByte(NBT_TYPE, type);
		if (value != null) tag.put(NBT_VALUE, value);
		return tag;
	}
	/** @return 载荷写注册名的槽位标签。 */
	private static CompoundTag content(byte type, ResourceLocation id) {
		return slot(type, StringTag.valueOf(id.toString()));
	}
	/** @return 载荷写 UUID、补充名写类型的槽位标签。 */
	private static CompoundTag entity(UUID uuid, ResourceLocation type) {
		var tag = new CompoundTag();
		tag.putByte(NBT_TYPE, TYPE_ENTITY);
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
	 * 存档里的实体值：只存 UUID 和类型，取用的时候才去世界里找。
	 * <p>区块加载的顺序不保证实体已经进了世界，直接写死成实体的话那个槽读档就空了。
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
