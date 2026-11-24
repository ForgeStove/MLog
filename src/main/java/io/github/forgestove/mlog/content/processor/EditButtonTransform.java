package io.github.forgestove.mlog.content.processor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

/**
 * 处理器编辑按钮的几何：贴在哪个面、朝哪个方向、命中范围。
 * 渲染与点击判定都从这里取，避免两处各算一份。
 * 与 Create 的 {@code ValueBoxTransform} 同构。
 */
public abstract class EditButtonTransform {
	/** 按钮边长，单位为格。 */
	public static final float SIZE = 1 / 2F;
	/** 按钮相对模型表面沿法向的内缩，单位为格。 */
	protected static final float LIFT = -1 / 32F;
	/** 按钮中心相对方块原点的偏移。 */
	public abstract Vec3 getLocalOffset(BlockGetter level, BlockPos pos, BlockState state);
	/** 按钮所在平面的朝向。 */
	public abstract Quaternionf getRotation(BlockGetter level, BlockPos pos, BlockState state);
	public float getScale() {
		return 1F;
	}
	/** 命中半径，沿面内任意方向等距。 */
	public float getHitRadius() {
		return SIZE / 2;
	}
	/** 按钮中心的世界坐标，物理结构内为 plot 坐标。 */
	public Vec3 getCenter(BlockGetter level, BlockPos pos, BlockState state) {
		return Vec3.atLowerCornerOf(pos).add(getLocalOffset(level, pos, state));
	}
	/** 命中点是否落在按钮上。 */
	public abstract boolean testHit(BlockGetter level, BlockPos pos, BlockState state, BlockHitResult hit);
	/** 贴在处理器正面的按钮。 */
	public static final class Faced extends EditButtonTransform {
		public static final Faced INSTANCE = new Faced();
		private Faced() {}
		@Override
		public Vec3 getLocalOffset(BlockGetter level, BlockPos pos, BlockState state) {
			return frame(level, pos, state).center().subtract(Vec3.atLowerCornerOf(pos));
		}
		@Override
		public Quaternionf getRotation(BlockGetter level, BlockPos pos, BlockState state) {
			return frame(level, pos, state).rotation();
		}
		@Override
		public boolean testHit(BlockGetter level, BlockPos pos, BlockState state, BlockHitResult hit) {
			if (!isActiveFace(state, hit.getDirection())) return false;
			return hit.getLocation().distanceTo(getCenter(level, pos, state)) < getHitRadius();
		}
		/** 该方向是否为按钮所在的面。 */
		public boolean isActiveFace(BlockState state, Direction direction) {
			return state.getValue(AbstractProcessorBlock.FACING) == direction;
		}
		/**
		 * 以模型实际形状的外表面为基准，中心沿法向内缩 {@link #LIFT}。
		 * {@code v} 指向图标下方，与字体坐标一致。
		 */
		private static Face frame(BlockGetter level, BlockPos pos, BlockState state) {
			var box = state.getShape(level, pos).bounds();
			var facing = state.getValue(AbstractProcessorBlock.FACING);
			var middle = box.getCenter();
			var center = switch (facing.getAxis()) {
				case X -> new Vec3(facing == Direction.EAST ? box.maxX : box.minX, middle.y, middle.z);
				case Y -> new Vec3(middle.x, facing == Direction.UP ? box.maxY : box.minY, middle.z);
				case Z -> new Vec3(middle.x, middle.y, facing == Direction.SOUTH ? box.maxZ : box.minZ);
			};
			var u = switch (facing) {
				case UP, DOWN, SOUTH -> Direction.EAST;
				case NORTH -> Direction.WEST;
				case EAST -> Direction.NORTH;
				case WEST -> Direction.SOUTH;
			};
			var v = switch (facing) {
				case UP -> Direction.SOUTH;
				case DOWN -> Direction.NORTH;
				default -> Direction.DOWN;
			};
			return new Face(
				Vec3.atLowerCornerOf(pos).add(center).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(LIFT)),
				Vec3.atLowerCornerOf(u.getNormal()),
				Vec3.atLowerCornerOf(v.getNormal())
			);
		}
		/** 按钮所在平面的坐标系，{@code center} 为世界坐标。 */
		private record Face(Vec3 center, Vec3 u, Vec3 v) {
			Quaternionf rotation() {
				var n = u.cross(v);
				var matrix = new Matrix3f()
					.setColumn(0, (float) u.x, (float) u.y, (float) u.z)
					.setColumn(1, (float) v.x, (float) v.y, (float) v.z)
					.setColumn(2, (float) n.x, (float) n.y, (float) n.z);
				return new Quaternionf().setFromNormalized(matrix);
			}
		}
	}
}
