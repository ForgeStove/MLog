package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import org.jetbrains.annotations.Nullable;
/** 把 MC 方块适配成 {@link MLogSenseable}。方块实体若自己实现了该接口，则优先用它的读数。 */
public final class MLogSenseables {
	/** 红石输出强度的属性名。它不是方块状态，单独走 {@link RedstoneSources}。 */
	public static final String POWER = "power";
	/** @return 坐标上的可感测对象，无法感测则返回 {@code null}。 */
	public static @Nullable MLogSenseable at(Level level, BlockPos pos) {
		if (!level.isLoaded(pos)) return null;
		var be = level.getBlockEntity(pos);
		if (be instanceof MLogSenseable senseable) return senseable;
		return new BlockAdapter(level, pos, be);
	}
	/**
	 * 绕过方块实体自身的 {@link MLogSenseable} 实现，直接用通用适配器。
	 * <p>处理器读自身时必须走这条，否则 {@link #at} 会查出自己再回调进来，无限递归。
	 */
	public static MLogSenseable generic(Level level, BlockPos pos) {
		return new BlockAdapter(level, pos, level.getBlockEntity(pos));
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
	/**
	 * {@code setValue} 的签名是 {@code <T, V extends T>}，而 {@code property} 到这里已经是 raw 的了，
	 * {@code T} 推断不出来，只能整体降级成 raw 调用。
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState withProperty(BlockState state, Property property, Object value) {
		// 两边的类型都被擦成 Comparable，形参这边也得跟着强转才过得了编译
		return (BlockState) ((StateHolder) state).setValue(property, (Comparable) value);
	}
	/** @return 枚举里按下标取的那一项，越界就绕回来；空枚举返回 {@code null}。 */
	private static @Nullable Object nextEnum(Enum<?> current, int index) {
		var constants = current.getDeclaringClass().getEnumConstants();
		return constants == null || constants.length == 0 ? null : constants[Math.floorMod(index, constants.length)];
	}
	/** 原版方块的通用适配器。 */
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
				case id -> Block.getId(state);
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
				case fluidLevel -> state.getFluidState().getAmount();
				case hasFluid -> state.getFluidState().isEmpty() ? 0 : 1;
				case energy -> energy(false);
				case energyCapacity -> energy(true);
				// 剩下的是方块状态属性：按同名属性读
				default -> property(state, access);
			};
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
			var container = container();
			if (container == null) return 0;
			var total = 0;
			for (var i = 0; i < container.getContainerSize(); i++) total += container.getItem(i).getCount();
			return total;
		}
		private double itemCapacity() {
			var container = container();
			return container == null ? 0 : (double) container.getContainerSize() * container.getMaxStackSize();
		}
		private double emptySlots() {
			var container = container();
			if (container == null) return 0;
			var empty = 0;
			for (var i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).isEmpty()) empty++;
			return empty;
		}
		/** @return 能量存储的已存量或容量，没有该能力时返回 0。 */
		private double energy(boolean capacity) {
			var storage = level.getCapability(EnergyStorage.BLOCK, pos, null);
			if (storage == null) return 0;
			return capacity ? storage.getMaxEnergyStored() : storage.getEnergyStored();
		}
		private @Nullable Container container() {
			return be instanceof Container c ? c : null;
		}
		/**
		 * 按名字读方块里该物品或流体的储量，对齐 Mindustry 的 {@code items.get(item)}。
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
		private double countOf(Item item) {
			var container = container();
			if (container == null) return 0;
			var count = 0;
			for (var i = 0; i < container.getContainerSize(); i++) {
				var stack = container.getItem(i);
				if (stack.is(item)) count += stack.getCount();
			}
			return count;
		}
		private double amountOf(Fluid fluid) {
			var handler = level.getCapability(FluidHandler.BLOCK, pos, null);
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
		@Override
		public boolean control(String access, double value, @Nullable BlockPos owner) {
			if (LAccess.CONTROL_DENIED.contains(access)) return false;
			// power 不是方块状态，而是「这个坐标该收到多少红石」——写进虚拟充能表，由 Mixin 参与信号判定
			if (POWER.equals(access)) {
				if (!(level instanceof ServerLevel serverLevel) || owner == null) return false;
				return RedstoneSources.set(serverLevel, pos, owner, Math.clamp((int) value, 0, 15));
			}
			return setProperty(level, pos, level.getBlockState(pos), access, value);
		}
		private @Nullable Item firstItem() {
			var container = container();
			if (container == null) return null;
			for (var i = 0; i < container.getContainerSize(); i++) {
				var stack = container.getItem(i);
				if (!stack.isEmpty()) return stack.getItem();
			}
			return null;
		}
	}
}
