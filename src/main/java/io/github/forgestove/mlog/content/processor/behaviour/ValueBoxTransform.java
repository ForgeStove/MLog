package io.github.forgestove.mlog.content.processor.behaviour;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.joml.*;

import java.util.function.BiPredicate;
public abstract class ValueBoxTransform {
	protected static final double LIFT = -1 / 32D;
	protected static Vec3 localHit(BlockPos pos, Vec3 hit) {
		return hit.subtract(Vec3.atLowerCornerOf(pos));
	}
	protected static Quaternionf rotation(Direction facing) {
		var u = u(facing);
		var v = v(facing);
		var n = u.cross(v);
		var matrix = new Matrix3d().setColumn(0, u.x, u.y, u.z).setColumn(1, v.x, v.y, v.z).setColumn(2, n.x, n.y, n.z);
		return new Quaternionf().setFromNormalized(matrix);
	}
	private static Vec3 u(Direction facing) {
		var direction = switch (facing) {
			case UP, DOWN, SOUTH -> Direction.EAST;
			case NORTH -> Direction.WEST;
			case EAST -> Direction.NORTH;
			case WEST -> Direction.SOUTH;
		};
		return Vec3.atLowerCornerOf(direction.getNormal());
	}
	private static Vec3 v(Direction facing) {
		var direction = switch (facing) {
			case UP -> Direction.SOUTH;
			case DOWN -> Direction.NORTH;
			default -> Direction.DOWN;
		};
		return Vec3.atLowerCornerOf(direction.getNormal());
	}
	public abstract Vec3 getLocalOffset(BlockGetter level, BlockPos pos, BlockState state);
	public abstract Quaternionf getRotation(BlockGetter level, BlockPos pos, BlockState state);
	public float getScale() {
		return 1F;
	}
	public float getHitRadius() {
		return 1 / 4F;
	}
	public boolean shouldRender(BlockGetter level, BlockPos pos, BlockState state) {
		return !state.isAir();
	}
	public Vec3 getCenter(BlockGetter level, BlockPos pos, BlockState state) {
		return Vec3.atLowerCornerOf(pos).add(getLocalOffset(level, pos, state));
	}
	public boolean testHit(BlockGetter level, BlockPos pos, BlockState state, Vec3 localHit) {
		return localHit.distanceTo(getLocalOffset(level, pos, state)) < getHitRadius();
	}
	public boolean testHit(BlockGetter level, BlockPos pos, BlockState state, BlockHitResult hit) {
		return testHit(level, pos, state, localHit(pos, hit.getLocation()));
	}
	public static abstract class Sided extends ValueBoxTransform {
		protected Direction direction = Direction.UP;
		public Sided fromSide(Direction direction) {
			this.direction = direction;
			return this;
		}
		public Direction getSide() {
			return direction;
		}
		protected boolean isSideActive(BlockState state, Direction direction) {
			return true;
		}
		@Override
		public Vec3 getLocalOffset(BlockGetter level, BlockPos pos, BlockState state) {
			var normal = getSide().getNormal();
			var offset = .5 + LIFT;
			return new Vec3(.5 + normal.getX() * offset, .5 + normal.getY() * offset, .5 + normal.getZ() * offset);
		}
		@Override
		public Quaternionf getRotation(BlockGetter level, BlockPos pos, BlockState state) {
			return rotation(getSide());
		}
		@Override
		public boolean shouldRender(BlockGetter level, BlockPos pos, BlockState state) {
			return super.shouldRender(level, pos, state) && isSideActive(state, getSide());
		}
		@Override
		public boolean testHit(BlockGetter level, BlockPos pos, BlockState state, BlockHitResult hit) {
			return isSideActive(state, hit.getDirection()) && super.testHit(level, pos, state, hit);
		}
	}
	public static class CenteredSideValueBoxTransform extends Sided {
		private final BiPredicate<BlockState, Direction> allowedDirections;
		public CenteredSideValueBoxTransform() {
			this((state, direction) -> true);
		}
		public CenteredSideValueBoxTransform(BiPredicate<BlockState, Direction> allowedDirections) {
			this.allowedDirections = allowedDirections;
		}
		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return allowedDirections.test(state, direction);
		}
	}
	public static final class Faced extends Sided {
		public static final Faced INSTANCE = new Faced();
		private static Vec3 center(BlockGetter level, BlockPos pos, BlockState state) {
			var box = state.getShape(level, pos).bounds();
			var facing = state.getValue(AbstractProcessorBlock.FACING);
			var middle = box.getCenter();
			var center = switch (facing.getAxis()) {
				case X -> new Vec3(facing == Direction.EAST ? box.maxX : box.minX, middle.y, middle.z);
				case Y -> new Vec3(middle.x, facing == Direction.UP ? box.maxY : box.minY, middle.z);
				case Z -> new Vec3(middle.x, middle.y, facing == Direction.SOUTH ? box.maxZ : box.minZ);
			};
			return Vec3.atLowerCornerOf(pos).add(center).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(LIFT));
		}
		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return state.getValue(AbstractProcessorBlock.FACING) == direction;
		}
		@Override
		public Vec3 getLocalOffset(BlockGetter level, BlockPos pos, BlockState state) {
			return center(level, pos, state).subtract(Vec3.atLowerCornerOf(pos));
		}
		@Override
		public Quaternionf getRotation(BlockGetter level, BlockPos pos, BlockState state) {
			return rotation(state.getValue(AbstractProcessorBlock.FACING));
		}
	}
}
