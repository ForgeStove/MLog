package io.github.forgestove.mlog.compat.create;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;
/**
 * Create 方块的读数适配器，由 {@link MLogSenseables#at} 在装了 Create 时取用。
 * <p>读数均走 Create 的公开 API，不依赖 mixin。
 * <p>本类直接引用 Create 的类，未安装时不得加载：调用点以 {@link MLogSenseables#CREATE} 挡住。
 * <p>属性名出处：{@code speed}、{@code stressImpact}、{@code stressCapacity} 对齐
 * {@code generic.speed}、{@code tooltip.stressImpact}、{@code tooltip.capacityProvided}；
 * {@code networkStress}、{@code networkCapacity}、{@code overstressed} 对齐
 * {@code gui.stressometer.title}、{@code display_source.kinetic_stress.max}、{@code gui.stressometer.overstressed}；
 * {@code filter} 即 {@code logistics.filter}。
 */
public final class CreateSenseables {
	/** @return 读数适配器，非 Create 方块实体时返回 {@code null} */
	public static @Nullable MLogSenseable at(Level level, BlockPos pos, @Nullable BlockEntity be, @Nullable Direction side) {
		return be instanceof SmartBlockEntity smart ? new Adapter(level, pos, smart, side) : null;
	}
	/** Create 方块实体：能读的走公开 API，其余退回通用适配器。 */
	private record Adapter(Level level, BlockPos pos, SmartBlockEntity be, @Nullable Direction side) implements MLogSenseable {
		@Override
		public double sense(String access) {
			var known = LAccess.byName(access);
			if (known == null) return generic().sense(access);
			// 传送带的物品在传输清单里，不经物品能力
			if (known == LAccess.totalItems && be instanceof BeltBlockEntity belt) return beltItems(belt);
			if (known == LAccess.value || known == LAccess.valueRow) {
				var settings = valueSettings();
				if (settings == null) return 0;
				var current = settings.getValueSettings();
				return known == LAccess.value ? current.value() : current.row();
			}
			if (!(be instanceof KineticBlockEntity kinetic)) return generic().sense(access);
			return switch (known) {
				// 转速（RPM），对应转速表
				case speed -> kinetic.getSpeed();
				// 本方块消耗 / 提供的应力量
				case stressImpact -> kinetic.calculateStressApplied();
				case stressCapacity -> kinetic.calculateAddedStressCapacity();
				// 整张传动网络的用量与上限
				case networkStress -> network(kinetic, false);
				case networkCapacity -> network(kinetic, true);
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
		/**
		 * 过滤槽与值设置走 Create 自己的接口，其余退回通用适配器。
		 * <p>{@link MLogSenseable} 新增方法时这里同样要转发，否则被包一层后读不到。
		 */
		@Override
		public boolean control(
			String access, LVar value, @Nullable Direction face, boolean strong, @Nullable BlockPos owner, boolean privileged, int index
		) {
			// 白名单与通用适配器同样要挡
			if (!privileged && !LAccess.controlAllowed().contains(access)) return false;
			if (MLogSenseables.VALUE.equals(access)) {
				var settings = valueSettings();
				if (settings == null) return false;
				// 该接口要玩家参数，但实现里只 setValue 与播服务端音效，传 null 即可
				settings.setValueSettings(null, new ValueSettings(index, (int) value.num()), false);
				return true;
			}
			if (MLogSenseables.FILTER.equals(access)) {
				var filtering = be.getBehaviour(FilteringBehaviour.TYPE);
				if (filtering == null) return false;
				// 值为物品名或物品对象，空值即清空过滤
				var item = itemOf(value.obj());
				var stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
				// 没给面设的是默认那份；给了面由 SidedFilteringBehaviour 落到那一面
				if (face == null) filtering.setFilter(stack);
				else filtering.setFilter(face, stack);
				return true;
			}
			return generic().control(access, value, face, strong, owner, privileged, index);
		}
		@Override
		public boolean read(LVar position, LVar output, boolean privileged) {
			return generic().read(position, output, privileged);
		}
		@Override
		public boolean write(LVar position, LVar value, boolean privileged) {
			return generic().write(position, value, privileged);
		}
		/** 文本交由显示链接器转发；其余方块沿用通用实现（告示牌等直接写入方块自身）。 */
		@Override
		public void print(String text) {
			if (be instanceof DisplayLinkBlockEntity link) {
				CreateDisplays.print(link, text);
				return;
			}
			generic().print(text);
		}
		/** 按序号那两样同样转发，否则被包一层后读不到容器内容。 */
		@Override
		public @Nullable Item itemAt(int slot) {
			return generic().itemAt(slot);
		}
		@Override
		public @Nullable Fluid fluidAt(int tank) {
			return generic().fluidAt(tank);
		}
		private MLogSenseable generic() {
			return MLogSenseables.generic(level, pos);
		}
		/**
		 * @return 过滤槽里设的物品，没设或没有过滤槽时返回 {@code null}
		 * 	<p>{@code SidedFilteringBehaviour} 按给定面取，未给定用默认
		 */
		private @Nullable Item filter() {
			var filtering = be.getBehaviour(FilteringBehaviour.TYPE);
			if (filtering == null) return null;
			ItemStack stack = side == null ? filtering.getFilter() : filtering.getFilter(side);
			return stack == null || stack.isEmpty() ? null : stack.getItem();
		}
		/**
		 * @return 本机器的值设置（扳手滚轮那种），没有则返回 {@code null}
		 * 	<p>Create 按准星位置挑，逻辑侧取第一个启用的；常规机器只有一个
		 */
		private @Nullable ValueSettingsBehaviour valueSettings() {
			for (var behaviour : be.getAllBehaviours())
				if (behaviour instanceof ValueSettingsBehaviour settings && settings.isActive()) return settings;
			return null;
		}
		/**
		 * @return 整张传动网络的应力量或应力上限，未接上传动时为 0
		 * 	<p>先用 {@code hasNetwork()} 挡住：{@code getOrCreateNetwork()} 会现建网络对象
		 */
		private static double network(KineticBlockEntity be, boolean capacity) {
			if (!be.hasNetwork()) return 0;
			var network = be.getOrCreateNetwork();
			return capacity ? network.calculateCapacity() : network.calculateStress();
		}
	}
	/**
	 * @return 值里的物品：物品名查注册表，物品对象直接用；空值或认不出的名字返回 {@code null}
	 */
	private static @Nullable Item itemOf(@Nullable Object value) {
		if (value instanceof Item item) return item;
		if (!(value instanceof String name)) return null;
		var id = ResourceLocation.tryParse(name);
		return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
	}
	/** @return 传送带上的传输清单，方块实体未就绪时为空表 */
	private static List<TransportedItemStack> beltStacks(BeltBlockEntity belt) {
		var inventory = belt.getInventory();
		return inventory == null ? List.of() : inventory.getTransportedItems();
	}
	/** @return 传送带上所有物品加起来的件数。 */
	private static double beltItems(BeltBlockEntity belt) {
		var total = 0;
		for (var transported : beltStacks(belt)) {
			if (transported == null || transported.stack == null) continue;
			total += transported.stack.getCount();
		}
		return total;
	}
	/** @return 传送带上第一个非空格的物品，带子上没有东西时返回 {@code null}。 */
	private static @Nullable Item beltFirstItem(BeltBlockEntity belt) {
		for (var transported : beltStacks(belt)) {
			if (transported == null || transported.stack == null || transported.stack.isEmpty()) continue;
			return transported.stack.getItem();
		}
		return null;
	}
}
