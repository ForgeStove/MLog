package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage;
import org.jetbrains.annotations.Nullable;
/** 把 MC 方块适配成 {@link MLogSenseable}。方块实体若自己实现了该接口，则优先用它的读数。 */
public final class MLogSenseables {
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
	/** 原版方块的通用适配器。 */
	private record BlockAdapter(Level level, BlockPos pos, @Nullable BlockEntity be) implements MLogSenseable {
		@Override
		public double sense(String access) {
			var state = level.getBlockState(pos);
			var known = LAccess.byName(access);
			// 不是内置属性，按方块状态属性名去查，方块没有该属性就返回 0
			if (known == null) return property(state, access);
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
