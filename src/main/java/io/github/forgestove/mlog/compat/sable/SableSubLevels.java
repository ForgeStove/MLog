package io.github.forgestove.mlog.compat.sable;
import dev.ryanhcode.sable.companion.*;
import io.github.forgestove.mlog.core.MLogMods;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
/**
 * 子层级（Sable 的物理结构）的空间判定与坐标换算。
 * <p>子层级内的方块坐标属于各自的 plot 坐标系，跨空间（地面与子层级之间、不同子层级之间）相减无意义，须经位姿换算。
 * <p>companion 为可选模组，未安装时全部退化为恒等返回；引用 companion 类型的代码都在 {@link Impl} 中。
 */
public final class SableSubLevels {
	/** @return 两个位置是否同属一个空间（均不在子层级内，或属于同一子层级）。 */
	public static boolean sameSpace(Level level, BlockPos a, BlockPos b) {
		if (!MLogMods.sableCompanion.isLoaded()) return true;
		return Impl.sameSpace(level, a, b);
	}
	/** @return 目标换算到处理器所在坐标系后的位置；同空间时原样返回。供跨空间比较距离使用。 */
	public static Vec3 relativeTo(Level level, BlockPos processor, Vec3 target) {
		if (!MLogMods.sableCompanion.isLoaded()) return target;
		return Impl.relativeTo(level, processor, target);
	}
	private static final class Impl {
		static boolean sameSpace(Level level, BlockPos a, BlockPos b) {
			return same(containing(level, a), containing(level, b));
		}
		private static boolean same(@Nullable SubLevelAccess a, @Nullable SubLevelAccess b) {
			if (a == null || b == null) return a == b;
			return a.getUniqueId().equals(b.getUniqueId());
		}
		/** @return 坐标所属的子层级；不在任何子层级内时为 {@code null}。 */
		private static @Nullable SubLevelAccess containing(Level level, BlockPos pos) {
			return containing(level, Vec3.atLowerCornerOf(pos));
		}
		/** 同上；位置不限于整数格。 */
		private static @Nullable SubLevelAccess containing(Level level, Vec3 pos) {
			return SableCompanion.INSTANCE.getContaining(level, pos);
		}
		static Vec3 relativeTo(Level level, BlockPos processor, Vec3 target) {
			var here = containing(level, processor);
			var there = containing(level, target);
			if (same(here, there)) return target;
			// 目标侧先换算至世界坐标，再换算至处理器侧；不在子层级内的一侧不变
			var world = there == null ? target : there.logicalPose().transformPosition(target);
			return here == null ? world : here.logicalPose().transformPositionInverse(world);
		}
	}
}
