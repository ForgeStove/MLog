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
import org.jetbrains.annotations.Nullable;

import java.util.List;
public class CreateSenseables {
	private static final List<ThresholdSwitchCompat> THRESHOLD_COMPAT = List.of(
		new FunctionalStorage(),
		new SophisticatedStorage(),
		new StorageDrawers()
	);
	@SuppressWarnings("unused")
	public static Senseable wrap(Senseable delegate, Level level, BlockPos pos, @Nullable BlockEntity be, @Nullable Direction side) {
		return be instanceof SmartBlockEntity smart ? new Adapter(delegate, smart, side) : delegate;
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
	private static final class Adapter extends ForwardingSenseable {
		private final SmartBlockEntity be;
		private final @Nullable Direction side;
		private Adapter(Senseable delegate, SmartBlockEntity be, @Nullable Direction side) {
			super(delegate);
			this.be = be;
			this.side = side;
		}
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
			var known = AccessRegistry.byName(access);
			if (known == null) return delegate.sense(access);
			// 传送带的物品存于传输清单，不经物品能力读取
			if (known == VanillaAccess.totalItems && be instanceof BeltBlockEntity belt) return beltItems(belt);
			var create = CreateAccess.byName(access);
			if (create == CreateAccess.value || create == CreateAccess.valueRow) {
				var settings = valueSettings();
				if (settings == null) return 0;
				var current = settings.getValueSettings();
				return create == CreateAccess.value ? current.value() : current.row();
			}
			if (create == null || !(be instanceof KineticBlockEntity kinetic)) return delegate.sense(access);
			return switch (create) {
				case speed -> kinetic.getSpeed();
				case stressImpact -> kinetic.calculateStressApplied();
				case stressCapacity -> kinetic.calculateAddedStressCapacity();
				case networkStress -> network(kinetic, false);
				case networkCapacity -> network(kinetic, true);
				case hasNetwork -> kinetic.hasNetwork() ? 1 : 0;
				case networkSize -> networkSize(kinetic);
				case networkSources -> networkSources(kinetic);
				case overstressed -> kinetic.isOverStressed() ? 1 : 0;
				default -> delegate.sense(access);
			};
		}
		@Override
		public Object senseObject(String access) {
			if (be instanceof BeltBlockEntity belt && VanillaAccess.byName(access) == VanillaAccess.firstItem) return beltFirstItem(belt);
			if (CreateAccess.byName(access) == CreateAccess.filter) return filter();
			return delegate.senseObject(access);
		}
		@Override
		public boolean control(
			String access,
			LVar value,
			@Nullable Direction face,
			@Nullable BlockPos owner,
			boolean privileged,
			int index
		) {
			if (!privileged && !AccessRegistry.controlAllowed().contains(access)) return false;
			if (Senseables.VALUE.equals(access)) {
				var settings = valueSettings();
				if (settings == null) return false;
				settings.setValueSettings(null, new ValueSettings(index, (int) value.num()), false);
				return true;
			}
			if (Senseables.FILTER.equals(access)) {
				var filtering = be.getBehaviour(FilteringBehaviour.TYPE);
				if (filtering == null) return false;
				var item = itemOf(value.obj());
				var stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
				if (face == null) filtering.setFilter(stack);
				else filtering.setFilter(face, stack);
				return true;
			}
			return delegate.control(access, value, face, owner, privileged, index);
		}
		@Override
		public void print(String text) {
			if (be instanceof DisplayLinkBlockEntity link) {
				CreateDisplays.print(link, text);
				return;
			}
			delegate.print(text);
		}
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
