package io.github.forgestove.mlog.content.display;
import com.mojang.serialization.MapCodec;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.*;
import org.jetbrains.annotations.Nullable;
/**
 * 逻辑显示单元。
 * <p>同朝向相邻的单元自动拼成一组，整组只在外缘留一圈框。
 * <p>哪些边相连不写入方块状态：由模型层按世界现算，邻居变化时区块自行重编。
 */
public class TileLogicDisplayBlock extends BaseEntityBlock {
	public static final MapCodec<TileLogicDisplayBlock> CODEC = simpleCodec(TileLogicDisplayBlock::new);
	/** 屏幕所在的面与其面内摆向，合为一个属性：{@code front} 是法向，{@code top} 是屏幕「上」指向。 */
	public static final EnumProperty<FrontAndTop> ORIENTATION = BlockStateProperties.ORIENTATION;
	/** 边框的框宽，单位是格。 */
	public static final float INSET = 6F / 32F;
	public TileLogicDisplayBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(ORIENTATION, FrontAndTop.UP_NORTH));
	}
	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(ORIENTATION);
	}
	/** 默认背对视线，即屏幕面向玩家；潜行时反向。点击面不参与判定。 */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		var looking = context.getNearestLookingDirection();
		var reversed = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
		var front = reversed ? looking : looking.getOpposite();
		return defaultBlockState().setValue(ORIENTATION, orientationFor(context, front));
	}
	/** @return 摆向：竖直朝向令屏幕「上」指向玩家一侧，并沿用相邻同类；水平朝向的「上」恒为 {@code UP}。 */
	private static FrontAndTop orientationFor(BlockPlaceContext context, Direction front) {
		if (front.getAxis().isHorizontal()) return FrontAndTop.fromFrontAndTop(front, Direction.UP);
		for (var side : Direction.values()) {
			var state = context.getLevel().getBlockState(context.getClickedPos().relative(side));
			if (state.getBlock() instanceof TileLogicDisplayBlock) {
				var orientation = state.getValue(ORIENTATION);
				if (orientation.front() == front) return orientation;
			}
		}
		return FrontAndTop.fromFrontAndTop(front, context.getHorizontalDirection());
	}
	/** 结构旋转时同步转动朝向与面内摆向。 */
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(ORIENTATION, rotation.rotation().rotate(state.getValue(ORIENTATION)));
	}
	/** 结构镜像时同步镜像朝向与面内摆向。 */
	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(ORIENTATION, mirror.rotation().rotate(state.getValue(ORIENTATION)));
	}
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	/** 默认的 {@code INVISIBLE} 会把方块模型一并隐藏。 */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}
	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileLogicDisplayBlockEntity(pos, state);
	}
	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		// 命令只在服务端累积，客户端只负责渲染
		if (level.isClientSide) return null;
		return createTickerHelper(type, MLogBlockEntities.TILE_LOGIC_DISPLAY.get(), TileLogicDisplayBlockEntity::tick);
	}
}
