package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/**
 * 虚拟红石源：登记某坐标朝某面发出的信号强度，由 {@link io.github.forgestove.mlog.mixin.SignalGetterMixin} 拦截红石查询后查表；MC 的红石信号是查询式的，写入方块状态会被红石更新改回，故改由该表供电。
 * 一条登记只发弱充能，接收方自身会响应但不会被判定为充能方块，须充能某方块时在其六个邻位各放一个源（{@link #charge}）；该表不写入存档，处理器重新编译代码后由 {@code control redstone} 重新写入。
 */
public class RedstoneSources {
	/** 维度 → 坐标 → 登记；坐标以不可变副本存储。 */
	private static final Map<ResourceKey<Level>, Map<BlockPos, Source>> SOURCES = new HashMap<>();
	/**
	 * 在 {@code pos} 的 {@code face} 一侧放置一个虚拟源向 {@code pos} 自身供电；{@code face} 指向中继器/比较器的背面才能驱动它们；{@code owner} 被拆除后该登记随之失效，{@code strength
	 * } 为 0~15、小于等于 0 时清除登记。
	 */
	public static boolean set(ServerLevel level, BlockPos pos, Direction face, BlockPos owner, int strength) {
		var source = pos.relative(face);
		// 表内记录的面是发射方向的反面，即朝向受电方
		if (!put(level, source, face.getOpposite(), owner, strength)) return false;
		wake(level, pos, source);
		return true;
	}
	/** 仅更新数据表，不触发方块更新。 */
	private static boolean put(ServerLevel level, BlockPos pos, Direction face, BlockPos owner, int strength) {
		var sources = SOURCES.computeIfAbsent(level.dimension(), key -> new HashMap<>());
		// 存副本：调用方传入的 BlockPos 可能可变
		var key = pos.immutable();
		var previous = sources.get(key);
		if (strength <= 0) {
			if (previous == null) return false;
			sources.remove(key);
		} else {
			if (previous != null && previous.same(face, owner, strength)) return false;
			sources.put(key, new Source(face, owner.immutable(), strength));
		}
		return true;
	}
	private static void wake(ServerLevel level, BlockPos receiver, BlockPos from) {
		if (!level.isLoaded(receiver)) return;
		var block = level.getBlockState(from).getBlock();
		level.neighborChanged(receiver, block, from);
		level.updateNeighborsAt(receiver, block);
	}
	/** 把 {@code pos} 上的方块当作被充能：等价于在它的六个邻位各放一个朝它发射的虚拟源；{@code owner} 被拆除后这些登记随之失效，{@code strength} 为 0~15、小于等于 0 时清除六个面的登记。 */
	public static boolean charge(ServerLevel level, BlockPos pos, BlockPos owner, int strength) {
		var changed = false;
		for (var face : Direction.values()) changed |= put(level, pos.relative(face), face.getOpposite(), owner, strength);
		// 六个源朝向同一个接收方，只须唤醒一次
		if (!changed) return false;
		wake(level, pos, pos);
		return true;
	}
	/** 清除某个处理器留下的全部登记；清除后相关坐标须重新评估，否则这些方块会持续被判定为充能。 */
	public static void removeAll(ServerLevel level, BlockPos owner) {
		var sources = SOURCES.get(level.dimension());
		if (sources == null || sources.isEmpty()) return;
		var affected = new HashMap<BlockPos, Direction>();
		sources.entrySet().removeIf(entry -> {
			if (!entry.getValue().owner().equals(owner)) return false;
			affected.put(entry.getKey(), entry.getValue().face());
			return true;
		});
		// 不清除则这些方块会持续被判定为充能
		affected.forEach((pos, face) -> wake(level, pos.relative(face), pos));
	}
	/** 该方向发出的信号强度；没登记过则返回 0。 */
	public static int signal(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
		var source = source(dimension, pos);
		return source == null || source.face() != face ? 0 : source.strength();
	}
	/** 没登记过时返回 {@code null}。 */
	private static @Nullable Source source(ResourceKey<Level> dimension, BlockPos pos) {
		if (SOURCES.isEmpty()) return null;
		var sources = SOURCES.get(dimension);
		return sources == null || sources.isEmpty() ? null : sources.get(pos);
	}
	/** 该表为静态字段，不清空将跨存档残留。 */
	public static void clear(ServerStoppedEvent ignoredEvent) {
		SOURCES.clear();
	}
	private record Source(Direction face, BlockPos owner, int strength) {
		/** 与既有登记完全一致时返回 {@code true}，此时无须触发更新。 */
		boolean same(Direction face, BlockPos owner, int strength) {
			return this.face == face && this.strength == strength && this.owner.equals(owner);
		}
	}
}
