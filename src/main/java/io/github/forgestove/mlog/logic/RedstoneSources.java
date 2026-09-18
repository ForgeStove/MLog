package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/**
 * 虚拟红石源：登记某个坐标「朝某个面」发出多少信号。
 * <p>MC 的红石是「问」出来的——方块判断自己被没被充能，走的是
 * {@code SignalGetter#getSignal} 与 {@code #getDirectSignal}，答案由周围方块的类型定死，
 * 运行时改不了。所以 {@code control power} 靠写方块状态没用（写完就被红石更新算回去），
 * 只能另存一张表，由 {@link io.github.forgestove.mlog.mixin.SignalGetterMixin} 拦一道查表。
 * <p>一条登记等价于一根贴在 {@code pos} 上、只朝 {@code face} 那一面供电的拉杆。默认只给弱充能：
 * 对面那个方块自己会动（灯亮、门开、活塞推），但不会被充能，也就不会波及它旁边的方块。
 * 开了 {@code strong} 才是完整的拉杆——对面若是红石导体就会被强充能，于是再向四周辐射。
 * 要「某个方块被充能」就在它六个邻位各贴一根（{@link #charge}）。
 * <p>不存盘：处理器开机本来就会重跑一遍代码，{@code control power} 会把值重新写上。
 */
public final class RedstoneSources {
	/** 按维度分开存，各自的坐标空间不互通。 */
	private static final Map<ResourceKey<Level>, Map<BlockPos, Source>> SOURCES = new HashMap<>();
	/**
	 * 在 {@code pos} 的 {@code face} 那一侧放一个虚拟源，朝 {@code pos} 供电——等价于在那一面接了一根线。
	 * <p>这就是 {@link #charge} 六面里的一面，收电的始终是 {@code pos} 自己；
	 * 要驱动中继器/比较器就把 {@code face} 指到它的背面（它只从背面读输入）。
	 *
	 * @param strong 是否也发强充能那一份；为假时只有 {@code pos} 自己被弱充能
	 * @param owner  下这条指令的处理器；它被拆掉时，这条登记要跟着失效
	 * @return 强度确实变了才返回 {@code true}
	 */
	public static boolean set(ServerLevel level, BlockPos pos, Direction face, boolean strong, BlockPos owner, int strength) {
		var source = pos.relative(face);
		// 发射方向是朝回收电方，所以存进表里的面是它的反面
		if (!put(level, source, face.getOpposite(), strong, owner, strength)) return false;
		wake(level, pos, source);
		return true;
	}
	/** 只改表，不惊动世界。 */
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
	/** 让接收方重新评估自己；它若是红石导体，周围的方块读的是它的强充能，也一并重算。 */
	private static void wake(ServerLevel level, BlockPos receiver, BlockPos from) {
		if (!level.isLoaded(receiver)) return;
		var block = level.getBlockState(from).getBlock();
		level.neighborChanged(receiver, block, from);
		level.updateNeighborsAt(receiver, block);
	}
	/**
	 * 把 {@code pos} 上的方块当作被充能：等价于在它的六个邻位各放一个朝它发射的虚拟源。
	 *
	 * @param strong 是否也发强充能那一份；为假时只有这个方块自己会动，不会向四周辐射
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
	/** 主人被拆掉时清掉它留下的全部登记，并让那些坐标重新评估一遍。 */
	public static void removeAll(ServerLevel level, BlockPos owner) {
		var sources = SOURCES.get(level.dimension());
		if (sources == null || sources.isEmpty()) return;
		var affected = new HashMap<BlockPos, Direction>();
		sources.entrySet().removeIf(entry -> {
			if (!entry.getValue().owner().equals(owner)) return false;
			affected.put(entry.getKey(), entry.getValue().face());
			return true;
		});
		// 不清的话，这些方块会一直以为自己还被充着能
		affected.forEach((pos, face) -> wake(level, pos.relative(face), pos));
	}
	/**
	 * @param direct 问的是不是强充能那一份（{@code getDirectSignal}）；源没开强充能就给 0
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
	/** 服务器停下时清空，换个存档不会串。 */
	public static void clear() {
		SOURCES.clear();
	}
	/** 一条登记：朝哪面发射、要不要强充能、谁下的指令、强度多少。 */
	private record Source(Direction face, boolean strong, BlockPos owner, int strength) {
		/** @return 和参数是不是同一条登记，没变就不用惊动世界。 */
		boolean same(Direction face, boolean strong, BlockPos owner, int strength) {
			return this.face == face && this.strong == strong && this.strength == strength && this.owner.equals(owner);
		}
	}
}
