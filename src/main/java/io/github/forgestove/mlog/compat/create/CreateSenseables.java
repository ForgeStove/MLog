package io.github.forgestove.mlog.compat.create;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
/**
 * Create 方块的读数适配器，只在装了 Create 时被 {@link MLogSenseables#at} 调到。
 * <p>读数全部走 Create 的公开 API：该公开的它都公开了（{@code getSpeed()}、{@code calculateStressApplied()}、
 * 应力表的 {@code getNetworkStress()}/可比的 {@code calculateStress()}、传送带的 {@code getTransportedItems()}），
 * 只有少数 private 字段（软管滑轮的内部罐、应力网络的缓存值）拿不到，而它们都有公开的等效值，
 * 所以这边一个 mixin 都用不上。
 * <p>本类直接引用 Create 的类，没装 Create 的环境不该被加载——调用点按 {@link MLogSenseables#CREATE} 挡着。
 * <p>属性名的出处：{@code speed} / {@code stressImpact} / {@code stressCapacity} 对齐 Create 的
 * {@code generic.speed}、{@code tooltip.stressImpact}、{@code tooltip.capacityProvided}，
 * {@code networkStress} / {@code networkCapacity} / {@code overstressed} 对齐应力表的
 * {@code gui.stressometer.title} / {@code display_source.kinetic_stress.max} / {@code gui.stressometer.overstressed}。
 */
public final class CreateSenseables {
	/** @return 这个方块实体的读数适配器；不是 Create 的动能方块时返回 {@code null}。 */
	public static @Nullable MLogSenseable at(Level level, BlockPos pos, @Nullable BlockEntity be) {
		// 传送带也算动能方块，它的物品在下面另判
		return be instanceof KineticBlockEntity kinetic ? new Adapter(level, pos, kinetic) : null;
	}
	/** Create 的动能方块：转速与应力走公开 API，其余读数退回通用适配器。 */
	private record Adapter(Level level, BlockPos pos, KineticBlockEntity be) implements MLogSenseable {
		@Override
		public double sense(String access) {
			var known = LAccess.byName(access);
			if (known == null) return generic().sense(access);
			return switch (known) {
				// 转速（RPM），转速表读的就是它
				case speed -> be.getSpeed();
				// 这个方块消耗 / 提供的应力量，对应 Create 的「应力影响」「应力量」
				case stressImpact -> be.calculateStressApplied();
				case stressCapacity -> be.calculateAddedStressCapacity();
				// 整张传动网络的应力量与上限，对应 Create 的应力表
				case networkStress -> network(false);
				case networkCapacity -> network(true);
				case overstressed -> be.isOverStressed() ? 1 : 0;
				// 传送带上的物品挂在传输清单里，不走物品能力，也不在方块状态里
				case totalItems -> be instanceof BeltBlockEntity belt ? beltItems(belt) : generic().sense(access);
				default -> generic().sense(access);
			};
		}
		@Override
		public Object senseObject(String access) {
			if (be instanceof BeltBlockEntity belt && LAccess.byName(access) == LAccess.firstItem) return beltFirstItem(belt);
			return generic().senseObject(access);
		}
		/** 其余能力（写方块状态、读处理器变量、往告示牌写字）都还是通用那套。 */
		@Override
		public boolean control(
			String access, double value, @Nullable Direction face, boolean strong, @Nullable BlockPos owner, boolean privileged
		) {
			return generic().control(access, value, face, strong, owner, privileged);
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
			generic().print(text);
		}
		private MLogSenseable generic() {
			return MLogSenseables.generic(level, pos);
		}
		/**
		 * @return 整张传动网络当前的应力量或应力上限。
		 * 	<p>方块没接上传动时给 0：{@code getOrCreateNetwork()} 名字里带 create，为一次静态读数据
		 * 	现建一个网络对象没必要，先用 {@code hasNetwork()} 挡一下。
		 */
		private double network(boolean capacity) {
			if (!be.hasNetwork()) return 0;
			var network = be.getOrCreateNetwork();
			return capacity ? network.calculateCapacity() : network.calculateStress();
		}
	}
	/** @return 传送带上的传输清单；方块实体还没就绪时返回空表。 */
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
