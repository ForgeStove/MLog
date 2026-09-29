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
	/** 画布所在面的法向，按玩家视线决定：默认背对视线，潜行时反向。 */
	public static final DirectionProperty FACING = BlockStateProperties.FACING;
	/** 面内摆向，每级 90°；竖直朝向靠它决定屏幕内容朝哪一侧。 */
	public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);
	/** 边框的框宽，单位是格。 */
	public static final float INSET = 6F / 32F;
	public TileLogicDisplayBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ROTATION, 0));
	}
	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACING, ROTATION);
	}
	/** 默认背对视线，即屏幕面向玩家；潜行时反向。点击面不参与判定。 */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		var looking = context.getNearestLookingDirection();
		var reversed = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
		var facing = reversed ? looking : looking.getOpposite();
		return defaultBlockState().setValue(FACING, facing).setValue(ROTATION, rotationFor(context, facing));
	}
	/** @return 面内的摆向：竖直朝向取玩家所在的一侧，并沿用相邻同类；水平朝向固定为 0。 */
	private static int rotationFor(BlockPlaceContext context, Direction facing) {
		if (facing.getAxis().isHorizontal()) return 0;
		for (var side : Direction.values()) {
			var state = context.getLevel().getBlockState(context.getClickedPos().relative(side));
			if (state.getBlock() instanceof TileLogicDisplayBlock && state.getValue(FACING) == facing) return state.getValue(ROTATION);
		}
		var back = context.getHorizontalDirection().getOpposite();
		for (var rotation = 0; rotation < 4; rotation++) if (DisplayGroup.down(facing, rotation) == back) return rotation;
		return 0;
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
		// 命令只在服务端攒，客户端只负责渲染
		if (level.isClientSide) return null;
		return createTickerHelper(type, MLogBlockEntities.TILE_LOGIC_DISPLAY.get(), TileLogicDisplayBlockEntity::tick);
	}
}
