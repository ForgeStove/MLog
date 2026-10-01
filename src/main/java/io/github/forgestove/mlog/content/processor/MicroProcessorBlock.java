package io.github.forgestove.mlog.content.processor;
import com.mojang.serialization.MapCodec;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
/** 微型逻辑处理器。 */
public class MicroProcessorBlock extends AbstractProcessorBlock {
	public static final MapCodec<MicroProcessorBlock> CODEC = simpleCodec(MicroProcessorBlock::new);
	public MicroProcessorBlock(Properties properties) {
		super(properties);
	}
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MicroProcessorBlockEntity(pos, state);
	}
	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		// 逻辑仅在服务端执行
		if (level.isClientSide) return null;
		return createTickerHelper(type, MLogBlockEntities.MICRO_PROCESSOR.get(), AbstractProcessorBlockEntity::tick);
	}
	@Override
	public BlockEntityType<? extends AbstractProcessorBlockEntity> processorType() {
		return MLogBlockEntities.MICRO_PROCESSOR.get();
	}
}
