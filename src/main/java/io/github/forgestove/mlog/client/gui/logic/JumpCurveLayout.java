package io.github.forgestove.mlog.client.gui.logic;
import java.util.*;
/** 连线避让：区间重叠的曲线分到不同 lane，靠伸出距离错开。 */
public final class JumpCurveLayout {
	/** 相邻两层的伸出距离差。 */
	private static final int STEP = 4;
	/** 最内层的伸出距离。 */
	public static final float INITIAL = 8;
	/** 就地分配 lane，需要卡片的 {@code index} 已经算好。 */
	public static void assignLanes(List<JumpCurve> curves) {
		// 向上跳时区间反向，须先校正；曲线对象可能复用，已算标记须清空
		for (var curve : curves) {
			curve.begin = Math.min(curve.from.index, curve.to.index);
			curve.end = Math.max(curve.from.index, curve.to.index);
			curve.flipped = curve.from.index > curve.to.index;
			curve.done = false;
		}
		// 共享同一目标的曲线只留覆盖最长的一条占位，其余沿用其 lane，使线在目标附近重合
		var before = new HashMap<Integer, JumpCurve>();
		var after = new HashMap<Integer, JumpCurve>();
		for (var curve : curves)
			if (curve.flipped) {
				var prev = after.get(curve.begin);
				if (prev != null && prev.end >= curve.end) continue;
				after.put(curve.begin, curve);
			} else {
				var prev = before.get(curve.end);
				if (prev != null && prev.begin <= curve.begin) continue;
				before.put(curve.end, curve);
			}
		var processed = new ArrayList<JumpCurve>(before.size() + after.size());
		processed.addAll(before.values());
		processed.addAll(after.values());
		processed.sort(Comparator.comparingInt(curve -> curve.begin));
		// 按区间起点扫描，维护仍跨过当前起点的曲线及其占用的层
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
	/** 第 {@code index} 条曲线该占的层：先算完被它完全包住的区间，再排到它们外面。 */
	private static int height(List<JumpCurve> processed, int index, List<JumpCurve> active, BitSet used) {
		var curve = processed.get(index);
		if (curve.done) return curve.height;
		var nested = new ArrayList<>(active);
		var nestedUsed = (BitSet) used.clone();
		var max = -1;
		for (var i = index + 1; i < processed.size(); i++) {
			var inner = processed.get(i);
			// 起点已排序，向后扫描只可能是内层；终点更靠后的那条未被包住，跳过
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
	public static float reach(int lane, int limit) {
		return Math.clamp(limit, INITIAL, INITIAL + STEP * lane);
	}
}
