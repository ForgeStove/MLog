package io.github.forgestove.mlog.content.processor;
import io.github.forgestove.mlog.logic.RedstoneSources;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.lang.Math;
import java.util.Arrays;
public abstract class AbstractProcessorBlock extends BaseEntityBlock {
	public static final DirectionProperty FACING = BlockStateProperties.FACING;
	/** 模型顶部编辑按钮的边长，占方块宽度的比例。 */
	public static final float BUTTON_SIZE = 1 / 2F;
	/** 按钮相对顶面沿法向偏移的距离，单位是格。 */
	public static final float BUTTON_LIFT = -1 / 32F;
	/** 正面朝上时各 element 的外接方框（0..16 像素，每行 {@code x1, y1, z1, x2, y2, z2}）；{@code VoxelShape} 为轴对齐，四十五度的斜柱须以外接方框代替，外框比实际形状略大。 */
	private static final float MIDDLE = 8F;
	private static final float[][] PARTS = {
		{2, 0.02F, 2, 14, 7.02F, 14},
		{3.83F, 6.4F, 3.83F, 12.17F, 7.4F, 12.17F},
		{4, 0.02F, 14, 12, 2.02F, 16},
		{0, 0.02F, 4, 2, 2.02F, 12},
		{14, 0.02F, 4, 16, 2.02F, 12},
		{4, 0.02F, 0, 12, 2.02F, 2},
		{2.41F, 0, 11.58F, 6.83F, 8, 16},
		{2.99F, 1, 10.75F, 5.24F, 7.5F, 12.99F},
		{0, 0, 9.17F, 4.42F, 8, 13.59F},
		{2.41F, 0, 0, 6.83F, 8, 4.42F},
		{2.99F, 1, 3.01F, 5.24F, 7.5F, 5.25F},
		{0, 0, 2.41F, 4.42F, 8, 6.83F},
		{9.17F, 0, 11.58F, 13.59F, 8, 16},
		{10.76F, 1, 10.75F, 13.01F, 7.5F, 12.99F},
		{11.58F, 0, 9.17F, 16, 8, 13.59F},
		{9.17F, 0, 0, 13.59F, 8, 4.42F},
		{10.76F, 1, 3.01F, 13.01F, 7.5F, 5.25F},
		{11.58F, 0, 2.41F, 16, 8, 6.83F},
	};
	/** 按 {@link Direction} 枚举顺序排；按钮、链接名与命中判定均由形状定位，形状与模型不一致时会随之偏移。 */
	private static final VoxelShape[] SHAPES = Arrays.stream(Direction.values())
		.map(AbstractProcessorBlock::turn)
		.toArray(VoxelShape[]::new);
	public AbstractProcessorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
	}
	/** 每个方向从 UP 旋转到该方向的四元数：{@code Direction.getRotation()} 在水平方向上有歧义。 */
	private static VoxelShape turn(Direction facing) {
		Quaternionf rotation = switch (facing) {
			case DOWN -> new Quaternionf().rotationX((float) Math.PI);
			case UP -> new Quaternionf();
			case NORTH -> new Quaternionf().rotationX((float) (-Math.PI / 2));
			case SOUTH -> new Quaternionf().rotationX((float) (Math.PI / 2));
			case WEST -> new Quaternionf().rotationZ((float) (Math.PI / 2));
			case EAST -> new Quaternionf().rotationZ((float) (-Math.PI / 2));
		};
		return Arrays.stream(PARTS).map(part -> turn(part, rotation)).reduce(Shapes.empty(), Shapes::or);
	}
	private static VoxelShape turn(float[] part, Quaternionf rotation) {
		var min = turn(new Vector3f(part[0], part[1], part[2]), rotation);
		var max = turn(new Vector3f(part[3], part[4], part[5]), rotation);
		return Shapes.box(
			Math.min(min.x, max.x) / 16.0,
			Math.min(min.y, max.y) / 16.0,
			Math.min(min.z, max.z) / 16.0,
			Math.max(min.x, max.x) / 16.0,
			Math.max(min.y, max.y) / 16.0,
			Math.max(min.z, max.z) / 16.0
		);
	}
	private static Vector3f turn(Vector3f v, Quaternionf rotation) {
		return v.sub(MIDDLE, MIDDLE, MIDDLE).rotate(rotation).add(MIDDLE, MIDDLE, MIDDLE);
	}
	public static boolean isEditButton(BlockGetter level, BlockPos pos, BlockHitResult hit) {
		var state = level.getBlockState(pos);
		if (hit.getDirection() != state.getValue(FACING)) return false;
		var frame = buttonFrame(level, pos);
		var offset = hit.getLocation().subtract(frame.center());
		var half = BUTTON_SIZE / 2;
		return Math.abs(offset.dot(frame.u())) <= half && Math.abs(offset.dot(frame.v())) <= half;
	}
	/** 面中心取自形状包围盒，改用半高模型时按钮随新形状自动定位。 */
	public static FaceFrame buttonFrame(BlockGetter level, BlockPos pos) {
		var state = level.getBlockState(pos);
		var box = state.getShape(level, pos).bounds();
		var facing = state.getValue(FACING);
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
		// 形状给出的是方块局部坐标（0..1），加上方块位置方为世界坐标
		return new FaceFrame(
			Vec3.atLowerCornerOf(pos).add(center).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(BUTTON_LIFT)),
			Vec3.atLowerCornerOf(u.getNormal()),
			Vec3.atLowerCornerOf(v.getNormal())
		);
	}
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace());
	}
	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
	/** 默认的 {@code INVISIBLE} 会把方块模型一并隐藏。 */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}
	/** 红石充能须一并撤销：该效果不写入方块状态，拆除无法清除；{@code newState} 仍为自身（改状态、区块卸载）时无须清除。 */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) RedstoneSources.removeAll(serverLevel, pos);
		super.onRemove(state, level, pos, newState, isMoving);
	}
	/** 编辑按钮以外的点击返回 {@code PASS}，交由客户端的链接模式接管；潜行时整次点击交回原版。 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player.isShiftKeyDown() || !isEditButton(level, pos, hit)) return InteractionResult.PASS;
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer)
			level.getBlockEntity(pos, processorType()).ifPresent(be -> serverPlayer.openMenu(be, pos));
		return InteractionResult.SUCCESS;
	}
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}
	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
	}
	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(FACING).ordinal()];
	}
	/** 两种处理器的方块实体类型不同，不得写死为其中一种。 */
	public abstract BlockEntityType<? extends AbstractProcessorBlockEntity> processorType();
	/** 连接范围的半边长，以处理器为中心三轴各 ±该值格；不限范围时为 {@code null}。 */
	public abstract @Nullable Integer linkRange();
	/** 编辑按钮所在面的坐标系：{@code center} 为面中心世界坐标（已沿法向偏移 {@link #BUTTON_LIFT}），{@code u}/{@code v} 为面内两方向，{@code v} 指向图标下方。 */
	public record FaceFrame(Vec3 center, Vec3 u, Vec3 v) {
		public Vec3 point(float x, float y) {
			return center.add(u.scale(x)).add(v.scale(y));
		}
		/** 三轴取 {@code u}、{@code v} 与两者叉积；三轴须构成右手系才能转为四元数。 */
		public Quaternionf rotation() {
			var n = u.cross(v);
			var matrix = new Matrix3f().setColumn(0, (float) u.x, (float) u.y, (float) u.z)
				.setColumn(1, (float) v.x, (float) v.y, (float) v.z)
				.setColumn(2, (float) n.x, (float) n.y, (float) n.z);
			return new Quaternionf().setFromNormalized(matrix);
		}
	}
}
