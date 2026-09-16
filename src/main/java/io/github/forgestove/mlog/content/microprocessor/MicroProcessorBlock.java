package io.github.forgestove.mlog.content.microprocessor;
import io.github.forgestove.mlog.core.register.*;
import com.mojang.serialization.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.*;
import org.jetbrains.annotations.*;
/** 微型逻辑处理器方块。 */
public class MicroProcessorBlock extends BaseEntityBlock {
	public static final MapCodec<MicroProcessorBlock> CODEC = simpleCodec(MicroProcessorBlock::new);
	public MicroProcessorBlock(BlockBehaviour.Properties properties) {
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
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			level.getBlockEntity(pos, MLogBlockEntities.MICRO_PROCESSOR.get()).ifPresent(be -> serverPlayer.openMenu(be, pos));
		}
		return InteractionResult.SUCCESS;
	}
}
