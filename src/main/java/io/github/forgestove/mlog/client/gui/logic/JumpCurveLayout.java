package io.github.forgestove.mlog.client.gui.logic;
import net.neoforged.api.distmarker.*;

import java.util.*;
/** 连线避让：区间重叠的曲线分到不同 lane，靠伸出距离错开，避免叠在一起。 */
@OnlyIn(Dist.CLIENT)
public final class JumpCurveLayout {
	/**
	 * 相邻两层之间的伸出距离差，同时也是最内层的伸出距离。
	 * <p>伸出距离是相对跳转节点算的，节点到卡片右边缘还有一截，所以最内层也是凸出来的，
	 * 不会贴着卡片边缘。层数多起来就按这个差值一层层往外让。
	 */
	private static final int STEP = 8;
	/** 连线出现时的伸出距离，就从最内层开始。 */
	public static final float INITIAL = STEP;
	/**
	 * 就地分配 lane，需要卡片的 {@code index} 已经算好。
	 * <p>照搬 Mindustry 的 {@code LCanvas.setJumpHeights}：先把曲线化成区间，
	 * 再让被包住的区间先分层，包着它们的那条排到外面去。
	 */
	public static void assignLanes(List<JumpCurve> curves) {
		// jump 往上跳时区间是反的，先理正。曲线对象可能是复用来的，算过的标记也要清掉
		for (var curve : curves) {
			curve.begin = Math.min(curve.from.index, curve.to.index);
			curve.end = Math.max(curve.from.index, curve.to.index);
			curve.flipped = curve.from.index > curve.to.index;
			curve.done = false;
		}
		// 共享同一个目标（向上跳则是共享同一个起点）的曲线只留覆盖最长的那条占位，
		// 其余的直接沿用它的 lane，于是汇到同一张卡片的线在目标附近重合成一条
		var before = new HashMap<Integer, JumpCurve>();
		var after = new HashMap<Integer, JumpCurve>();
		for (var curve : curves) {
			if (curve.flipped) {
				var prev = after.get(curve.begin);
				if (prev != null && prev.end >= curve.end) continue;
				after.put(curve.begin, curve);
			} else {
				var prev = before.get(curve.end);
				if (prev != null && prev.begin <= curve.begin) continue;
				before.put(curve.end, curve);
			}
		}
		var processed = new ArrayList<JumpCurve>(before.values().size() + after.values().size());
		processed.addAll(before.values());
		processed.addAll(after.values());
		processed.sort(Comparator.comparingInt(curve -> curve.begin));
		// 按区间起点扫一遍，维护还在跨过当前起点的曲线和它们占用的层
		var active = new ArrayList<JumpCurve>();
		var used = new BitSet();
		for (var i = 0; i < processed.size(); i++) {
			var curve = processed.get(i);
			for (var it = active.iterator(); it.hasNext(); ) {
				var other = it.next();
				if (other.end > curve.begin) continue;
				used.clear(other.height);
				it.remove();
			}
			var height = height(processed, i, active, used);
			active.add(curve);
			used.set(height);
		}
		for (var curve : curves) {
			var repr = curve.flipped ? after.get(curve.begin) : before.get(curve.end);
			curve.lane = repr.height;
		}
	}
	/**
	 * @return 第 {@code index} 条曲线该占的层。
	 * <p>被它完全包住的区间要先算完，自己再排到它们外面，于是嵌套的跳转一层套一层，
	 * 而不是互相穿插。{@code active} 与 {@code used} 是这条曲线起点处的现场，逐层复制下去。
	 */
	private static int height(List<JumpCurve> processed, int index, List<JumpCurve> active, BitSet used) {
		var curve = processed.get(index);
		if (curve.done) return curve.height;
		var nested = new ArrayList<>(active);
		var nestedUsed = (BitSet) used.clone();
		var max = -1;
		for (var i = index + 1; i < processed.size(); i++) {
			var inner = processed.get(i);
			// 起点已经排过序，往后扫只可能是内层；终点更靠后的那条没被包住，跳过
			if (inner.end > curve.end) continue;
			for (var it = nested.iterator(); it.hasNext(); ) {
				var other = it.next();
				if (other.end > inner.begin) continue;
				nestedUsed.clear(other.height);
				it.remove();
			}
			max = Math.max(max, height(processed, i, nested, nestedUsed));
			nested.add(inner);
			nestedUsed.set(inner.height);
		}
		curve.height = used.nextClearBit(max + 1);
		curve.done = true;
		return curve.height;
	}
	/** @param limit 卡片列右侧的可用宽度，伸出得再远也不该越过画布。 */
	public static float reach(int lane, int limit) {
		return Math.min(STEP * (lane + 1), Math.max(INITIAL, limit));
	}
}
