package io.github.forgestove.mlog.content.processor;
import com.mojang.serialization.MapCodec;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
public class WorldProcessorBlock extends AbstractProcessorBlock implements GameMasterBlock {
	public static final MapCodec<WorldProcessorBlock> CODEC = simpleCodec(WorldProcessorBlock::new);
	public WorldProcessorBlock(Properties properties) {
		super(properties);
	}
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WorldProcessorBlockEntity(pos, state);
	}
	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		// 逻辑仅在服务端执行
		if (level.isClientSide) return null;
		return createTickerHelper(type, MLogBlockEntities.WORLD_PROCESSOR.get(), AbstractProcessorBlockEntity::tick);
	}
	@Override
	public BlockEntityType<? extends AbstractProcessorBlockEntity> processorType() {
		return MLogBlockEntities.WORLD_PROCESSOR.get();
	}
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return player.canUseGameMasterBlocks() ? super.useWithoutItem(state, level, pos, player, hit) : InteractionResult.PASS;
	}
}
