package io.github.forgestove.mlog.content.memory;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
/** 内存元方块。 */
public class MemoryCellBlock extends AbstractMemoryBlock {
	public static final MapCodec<MemoryCellBlock> CODEC = simpleCodec(MemoryCellBlock::new);
	public MemoryCellBlock(Properties properties) {
		super(properties);
	}
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MemoryCellBlockEntity(pos, state);
	}
}
