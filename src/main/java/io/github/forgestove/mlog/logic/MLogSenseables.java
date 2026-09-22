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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities.*;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;
/**
 * 把 MC 的方块与实体适配成 {@link MLogSenseable}。方块的方块实体若自己实现了该接口，则优先用它的读数。
 * <p>实体这一路是给 {@code query} 查出来的单位用的：除了位置、类型、名字、血量，别的都没读数。
 */
public final class MLogSenseables {
	/** 红石输出强度的属性名。它不是方块状态，单独走 {@link RedstoneSources}。 */
	public static final String POWER = "power";
	/** Create 值设置的属性名，写法同 {@link LAccess#value}。 */
	public static final String VALUE = "value";
	/** Create 过滤槽的属性名，写法同 {@link LAccess#filter}。 */
	public static final String FILTER = "filter";
	/** @return 坐标上的可感测对象，无法感测则返回 {@code null}。 */
	public static @Nullable MLogSenseable at(Level level, BlockPos pos) {
		return at(level, pos, null);
	}
	/**
	 * @param side 从哪一面读。只有 Create 的过滤槽真按面分，其余读法一律不看它——
	 *             容器六面给的是同一份
	 * @return 坐标上的可感测对象，无法感测则返回 {@code null}
	 */
	public static @Nullable MLogSenseable at(Level level, BlockPos pos, @Nullable Direction side) {
		if (!level.isLoaded(pos)) return null;
		var be = level.getBlockEntity(pos);
		if (be instanceof MLogSenseable senseable) return senseable;
		// 常量是 false 时这支不执行，compat 的类也就不会被加载（它直接引用 Create 的类）
		if (MLogMods.create.isLoaded()) {
			var create = CreateSenseables.at(level, pos, be, side);
			if (create != null) return create;
		}
		return new BlockAdapter(level, pos, be);
	}
	/**
	 * 绕过方块实体自身的 {@link MLogSenseable} 实现，直接用通用适配器。
	 * <p>处理器读自身时必须走这条，否则 {@link #at} 会查出自己再回调进来，无限递归。
	 */
	public static MLogSenseable generic(Level level, BlockPos pos) {
		return new BlockAdapter(level, pos, level.getBlockEntity(pos));
	}
	/** @return 实体的适配器，供 {@code query} 查出来的单位使用。 */
	public static MLogSenseable of(Entity entity) {
		return new EntityAdapter(entity);
	}
	/**
	 * @return 名字是否为物品或流体名，即下拉里那两张图标墙给的那种，按它读的是储量
	 * 	<p>汇编时靠它把 {@code @create:honey} 认成字符串常量（见 {@code LAssembler#var}），
	 * 	执行时靠它在 {@code stored()} 里查注册表，两处必须是同一批
	 */
	public static boolean isContent(String name) {
		var id = ResourceLocation.tryParse(name);
		return id != null && (BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.FLUID.containsKey(id));
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
	 * <p>布尔按非零转真，方向按 3D 序号取，枚举按下标取（越界绕回来），数字原样写。
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
			// 走 setBlockAndUpdate 而不是直接改状态：相邻方块与渲染都要跟着更新
			level.setBlockAndUpdate(pos, withProperty(state, property, next));
			return true;
		}
		return false;
	}
	/** @return 枚举里按下标取的那一项，越界就绕回来；空枚举返回 {@code null}。 */
	private static @Nullable Object nextEnum(Enum<?> current, int index) {
		var constants = current.getDeclaringClass().getEnumConstants();
		return constants == null || constants.length == 0 ? null : constants[Math.floorMod(index, constants.length)];
	}
	/**
	 * {@code setValue} 的签名是 {@code <T, V extends T>}，而 {@code property} 到这里已经是 raw 的了，
	 * {@code T} 推断不出来，只能整体降级成 raw 调用。
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState withProperty(BlockState state, Property property, Object value) {
		// 两边的类型都被擦成 Comparable，形参这边也得跟着强转才过得了编译
		return (BlockState) ((StateHolder) state).setValue(property, (Comparable) value);
	}
	/** 原版方块的通用适配器，面不参与读数。 */
	private record BlockAdapter(Level level, BlockPos pos, @Nullable BlockEntity be) implements MLogSenseable {
		@Override
		public double sense(String access) {
			var state = level.getBlockState(pos);
			var known = LAccess.byName(access);
			// 不是内置属性：先看是不是具体物品/流体名（获取数据弹窗里那两组），
			// 都不是才按方块状态属性名去查，方块没有该属性就返回 0
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
				// 剩下的是方块状态属性：按同名属性读
				default -> property(state, access);
			};
		}
		/**
		 * 按名字读方块里该物品或流体的储量。
		 *
		 * @return 名字不是注册项时返回 {@code -1}，好和「是注册项但一个都没有」的 {@code 0} 区分开
		 */
		private double stored(String name) {
			var id = ResourceLocation.tryParse(name);
			if (id == null) return -1;
			if (BuiltInRegistries.ITEM.containsKey(id)) return countOf(BuiltInRegistries.ITEM.get(id));
			if (BuiltInRegistries.FLUID.containsKey(id)) return amountOf(BuiltInRegistries.FLUID.get(id));
			return -1;
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
		 * @return 物品槽视图，没有容器也没有能力时返回 {@code null}。
		 * 	<p>原版容器包一层 {@link InvWrapper}，方块自己的物品能力（多数模组用这个，Create 的
		 *    {@code SmartInventory} 就是）直接用，两条路合成一条，下面几个读数不用分情况写两遍。
		 */
		private @Nullable IItemHandler items() {
			if (be instanceof Container container) return new InvWrapper(container);
			return face(ItemHandler.BLOCK);
		}
		/**
		 * @return 这个坐标上的方块能力，没有时返回 {@code null}。
		 * 	<p>先问不带面的那一份；只在某个面上挂了能力的方块（机器的进料口 / 出料口常这么写）
		 * 	再挨个面问，取第一个非空的——同一个能力挂满六个面时也只是重复拿到同一个实例，不会重复计数。
		 */
		private <T> @Nullable T face(BlockCapability<T, @Nullable Direction> capability) {
			var unsided = level.getCapability(capability, pos, null);
			if (unsided != null) return unsided;
			for (var direction : Direction.values()) {
				var sided = level.getCapability(capability, pos, direction);
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
			// 空罐的 getFluid() 是 Fluids.EMPTY（真注册项），必须挡掉
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
			// 非特权处理器只改得动白名单里的属性，别的名字连方块状态都不去扫——不然一句 control
			// 就能改掉任意方块的任意状态。特权处理器（世界处理器）跳过这一层
			if (!privileged && !LAccess.controlAllowed().contains(access)) return false;
			// power 不是方块状态，而是「这个坐标该发出多少红石」——写进虚拟源表，由 Mixin 参与信号判定
			if (POWER.equals(access)) {
				if (!(level instanceof ServerLevel serverLevel) || owner == null) return false;
				var strength = Math.clamp((int) value.num(), 0, 15);
				// 没给方向就是六个面都接上源，给了就是只在那一面接一根
				return face == null
					? RedstoneSources.charge(serverLevel, pos, strong, owner, strength)
					: RedstoneSources.set(serverLevel, pos, face, strong, owner, strength);
			}
			return setProperty(level, pos, level.getBlockState(pos), access, value.num());
		}
		@Override
		public void print(String text) {
			if (!(be instanceof SignBlockEntity sign)) return;
			// 告示牌正面只有四行，多的丢掉；内容没变就不写，免得每 tick 都推一次方块更新
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
