package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/**
 * 虚拟红石源：登记某个坐标「朝某个面」发出多少信号。
 * <p>MC 的红石信号是查询式的：方块是否被充能由周围方块的类型决定，经
 * {@code SignalGetter#getSignal} 与 {@code #getDirectSignal} 求得，运行时无法改写。
 * 因此 {@code control power} 不能通过写方块状态实现（写入后会被红石更新改回），
 * 只能另存一张表，由 {@link io.github.forgestove.mlog.mixin.SignalGetterMixin} 拦截并查表。
 * <p>一条登记等价于在 {@code pos} 旁放置一个只朝 {@code face} 供电的源。默认只发弱充能：
 * 接收方自身会响应（如亮灯、开门、推动活塞），但不会被充能，也就不会影响其相邻方块。
 * 开启 {@code strong} 才是完整的源：接收方若为红石导体即被强充能，进而向四周辐射。
 * 要使某个方块被充能，就在其六个邻位各放置一个源（{@link #charge}）。
 * <p>不存盘：处理器开机时会重跑代码，{@code control power} 会重新写入。
 */
public final class RedstoneSources {
	/** 按维度分开存，各自的坐标空间不互通。 */
	private static final Map<ResourceKey<Level>, Map<BlockPos, Source>> SOURCES = new HashMap<>();
	/**
	 * 在 {@code pos} 的 {@code face} 一侧放置一个虚拟源，朝 {@code pos} 供电，等价于在该面接一根线。
	 * <p>即 {@link #charge} 六面之一，受电方始终是 {@code pos} 自身；
	 * 要驱动中继器/比较器，须将 {@code face} 指向其背面（它只从背面读取输入）。
	 *
	 * @param strong 是否也发强充能那一份；为假时只有 {@code pos} 自己被弱充能
	 * @param owner  下这条指令的处理器；它被拆掉时，这条登记要跟着失效
	 * @return 强度确实变了才返回 {@code true}
	 */
	public static boolean set(ServerLevel level, BlockPos pos, Direction face, boolean strong, BlockPos owner, int strength) {
		var source = pos.relative(face);
		// 表内记录的面是发射方向的反面，即朝向受电方
		if (!put(level, source, face.getOpposite(), strong, owner, strength)) return false;
		wake(level, pos, source);
		return true;
	}
	/** 仅更新数据表，不触发方块更新。 */
	private static boolean put(ServerLevel level, BlockPos pos, Direction face, boolean strong, BlockPos owner, int strength) {
		var sources = SOURCES.computeIfAbsent(level.dimension(), key -> new HashMap<>());
		// 存副本：调用方给的可能是可变的 BlockPos
		var key = pos.immutable();
		var previous = sources.get(key);
		if (strength <= 0) {
			if (previous == null) return false;
			sources.remove(key);
		} else {
			if (previous != null && previous.same(face, strong, owner, strength)) return false;
			sources.put(key, new Source(face, strong, owner.immutable(), strength));
		}
		return true;
	}
	/** 令接收方重新评估自身；它若为红石导体，相邻方块读到的强充能也一并重算。 */
	private static void wake(ServerLevel level, BlockPos receiver, BlockPos from) {
		if (!level.isLoaded(receiver)) return;
		var block = level.getBlockState(from).getBlock();
		level.neighborChanged(receiver, block, from);
		level.updateNeighborsAt(receiver, block);
	}
	/**
	 * 把 {@code pos} 上的方块当作被充能：等价于在它的六个邻位各放一个朝它发射的虚拟源。
	 *
	 * @param strong 是否一并发出强充能；为假时仅该方块自身响应，不向四周辐射
	 * @param owner  下这条指令的处理器；它被拆掉时，这些登记要跟着失效
	 * @return 强度确实变了才返回 {@code true}
	 */
	public static boolean charge(ServerLevel level, BlockPos pos, boolean strong, BlockPos owner, int strength) {
		var changed = false;
		for (var face : Direction.values()) changed |= put(level, pos.relative(face), face.getOpposite(), strong, owner, strength);
		// 六个源对着的是同一个接收方，唤醒一次就够
		if (!changed) return false;
		wake(level, pos, pos);
		return true;
	}
	/** 发起方被拆除时清除其留下的全部登记，并令相关坐标重新评估。 */
	public static void removeAll(ServerLevel level, BlockPos owner) {
		var sources = SOURCES.get(level.dimension());
		if (sources == null || sources.isEmpty()) return;
		var affected = new HashMap<BlockPos, Direction>();
		sources.entrySet().removeIf(entry -> {
			if (!entry.getValue().owner().equals(owner)) return false;
			affected.put(entry.getKey(), entry.getValue().face());
			return true;
		});
		// 不清除则这些方块会持续认为自己处于充能状态
		affected.forEach((pos, face) -> wake(level, pos.relative(face), pos));
	}
	/**
	 * @param direct 是否为强充能查询（{@code getDirectSignal}）；源未开启强充能时给 0
	 * @return 该坐标朝该面发射的强度，没登记过则 0
	 */
	public static int signal(ResourceKey<Level> dimension, BlockPos pos, Direction face, boolean direct) {
		var source = source(dimension, pos);
		if (source == null || source.face() != face) return 0;
		// 弱充能那一份谁都给，强充能只有开了的源才发
		return direct && !source.strong() ? 0 : source.strength();
	}
	/** @return 该坐标上的源，没登记过则 {@code null}。 */
	private static @Nullable Source source(ResourceKey<Level> dimension, BlockPos pos) {
		if (SOURCES.isEmpty()) return null;
		var sources = SOURCES.get(dimension);
		return sources == null || sources.isEmpty() ? null : sources.get(pos);
	}
	/** 服务器停止时清空，避免跨存档残留。 */
	public static void clear() {
		SOURCES.clear();
	}
	/** 一条登记：朝哪面发射、是否强充能、由谁发起、强度多少。 */
	private record Source(Direction face, boolean strong, BlockPos owner, int strength) {
		/** @return 与参数是否为同一条登记；未变化则无须触发更新。 */
		boolean same(Direction face, boolean strong, BlockPos owner, int strength) {
			return this.face == face && this.strong == strong && this.strength == strength && this.owner.equals(owner);
		}
	}
}
