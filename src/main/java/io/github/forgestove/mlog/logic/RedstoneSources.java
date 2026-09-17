package io.github.forgestove.mlog.logic;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;
/**
 * 虚拟充能表：记下某个坐标「收到了」多少红石强度。
 * <p>MC 的红石是「问」出来的——方块判断自己被没被充能，走的是
 * {@code SignalGetter#hasNeighborSignal} 与 {@code #getBestNeighborSignal}，
 * 答案由周围方块的类型定死，运行时改不了。所以 {@code control power} 靠写方块状态没用
 * （写完就被红石更新算回去），只能另存一张表，由
 * {@link io.github.forgestove.mlog.mixin.SignalGetterMixin} 拦一道查表，
 * 让目标坐标「以为自己被充能了」。
 * <p>登记的是<b>输入</b>不是输出：目标自己会动（门开、活塞推），但不会向外辐射信号，
 * 周围方块一点都不会受影响。
 * <p>不存盘：处理器开机本来就会重跑一遍代码，{@code control power} 会把值重新写上。
 */
public final class RedstoneSources {
	/** 按维度分开存，各自的坐标空间不互通。 */
	private static final Map<ResourceKey<Level>, Map<BlockPos, Source>> SOURCES = new HashMap<>();
	/**
	 * 记下某个坐标「收到」的强度，并让这个方块自己重新评估一遍。
	 *
	 * @param owner 下这条指令的处理器；它被拆掉时，这条登记要跟着失效
	 * @return 强度确实变了才返回 {@code true}
	 */
	public static boolean set(ServerLevel level, BlockPos pos, BlockPos owner, int strength) {
		var sources = SOURCES.computeIfAbsent(level.dimension(), key -> new HashMap<>());
		var previous = sources.get(pos);
		if (strength <= 0) {
			if (previous == null) return false;
			sources.remove(pos);
		} else {
			if (previous != null && previous.strength() == strength && previous.owner().equals(owner)) return false;
			// 存副本：调用方给的可能是可变的 BlockPos
			sources.put(pos.immutable(), new Source(owner.immutable(), strength));
		}
		// 只惊动目标自己。登记的是输入不是输出，周围方块不该有任何变化
		level.neighborChanged(pos, level.getBlockState(pos).getBlock(), pos);
		return true;
	}
	/** 主人被拆掉时清掉它留下的全部登记，并让那些坐标重新评估一遍。 */
	public static void removeAll(ServerLevel level, BlockPos owner) {
		var sources = SOURCES.get(level.dimension());
		if (sources == null || sources.isEmpty()) return;
		var affected = new ArrayList<BlockPos>();
		sources.entrySet().removeIf(entry -> {
			if (!entry.getValue().owner().equals(owner)) return false;
			affected.add(entry.getKey());
			return true;
		});
		// 不清的话，这些方块会一直以为自己还被充着能
		for (var pos : affected) level.neighborChanged(pos, level.getBlockState(pos).getBlock(), pos);
	}
	/** @return 该坐标的虚拟充能强度，没登记过则 0。 */
	public static int signal(ResourceKey<Level> dimension, BlockPos pos) {
		if (SOURCES.isEmpty()) return 0;
		var sources = SOURCES.get(dimension);
		if (sources == null || sources.isEmpty()) return 0;
		var source = sources.get(pos);
		return source == null ? 0 : source.strength();
	}
	/** 服务器停下时清空，换个存档不会串。 */
	public static void clear() {
		SOURCES.clear();
	}
	/** 一条登记：谁下的指令，强度多少。 */
	private record Source(BlockPos owner, int strength) {}
}
