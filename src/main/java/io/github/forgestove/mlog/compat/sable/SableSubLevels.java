package io.github.forgestove.mlog.compat.sable;
import dev.ryanhcode.sable.companion.*;
import io.github.forgestove.mlog.core.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
public class SableSubLevels {
	public static boolean sameSpace(Level level, BlockPos a, BlockPos b) {
		if (!Mods.sableCompanion.isLoaded()) return true;
		return Impl.sameSpace(level, a, b);
	}
	public static Vec3 relativeTo(Level level, BlockPos processor, Vec3 target) {
		if (!Mods.sableCompanion.isLoaded()) return target;
		return Impl.relativeTo(level, processor, target);
	}
	/**
	 * 将物理结构内的坐标转换为世界坐标。
	 */
	public static Vec3 toWorld(Level level, Vec3 pos) {
		if (!Mods.sableCompanion.isLoaded()) return pos;
		return Impl.toWorld(level, pos);
	}
	/**
	 * 查询位置所在物理结构的朝向。
	 * <p>
	 * 不在物理结构内时返回 {@code null}。
	 */
	public static @Nullable Quaternionf orientation(Level level, Vec3 pos) {
		if (!Mods.sableCompanion.isLoaded()) return null;
		return Impl.orientation(level, pos);
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
		private static @Nullable SubLevelAccess containing(Level level, Vec3 pos) {
			return SableCompanion.INSTANCE.getContaining(level, pos);
		}
		static Vec3 relativeTo(Level level, BlockPos processor, Vec3 target) {
			var here = containing(level, processor);
			var there = containing(level, target);
			if (same(here, there)) return target;
			var world = there == null ? target : there.logicalPose().transformPosition(target);
			return here == null ? world : here.logicalPose().transformPositionInverse(world);
		}
		static Vec3 toWorld(Level level, Vec3 pos) {
			var here = containing(level, pos);
			return here == null ? pos : here.logicalPose().transformPosition(pos);
		}
		static @Nullable Quaternionf orientation(Level level, Vec3 pos) {
			var here = containing(level, pos);
			return here == null ? null : new Quaternionf(here.logicalPose().orientation());
		}
	}
}
