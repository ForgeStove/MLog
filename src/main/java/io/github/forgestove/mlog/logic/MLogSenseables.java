package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.compat.create.CreateSenseables;
import io.github.forgestove.mlog.core.MLogMods;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities.*;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * 把 MC 的方块与实体适配成 {@link MLogSenseable}。方块的方块实体若自身实现了该接口，则优先采用其读数。
 * <p>实体分支供 {@code query} 查出的单位使用：除位置、类型、名字、血量外均无读数。
 */
public final class MLogSenseables {
	/** 红石输出强度的属性名。它不是方块状态，单独走 {@link RedstoneSources}。 */
	public static final String POWER = "power";
	/** Create 值设置的属性名，写法同 {@link LAccess#value}。 */
	public static final String VALUE = "value";
	/** Create 过滤槽的属性名，写法同 {@link LAccess#filter}。 */
	public static final String FILTER = "filter";
	/** 名字到内容（物品 / 流体 / 方块）的解析结果，解析不出时为 {@link #NONE}；注册表运行期不变，可长期复用。 */
	private static final Map<String, Object> CONTENTS = new ConcurrentHashMap<>();
	/** 解析不出内容的哨兵值。 */
	private static final Object NONE = new Object();
	/** Create 是否加载：整个进程不变，缓存下来免得每条 sensor 都查一次模组列表。 */
	private static @Nullable Boolean createLoaded;
	private static boolean createLoaded() {
		var cached = createLoaded;
		if (cached == null) createLoaded = cached = MLogMods.create.isLoaded();
		return cached;
	}
	/** @return 坐标上的可感测对象，无法感测则返回 {@code null}。 */
	public static @Nullable MLogSenseable at(Level level, BlockPos pos) {
		return at(level, pos, null);
	}
	/**
	 * @param side 读取所用的面。仅 Create 的过滤槽按面区分，其余读法一律忽略；
	 *             容器六个面返回同一份读数
	 * @return 坐标上的可感测对象，无法感测则返回 {@code null}
	 */
	public static @Nullable MLogSenseable at(Level level, BlockPos pos, @Nullable Direction side) {
		if (!level.isLoaded(pos)) return null;
		var be = level.getBlockEntity(pos);
		if (be instanceof MLogSenseable senseable) return senseable;
		// 常量是 false 时此分支不执行，compat 的类因此不会被加载（它直接引用 Create 的类）
		if (createLoaded()) {
			var create = CreateSenseables.at(level, pos, be, side);
			if (create != null) return create;
		}
		return new BlockAdapter(level, pos, be);
	}
	/**
	 * 绕过方块实体自身的 {@link MLogSenseable} 实现，直接使用通用适配器。
	 * <p>处理器读自身时必须走此路径，否则 {@link #at} 会查出自身并回调进来，造成无限递归。
	 */
	public static MLogSenseable generic(Level level, BlockPos pos) {
		return new BlockAdapter(level, pos, level.getBlockEntity(pos));
	}
	/** @return 实体的适配器，供 {@code query} 查出来的单位使用。 */
	public static MLogSenseable of(Entity entity) {
		return new EntityAdapter(entity);
	}
	/**
	 * @return 名字是否为物品、流体或方块名，即下拉列表的图标墙与 {@code draw image} 所需的名称，按它读取的是储量
	 * 	<p>汇编时据此把 {@code @create:honey} 识别为字符串常量（见 {@code LAssembler#var}），
	 * 	执行时据此在 {@code stored()} 中查注册表，两处判据必须一致
	 */
	public static boolean isContent(String name) {
		var id = ResourceLocation.tryParse(name);
		return id != null && (
			BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.FLUID.containsKey(id) || BuiltInRegistries.BLOCK.containsKey(id)
		);
	}
	/** 按名字读方块状态属性。布尔转 0/1，方向与枚举转序号，方块没有该属性时返回 0。 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static double property(BlockState state, String name) {
		for (var raw : state.getProperties()) {
			if (!raw.getName().equals(name)) continue;
			return switch ((Comparable<?>) state.getValue((Property) raw)) {
				case Boolean value -> value ? 1 : 0;
				case Direction value -> value.get3DDataValue();
				case Enum<?> value -> value.ordinal();
				case Number value -> value.doubleValue();
				default -> 0;
			};
		}
		return 0;
	}
	/**
	 * 把数值写回方块状态属性，是 {@link #property} 的反向操作。
	 * <p>布尔按非零转真，方向按 3D 序号取，枚举按下标取（越界回绕），数字原样写。
	 *
	 * @return 方块没有这个属性、或给的值不是它的合法取值时返回 {@code false}
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static boolean setProperty(Level level, BlockPos pos, BlockState state, String name, double value) {
		for (var raw : state.getProperties()) {
			if (!raw.getName().equals(name)) continue;
			var property = (Property) raw;
			var next = switch (state.getValue(property)) {
				case Boolean ignored -> value != 0;
				case Direction ignored -> Direction.from3DDataValue((int) value);
				case Enum<?> current -> nextEnum(current, (int) value);
				case Number ignored -> (int) value;
				default -> null;
			};
			if (next == null || !property.getPossibleValues().contains(next)) return false;
			// 用 setBlockAndUpdate 而非直接改状态：相邻方块与渲染都需随之更新
			level.setBlockAndUpdate(pos, withProperty(state, property, next));
			return true;
		}
		return false;
	}
	/** @return 枚举中按下标取的那一项，越界则回绕；空枚举返回 {@code null}。 */
	private static @Nullable Object nextEnum(Enum<?> current, int index) {
		var constants = current.getDeclaringClass().getEnumConstants();
		return constants == null || constants.length == 0 ? null : constants[Math.floorMod(index, constants.length)];
	}
	/**
	 * {@code setValue} 的签名为 {@code <T, V extends T>}，而 {@code property} 在此处已为 raw 类型，
	 * {@code T} 无法推断，只能整体降级为 raw 调用。
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState withProperty(BlockState state, Property property, Object value) {
		// 两侧类型均被擦除为 Comparable，形参此处也须强转才能通过编译
		return (BlockState) ((StateHolder) state).setValue(property, (Comparable) value);
	}
	/** 原版方块的通用适配器，面不参与读数。 */
	private record BlockAdapter(Level level, BlockPos pos, @Nullable BlockEntity be) implements MLogSenseable {
		@Override
		public double sense(String access) {
			var state = level.getBlockState(pos);
			var known = LAccess.byName(access);
			// 非内置属性：先判断是否为具体物品/流体名（获取数据弹窗中的两组），
			// 均不是时再按方块状态属性名查询，方块无该属性则返回 0
			if (known == null) {
				var stored = stored(access);
				return stored >= 0 ? stored : property(state, access);
			}
			return switch (known) {
				case x -> pos.getX();
				case y -> pos.getY();
				case z -> pos.getZ();
				case id -> BuiltInRegistries.BLOCK.getId(state.getBlock());
				case solid -> state.getCollisionShape(level, pos).isEmpty() ? 0 : 1;
				case air -> state.isAir() ? 1 : 0;
				case hardness -> state.getDestroySpeed(level, pos);
				case hasBlockEntity -> state.hasBlockEntity() ? 1 : 0;
				case raining -> level.isRainingAt(pos) ? 1 : 0;
				case light -> state.getLightEmission(level, pos);
				case blockLight -> level.getBrightness(LightLayer.BLOCK, pos);
				case skyLight -> level.getBrightness(LightLayer.SKY, pos);
				// 红石：邻近最强信号 / 本方块的输出强度 / 比较器读数
				case redstone -> level.getBestNeighborSignal(pos);
				case emittedRedstone -> emittedRedstone(state);
				case comparator -> state.getAnalogOutputSignal(level, pos);
				case progress -> progress();
				case totalItems -> totalItems();
				case itemCapacity -> itemCapacity();
				case emptySlots -> emptySlots();
				case hasFluid -> state.getFluidState().isEmpty() ? 0 : 1;
				case energy -> energy(false);
				case energyCapacity -> energy(true);
				// 其余为方块状态属性：按同名属性读取
				default -> property(state, access);
			};
		}
		/**
		 * 按名字读方块里该物品或流体的储量。
		 *
		 * @return 名字不是注册项时返回 {@code -1}，以便与「是注册项但数量为零」的 {@code 0} 相区分
		 */
		private double stored(String name) {
			var content = CONTENTS.computeIfAbsent(name, BlockAdapter::content);
			if (content == NONE) return -1;
			if (content instanceof Item item) return countOf(item);
			if (content instanceof Fluid fluid) return amountOf(fluid);
			// 方块按其物品形态计数，无物品形态者不计数
			var item = ((Block) content).asItem();
			return item == Items.AIR ? -1 : countOf(item);
		}
		/** @return 名字对应的物品、流体或方块，都不是时返回 {@link #NONE}。 */
		private static Object content(String name) {
			var id = ResourceLocation.tryParse(name);
			if (id == null) return NONE;
			if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.get(id);
			if (BuiltInRegistries.FLUID.containsKey(id)) return BuiltInRegistries.FLUID.get(id);
			if (BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.get(id);
			return NONE;
		}
		/** 六个方向里最强的输出信号。 */
		private double emittedRedstone(BlockState state) {
			var best = 0;
			for (var direction : Direction.values()) best = Math.max(best, state.getSignal(level, pos, direction));
			return best;
		}
		/** 熔炉系的烧炼进度，归一化到 0~1；其他方块恒为 0。 */
		private double progress() {
			if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return 0;
			var total = furnace.dataAccess.get(AbstractFurnaceBlockEntity.DATA_COOKING_TOTAL_TIME);
			return total <= 0 ? 0 : (double) furnace.dataAccess.get(AbstractFurnaceBlockEntity.DATA_COOKING_PROGRESS) / total;
		}
		private double totalItems() {
			var items = items();
			if (items == null) return 0;
			var total = 0;
			for (var i = 0; i < items.getSlots(); i++) total += items.getStackInSlot(i).getCount();
			return total;
		}
		private double itemCapacity() {
			var items = items();
			if (items == null) return 0;
			var capacity = 0;
			for (var i = 0; i < items.getSlots(); i++) capacity += items.getSlotLimit(i);
			return capacity;
		}
		private double emptySlots() {
			var items = items();
			if (items == null) return 0;
			var empty = 0;
			for (var i = 0; i < items.getSlots(); i++) if (items.getStackInSlot(i).isEmpty()) empty++;
			return empty;
		}
		/** @return 能量存储的已存量或容量，没有该能力时返回 0。 */
		private double energy(boolean capacity) {
			var storage = face(EnergyStorage.BLOCK);
			if (storage == null) return 0;
			return capacity ? storage.getMaxEnergyStored() : storage.getEnergyStored();
		}
		private double countOf(Item item) {
			var items = items();
			if (items == null) return 0;
			var count = 0;
			for (var i = 0; i < items.getSlots(); i++) {
				var stack = items.getStackInSlot(i);
				if (stack.is(item)) count += stack.getCount();
			}
			return count;
		}
		private double amountOf(Fluid fluid) {
			var handler = face(FluidHandler.BLOCK);
			if (handler == null) return 0;
			var amount = 0;
			for (var i = 0; i < handler.getTanks(); i++) {
				var stack = handler.getFluidInTank(i);
				if (stack.getFluid() == fluid) amount += stack.getAmount();
			}
			return amount;
		}
		@Override
		public Object senseObject(String access) {
			var block = level.getBlockState(pos).getBlock();
			if (!(LAccess.byName(access) instanceof LAccess known)) return NO_SENSED;
			return switch (known) {
				case type -> block;
				case name -> block.getName().getString();
				case firstItem -> firstItem();
				default -> NO_SENSED;
			};
		}
		private @Nullable Item firstItem() {
			var items = items();
			if (items == null) return null;
			for (var i = 0; i < items.getSlots(); i++) {
				var stack = items.getStackInSlot(i);
				if (!stack.isEmpty()) return stack.getItem();
			}
			return null;
		}
		/**
		 * @return 物品槽视图，既无容器也无能力时返回 {@code null}。
		 * 	<p>原版容器以 {@link InvWrapper} 包装，方块自身的物品能力（多数模组采用，Create 的
		 *    {@code SmartInventory} 即如此）直接使用；两条路径合一，下列各读数无须分情况重复实现。
		 */
		private @Nullable IItemHandler items() {
			if (be instanceof Container container) return new InvWrapper(container);
			return face(ItemHandler.BLOCK);
		}
		/**
		 * @return 该坐标上的方块能力，没有时返回 {@code null}。
		 * 	<p>先查询不带面的能力；仅在某个面注册能力的方块（机器的进料口 / 出料口常如此）
		 * 	再逐面查询，取第一个非空结果——同一能力注册于六个面时只会重复取得同一实例，不会重复计数。
		 */
		private <T> @Nullable T face(BlockCapability<T, @Nullable Direction> capability) {
			// 方块状态与方块实体都已知，交给带它们的重载，否则每查一次都要在内部重查一遍
			var state = level.getBlockState(pos);
			var unsided = level.getCapability(capability, pos, state, be, null);
			if (unsided != null) return unsided;
			for (var direction : Direction.values()) {
				var sided = level.getCapability(capability, pos, state, be, direction);
				if (sided != null) return sided;
			}
			return null;
		}
		/** @return 指定槽位的物品，非容器、越界或空槽返回 {@code null} */
		@Override
		public @Nullable Item itemAt(int slot) {
			var items = items();
			if (items == null || slot < 0 || slot >= items.getSlots()) return null;
			var stack = items.getStackInSlot(slot);
			return stack.isEmpty() ? null : stack.getItem();
		}
		/** @return 指定罐位的流体，无流体能力、越界或空罐返回 {@code null} */
		@Override
		public @Nullable Fluid fluidAt(int tank) {
			var handler = face(FluidHandler.BLOCK);
			if (handler == null || tank < 0 || tank >= handler.getTanks()) return null;
			var stack = handler.getFluidInTank(tank);
			// 空罐的 getFluid() 为 Fluids.EMPTY（真实注册项），须排除
			return stack.isEmpty() ? null : stack.getFluid();
		}
		@Override
		public boolean control(
			String access,
			LVar value,
			@Nullable Direction face,
			boolean strong,
			@Nullable BlockPos owner,
			boolean privileged,
			int index
		) {
			// 非特权处理器只能修改白名单内的属性，其余名字不扫描方块状态——否则一条 control
			// 即可修改任意方块的任意状态。特权处理器（世界处理器）跳过此检查
			if (!privileged && !LAccess.controlAllowed().contains(access)) return false;
			// power 不是方块状态，而是「该坐标应发出多少红石」——写入虚拟源表，由 Mixin 参与信号判定
			if (POWER.equals(access)) {
				if (!(level instanceof ServerLevel serverLevel) || owner == null) return false;
				var strength = Math.clamp((int) value.num(), 0, 15);
				// 未给出方向时六个面均接入源，给出时仅该面接入
				return face == null
					? RedstoneSources.charge(serverLevel, pos, strong, owner, strength)
					: RedstoneSources.set(serverLevel, pos, face, strong, owner, strength);
			}
			return setProperty(level, pos, level.getBlockState(pos), access, value.num());
		}
		@Override
		public void print(String text) {
			if (!(be instanceof SignBlockEntity sign)) return;
			// 告示牌正面仅四行，多余部分丢弃；内容未变则不写入，避免每 tick 推送一次方块更新
			var lines = text.split("\n", -1);
			var current = sign.getFrontText();
			var next = current;
			for (var i = 0; i < current.getMessages(false).length; i++) {
				var want = Component.literal(i < lines.length ? lines[i] : "");
				if (want.equals(current.getMessage(i, false))) continue;
				next = next.setMessage(i, want, want);
			}
			if (next != current) sign.setText(next, true);
		}
	}
	/** 实体的通用适配器：只认和实体有关的属性，其余一律 0 / 无输出。 */
	private record EntityAdapter(Entity entity) implements MLogSenseable {
		@Override
		public double sense(String access) {
			if (!(LAccess.byName(access) instanceof LAccess known)) return 0;
			return switch (known) {
				case x -> entity.getX();
				case y -> entity.getY();
				case z -> entity.getZ();
				case id -> BuiltInRegistries.ENTITY_TYPE.getId(entity.getType());
				case health -> health(false);
				case maxHealth -> health(true);
				case dead -> entity.isRemoved() || entity instanceof LivingEntity living && living.isDeadOrDying() ? 1 : 0;
				default -> 0;
			};
		}
		/** @return 当前血量或血量上限，不是生物时都是 0。 */
		private double health(boolean max) {
			if (!(entity instanceof LivingEntity living)) return 0;
			return max ? living.getMaxHealth() : living.getHealth();
		}
		@Override
		public Object senseObject(String access) {
			if (!(LAccess.byName(access) instanceof LAccess known)) return NO_SENSED;
			return switch (known) {
				case type -> entity.getType();
				case name -> entity.getDisplayName().getString();
				default -> NO_SENSED;
			};
		}
	}
}
