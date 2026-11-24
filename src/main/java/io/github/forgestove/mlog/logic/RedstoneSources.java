package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
public class RedstoneSources {
	private static final Map<ResourceKey<Level>, Map<BlockPos, Source>> SOURCES = new HashMap<>();
	/**
	 * 充能目标位置的一面。
	 *
	 * @param level    所在世界
	 * @param target   充能位置
	 * @param face     充能面，指向中继器/比较器的背面才能驱动它们
	 * @param owner    所属处理器位置，被拆除后该信号源随之失效
	 * @param strength 信号强度，小于等于 0 时清除该信号源
	 * @return 是否发生变化
	 */
	public static boolean set(ServerLevel level, BlockPos target, Direction face, BlockPos owner, int strength) {
		var source = target.relative(face);
		if (!put(level, source, face.getOpposite(), owner, strength)) return false;
		wake(level, target, source);
		return true;
	}
	/**
	 * 仅更新数据表，不触发方块更新。
	 *
	 * @return 是否发生变化
	 */
	private static boolean put(ServerLevel level, BlockPos pos, Direction face, BlockPos owner, int strength) {
		var sources = SOURCES.computeIfAbsent(level.dimension(), key -> new HashMap<>());
		var key = pos.immutable();
		var previous = sources.get(key);
		if (strength <= 0) {
			if (previous == null) return false;
			sources.remove(key);
		} else {
			if (previous != null && previous.isUnchanged(face, owner, strength)) return false;
			sources.put(key, new Source(face, owner.immutable(), strength));
		}
		return true;
	}
	/**
	 * 令接收方重新评估自身。
	 *
	 * @param level  所在世界
	 * @param target 充能位置
	 * @param owner  来源位置
	 */
	private static void wake(ServerLevel level, BlockPos target, BlockPos owner) {
		if (!level.isLoaded(target)) return;
		var block = level.getBlockState(owner).getBlock();
		level.neighborChanged(target, block, owner);
		level.updateNeighborsAt(target, block);
	}
	/**
	 * 充能该方块的所有面。
	 *
	 * @param level    所在世界
	 * @param pos      充能位置
	 * @param owner    来源位置
	 * @param strength 0~15，小于等于 0 时清除六个面
	 * @return 是否发生变化
	 */
	public static boolean charge(ServerLevel level, BlockPos pos, BlockPos owner, int strength) {
		var changed = false;
		for (var face : Direction.values()) changed |= put(level, pos.relative(face), face.getOpposite(), owner, strength);
		if (!changed) return false;
		wake(level, pos, pos);
		return true;
	}
	/**
	 * 清除某个处理器留下的全部信号源，并使受影响的方块重新计算充能状态。
	 *
	 * @param level 所在世界
	 * @param owner 来源位置
	 */
	public static void removeAll(ServerLevel level, BlockPos owner) {
		var sources = SOURCES.get(level.dimension());
		if (sources == null || sources.isEmpty()) return;
		var affected = new HashMap<BlockPos, Direction>();
		sources.entrySet().removeIf(entry -> {
			if (!entry.getValue().owner().equals(owner)) return false;
			affected.put(entry.getKey(), entry.getValue().face());
			return true;
		});
		affected.forEach((pos, face) -> wake(level, pos.relative(face), pos));
	}
	/**
	 * 查询某信号源朝某面发出的信号强度。
	 *
	 * @param dimension 所在维度
	 * @param owner     来源位置
	 * @param face      充能面
	 * @return 信号强度，没有该信号源时为 0
	 */
	public static int signal(ResourceKey<Level> dimension, BlockPos owner, Direction face) {
		var source = source(dimension, owner);
		return source == null || source.face() != face ? 0 : source.strength();
	}
	/**
	 * 取该坐标上的信号源。
	 *
	 * @param dimension 所在维度
	 * @param owner     来源位置
	 * @return 该坐标上的信号源，没有时为 null
	 */
	private static @Nullable Source source(ResourceKey<Level> dimension, BlockPos owner) {
		if (SOURCES.isEmpty()) return null;
		var sources = SOURCES.get(dimension);
		return sources == null || sources.isEmpty() ? null : sources.get(owner);
	}
	/**
	 * 服务器停止时清空，避免跨存档残留。
	 */
	public static void clear(ServerStoppedEvent ignoredEvent) {
		SOURCES.clear();
	}
	/**
	 * 信号源。
	 *
	 * @param face     充能面
	 * @param owner    来源位置
	 * @param strength 信号强度
	 */
	private record Source(Direction face, BlockPos owner, int strength) {
		/**
		 * 判断与既有信号源是否一致。
		 */
		boolean isUnchanged(Direction face, BlockPos owner, int strength) {
			return this.face == face && this.strength == strength && this.owner.equals(owner);
		}
	}
}
