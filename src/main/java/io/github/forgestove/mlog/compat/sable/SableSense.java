package io.github.forgestove.mlog.compat.sable;
import io.github.forgestove.mlog.logic.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
public final class SableSense {
	@SuppressWarnings("unused")
	public static Senseable wrapBlock(Senseable delegate, Level level, BlockPos pos, @Nullable BlockEntity be, @Nullable Direction side) {
		return new BlockSable(delegate, level, Vec3.atLowerCornerOf(pos));
	}
	public static Senseable wrapEntity(Senseable delegate, Entity entity) {
		return new EntitySable(delegate, entity.level(), entity.position());
	}
	private static double sense(Senseable delegate, String access, Level level, Vec3 position) {
		var sable = SableAccess.byName(access);
		if (sable != null) {
			var orientation = SableSubLevels.orientation(level, position);
			var euler = orientation == null ? null : orientation.getEulerAnglesYXZ(new Vector3f());
			return switch (sable) {
				case plotX -> position.x;
				case plotY -> position.y;
				case plotZ -> position.z;
				case yaw -> euler == null ? 0 : Math.toDegrees(euler.y);
				case pitch -> euler == null ? 0 : Math.toDegrees(euler.x);
				case roll -> euler == null ? 0 : Math.toDegrees(euler.z);
			};
		}
		var vanilla = VanillaAccess.byName(access);
		if (vanilla == VanillaAccess.x || vanilla == VanillaAccess.y || vanilla == VanillaAccess.z) {
			var world = SableSubLevels.toWorld(level, position);
			return vanilla == VanillaAccess.x ? world.x : vanilla == VanillaAccess.y ? world.y : world.z;
		}
		return delegate.sense(access);
	}
	private static final class BlockSable extends ForwardingSenseable {
		private final Level level;
		private final Vec3 position;
		private BlockSable(Senseable delegate, Level level, Vec3 position) {
			super(delegate);
			this.level = level;
			this.position = position;
		}
		@Override
		public double sense(String access) {
			return SableSense.sense(delegate, access, level, position);
		}
	}
	private static final class EntitySable extends ForwardingSenseable {
		private final Level level;
		private final Vec3 position;
		private EntitySable(Senseable delegate, Level level, Vec3 position) {
			super(delegate);
			this.level = level;
			this.position = position;
		}
		@Override
		public double sense(String access) {
			return SableSense.sense(delegate, access, level, position);
		}
	}
}
