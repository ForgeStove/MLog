package io.github.forgestove.mlog.compat.sable;
import dev.ryanhcode.sable.companion.*;
import io.github.forgestove.mlog.core.MLogMods;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
/** 子层级（Sable 物理结构）的空间判定与坐标换算；跨空间须经位姿换算，无 companion 时退化为恒等返回。 */
public class SableSubLevels {
	public static boolean sameSpace(Level level, BlockPos a, BlockPos b) {
		if (!MLogMods.sableCompanion.isLoaded()) return true;
		return Impl.sameSpace(level, a, b);
	}
	public static Vec3 relativeTo(Level level, BlockPos processor, Vec3 target) {
		if (!MLogMods.sableCompanion.isLoaded()) return target;
		return Impl.relativeTo(level, processor, target);
	}
	private static class Impl {
		static boolean sameSpace(Level level, BlockPos a, BlockPos b) {
			return same(containing(level, a), containing(level, b));
		}
		private static boolean same(@Nullable SubLevelAccess a, @Nullable SubLevelAccess b) {
			if (a == null || b == null) return a == b;
			return a.getUniqueId().equals(b.getUniqueId());
		}
		private static @Nullable SubLevelAccess containing(Level level, BlockPos pos) {
			return containing(level, Vec3.atLowerCornerOf(pos));
		}
		/** 位置不限于整数格。 */
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
