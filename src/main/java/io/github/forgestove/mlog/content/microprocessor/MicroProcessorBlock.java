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
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
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
	public static final float BUTTON_LIFT = 1 / 32F;
	public MicroProcessorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
	}
	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
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
	 * <p>潜行也放行，对齐 Create 的 {@code canInteract}：客户端那边的链接模式同样不接管潜行的右键，
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
			var matrix = new Matrix3f()
				.setColumn(0, (float) u.x, (float) u.y, (float) u.z)
				.setColumn(1, (float) v.x, (float) v.y, (float) v.z)
				.setColumn(2, (float) n.x, (float) n.y, (float) n.z);
			return new Quaternionf().setFromNormalized(matrix);
		}
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
}
