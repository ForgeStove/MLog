package io.github.forgestove.mlog.compat.create;
import com.simibubi.create.compat.thresholdSwitch.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.Senseables.SlotSpace;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;
public class CreateSenseables {
	/** 存量转信器给这些模组的仓储定制了每槽空间。 */
	private static final List<ThresholdSwitchCompat> THRESHOLD_COMPAT = List.of(
		new FunctionalStorage(), new SophisticatedStorage(), new StorageDrawers()
	);
	public static @Nullable Senseable at(Level level, BlockPos pos, @Nullable BlockEntity be, @Nullable Direction side) {
		return be instanceof SmartBlockEntity smart ? new Adapter(level, pos, smart, side) : null;
	}
	/**
	 * 该方块属于转信器的兼容模组时返回其每槽空间。
	 * 否则返回 {@code null}，改用通用实现。
	 */
	public static @Nullable SlotSpace slotSpace(@Nullable BlockEntity be) {
		if (be == null) return null;
		for (var compat : THRESHOLD_COMPAT) if (compat.isFromThisMod(be)) return compat::getSpaceInSlot;
		return null;
	}
	private static @Nullable Item itemOf(@Nullable Object value) {
		if (value instanceof Item item) return item;
		if (!(value instanceof String name)) return null;
		var id = ResourceLocation.tryParse(name);
		return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
	}
	private static double beltItems(BeltBlockEntity belt) {
		var total = 0;
		for (var transported : beltStacks(belt)) {
			if (transported == null || transported.stack == null) continue;
			total += transported.stack.getCount();
		}
		return total;
	}
	private static List<TransportedItemStack> beltStacks(BeltBlockEntity belt) {
		var inventory = belt.getInventory();
		return inventory == null ? List.of() : inventory.getTransportedItems();
	}
	private static @Nullable Item beltFirstItem(BeltBlockEntity belt) {
		for (var transported : beltStacks(belt)) {
			if (transported == null || transported.stack == null || transported.stack.isEmpty()) continue;
			return transported.stack.getItem();
		}
		return null;
	}
	private record Adapter(Level level, BlockPos pos, SmartBlockEntity be, @Nullable Direction side) implements Senseable {
		/**
		 * 未接入网络时返回 0。
		 * 调用前须先用 {@code hasNetwork()} 判断，{@code getOrCreateNetwork()} 会即时创建网络对象。
		 */
		private static double network(KineticBlockEntity be, boolean capacity) {
			if (!be.hasNetwork()) return 0;
			var network = be.getOrCreateNetwork();
			return capacity ? network.calculateCapacity() : network.calculateStress();
		}
		private static double networkSize(KineticBlockEntity be) {
			return be.hasNetwork() ? be.getOrCreateNetwork().getSize() : 0;
		}
		private static double networkSources(KineticBlockEntity be) {
			return be.hasNetwork() ? be.getOrCreateNetwork().sources.size() : 0;
		}
		@Override
		public double sense(String access) {
			var known = LAccess.byName(access);
			if (known == null) return generic().sense(access);
			// 传送带的物品存于传输清单，不经物品能力读取
			if (known == LAccess.totalItems && be instanceof BeltBlockEntity belt) return beltItems(belt);
			if (known == LAccess.value || known == LAccess.valueRow) {
				var settings = valueSettings();
				if (settings == null) return 0;
				var current = settings.getValueSettings();
				return known == LAccess.value ? current.value() : current.row();
			}
			if (!(be instanceof KineticBlockEntity kinetic)) return generic().sense(access);
			return switch (known) {
				case speed -> kinetic.getSpeed();
				case stressImpact -> kinetic.calculateStressApplied();
				case stressCapacity -> kinetic.calculateAddedStressCapacity();
				case networkStress -> network(kinetic, false);
				case networkCapacity -> network(kinetic, true);
				case hasNetwork -> kinetic.hasNetwork() ? 1 : 0;
				case networkSize -> networkSize(kinetic);
				case networkSources -> networkSources(kinetic);
				case overstressed -> kinetic.isOverStressed() ? 1 : 0;
				default -> generic().sense(access);
			};
		}
		@Override
		public Object senseObject(String access) {
			if (be instanceof BeltBlockEntity belt && LAccess.byName(access) == LAccess.firstItem) return beltFirstItem(belt);
			if (LAccess.byName(access) == LAccess.filter) return filter();
			return generic().senseObject(access);
		}
		@Override
		public @Nullable Item itemAt(int slot) {
			return generic().itemAt(slot);
		}
		@Override
		public @Nullable Fluid fluidAt(int tank) {
			return generic().fluidAt(tank);
		}
		/** {@link Senseable} 新增方法时须在此转发，否则经适配层后无法读取。 */
		@Override
		public boolean control(
			String access,
			LVar value,
			@Nullable Direction face,
			@Nullable BlockPos owner,
			boolean privileged,
			int index
		) {
			// 非特权时须校验访问白名单
			if (!privileged && !LAccess.controlAllowed().contains(access)) return false;
			if (Senseables.VALUE.equals(access)) {
				var settings = valueSettings();
				if (settings == null) return false;
				// 该接口声明了玩家参数，实现仅设置数值并播放服务端音效，可传 null
				settings.setValueSettings(null, new ValueSettings(index, (int) value.num()), false);
				return true;
			}
			if (Senseables.FILTER.equals(access)) {
				var filtering = be.getBehaviour(FilteringBehaviour.TYPE);
				if (filtering == null) return false;
				// 值可为物品名或物品对象
				// 空值即清空过滤
				var item = itemOf(value.obj());
				var stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
				// 未指定面时设置默认过滤
				// 指定面时由 SidedFilteringBehaviour 应用于该面
				if (face == null) filtering.setFilter(stack);
				else filtering.setFilter(face, stack);
				return true;
			}
			return generic().control(access, value, face, owner, privileged, index);
		}
		@Override
		public boolean read(LVar position, LVar output, boolean privileged) {
			return generic().read(position, output, privileged);
		}
		@Override
		public boolean write(LVar position, LVar value, boolean privileged) {
			return generic().write(position, value, privileged);
		}
		@Override
		public void print(String text) {
			if (be instanceof DisplayLinkBlockEntity link) {
				CreateDisplays.print(link, text);
				return;
			}
			generic().print(text);
		}
		private Senseable generic() {
			return Senseables.generic(level, pos);
		}
		/** Create 按准星位置选择值设置，逻辑侧取第一个启用的。 */
		private @Nullable ValueSettingsBehaviour valueSettings() {
			for (var behaviour : be.getAllBehaviours())
				if (behaviour instanceof ValueSettingsBehaviour settings && settings.isActive()) return settings;
			return null;
		}
		private @Nullable Item filter() {
			var filtering = be.getBehaviour(FilteringBehaviour.TYPE);
			if (filtering == null) return null;
			ItemStack stack = side == null ? filtering.getFilter() : filtering.getFilter(side);
			return stack == null || stack.isEmpty() ? null : stack.getItem();
		}
	}
}
