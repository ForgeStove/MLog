package io.github.forgestove.mlog.content.microprocessor;
import com.mojang.serialization.MapCodec;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import io.github.forgestove.mlog.logic.RedstoneSources;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
/** 微型逻辑处理器方块。 */
public class MicroProcessorBlock extends BaseEntityBlock {
	public static final MapCodec<MicroProcessorBlock> CODEC = simpleCodec(MicroProcessorBlock::new);
	public MicroProcessorBlock(Properties properties) {
		super(properties);
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
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer)
			level.getBlockEntity(pos, MLogBlockEntities.MICRO_PROCESSOR.get()).ifPresent(be -> serverPlayer.openMenu(be, pos));
		return InteractionResult.SUCCESS;
	}
}
