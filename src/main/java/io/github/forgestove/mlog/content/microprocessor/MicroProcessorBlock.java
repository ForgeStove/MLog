package io.github.forgestove.mlog.content.microprocessor;
import com.mojang.serialization.MapCodec;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import io.github.forgestove.mlog.logic.RedstoneSources;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.lang.Math;
import java.util.Arrays;
/** 微型逻辑处理器方块。 */
public class MicroProcessorBlock extends BaseEntityBlock {
	public static final MapCodec<MicroProcessorBlock> CODEC = simpleCodec(MicroProcessorBlock::new);
	/** 正面朝向：模型上分前后那面冲哪边，放置时跟着玩家点的那一面走。 */
	public static final DirectionProperty FACING = BlockStateProperties.FACING;
	/** 顶面那个编辑按钮的边长，占方块宽度的比例。 */
	public static final float BUTTON_SIZE = 1 / 2F;
	/**
	 * 按钮离顶面抬起的量，单位是格。
	 * <p>取半个像素。
	 */
	public static final float BUTTON_LIFT = -1 / 32F;
	/**
	 * 正面朝上时，模型每个 element 各出一个外接方框，坐标是 0..16 的方块局部像素，
	 * 每行六个数是 {@code x1, y1, z1, x2, y2, z2}。
	 * 直接取自 {@code models/block/micro_processor.json}（{@code from}/{@code to} 绕各自的
	 * {@code origin} 转过 {@code rotation} 之后再取包围盒）。
	 * <p>{@code VoxelShape} 只能轴对齐，那几根四十五度的斜柱只能拿外接方框顶替——它们本身是细柱，
	 * 外框会比方柱胖一圈，模型要是改了，重新按同样的办法生成一遍这段坐标。
	 */
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
	/**
	 * 六个朝向的轮廓，按 {@link Direction} 的枚举顺序排（下、上、北、南、西、东）。
	 * <p>编辑按钮、链接名和命中判定都从形状定位，形状和模型对不上时那几处会跟着飘。
	 */
	private static final VoxelShape[] SHAPES = Arrays.stream(Direction.values()).map(MicroProcessorBlock::turn).toArray(VoxelShape[]::new);
	public MicroProcessorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
	}
	/**
	 * 把正面朝上的轮廓绕方块中心转到 {@code facing} 指的方向。
	 * <p>这里直接为每个方向指定从 UP 到该方向的旋转四元数，避免使用 {@code Direction.getRotation()}
	 * 在水平方向上的歧义。
	 */
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
	/**
	 * 单个方框的旋转：两个角点各转一次，再取新的包围盒。
	 * <p>坐标是 {@code PARTS} 里那套 0..16 的像素值，加减 {@link #MIDDLE} 就在方块中心上做旋转，
	 * 转完除以 16 转换为 0..1 的方块局部坐标，再喂给 {@code Shapes.box}。
	 */
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
	/** 绕方块中心把一个点转过去。 */
	private static Vector3f turn(Vector3f v, Quaternionf rotation) {
		return v.sub(MIDDLE, MIDDLE, MIDDLE).rotate(rotation).add(MIDDLE, MIDDLE, MIDDLE);
	}
	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(FACING).ordinal()];
	}
	/** 贴着玩家点的那一面放：点在顶面就是 UP，点在侧面就是那个水平方向，点底面就是 DOWN。 */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace());
	}
	/** 跟着扳手、结构方块一类的旋转走，别把朝向留在原地。 */
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}
	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
	}
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	/** 默认的 {@code INVISIBLE} 会把方块模型也吃掉，悬浮文字是在模型之上叠加的。 */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}
	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MicroProcessorBlockEntity(pos, state);
	}
	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		// 逻辑只在服务端跑
		if (level.isClientSide) return null;
		return createTickerHelper(type, MLogBlockEntities.MICRO_PROCESSOR.get(), MicroProcessorBlockEntity::tick);
	}
	/**
	 * 处理器没了，它留下的红石充能也得跟着撤。
	 * <p>那种效果不写在方块状态里，光是把方块拆掉清不掉，目标会一直以为自己还被充着能。
	 * <p>只在真正换成别的方块时清：{@code newState} 还是自己（改状态、区块卸载）就不动。
	 */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) RedstoneSources.removeAll(serverLevel, pos);
		super.onRemove(state, level, pos, newState, isMoving);
	}
	/**
	 * 只有点在顶面那个编辑按钮上才开界面。
	 * <p>方块其余部分一律放行，交给客户端那边的链接模式接管——所以这里返回 {@code PASS} 而不是 {@code SUCCESS}。
	 * <p>潜行也放行：客户端那边的链接模式同样不接管潜行的右键，
	 * 这一下于是完整地留给原版。
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player.isShiftKeyDown() || !isEditButton(level, pos, hit)) return InteractionResult.PASS;
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer)
			level.getBlockEntity(pos, MLogBlockEntities.MICRO_PROCESSOR.get()).ifPresent(be -> serverPlayer.openMenu(be, pos));
		return InteractionResult.SUCCESS;
	}
	/** @return 这次点击是不是落在编辑按钮上——按钮贴在 {@code FACING} 指的那一面。 */
	public static boolean isEditButton(BlockGetter level, BlockPos pos, BlockHitResult hit) {
		var state = level.getBlockState(pos);
		if (hit.getDirection() != state.getValue(FACING)) return false;
		var frame = buttonFrame(level, pos);
		var offset = hit.getLocation().subtract(frame.center());
		var half = BUTTON_SIZE / 2;
		return Math.abs(offset.dot(frame.u())) <= half && Math.abs(offset.dot(frame.v())) <= half;
	}
	/**
	 * @return 编辑按钮所在的那一面：{@code FACING} 指哪面就贴哪面。
	 * 	<p>面中心取自形状的包围盒，换成半高模型时按钮会自己跟着新的面走，这里不用动。
	 */
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
		// 形状给的是方块局部坐标（0..1），加上方块自身的位置才是外面要用的世界坐标
		return new FaceFrame(
			Vec3.atLowerCornerOf(pos).add(center).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(BUTTON_LIFT)),
			Vec3.atLowerCornerOf(u.getNormal()),
			Vec3.atLowerCornerOf(v.getNormal())
		);
	}
	/**
	 * 编辑按钮贴的那一面的坐标系。
	 * <p>{@code center} 是面中心的<b>世界坐标</b>（已经沿法向抬起 {@link #BUTTON_LIFT}），{@code u} 与
	 * {@code v} 是面内的两个方向，分别对应按钮局部坐标的 x 轴和 y 轴（y 指向图标的下方）。
	 * <p>取向按「从面外侧正对着看」定：底面和四个侧面都把世界朝下当成本地 y，图标不会看着倒过来。
	 */
	public record FaceFrame(Vec3 center, Vec3 u, Vec3 v) {
		/** @return 面内局部坐标 {@code (x, y)} 对应的世界坐标。 */
		public Vec3 point(float x, float y) {
			return center.add(u.scale(x)).add(v.scale(y));
		}
		/**
		 * @return 把局部坐标转到这个面上的旋转。
		 * 	<p>三根轴分别落到 {@code u}、{@code v} 和两者的叉积上：图标的正面朝的是局部 -z，
		 * 	所以叉积正好是「穿进面里」那个方向，三个轴凑成右手系才转得成四元数。
		 */
		public Quaternionf rotation() {
			var n = u.cross(v);
			var matrix = new Matrix3f().setColumn(0, (float) u.x, (float) u.y, (float) u.z)
				.setColumn(1, (float) v.x, (float) v.y, (float) v.z)
				.setColumn(2, (float) n.x, (float) n.y, (float) n.z);
			return new Quaternionf().setFromNormalized(matrix);
		}
	}
}