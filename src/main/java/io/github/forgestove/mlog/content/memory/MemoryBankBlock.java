package io.github.forgestove.mlog.content.memory;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
/** 内存库方块：形状与读写行为均与内存元相同，槽位数为 512，链接名为 {@code bank1} 而非 {@code cell1}。 */
public class MemoryBankBlock extends AbstractMemoryBlock {
	public static final MapCodec<MemoryBankBlock> CODEC = simpleCodec(MemoryBankBlock::new);
	public MemoryBankBlock(Properties properties) {
		super(properties);
	}
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MemoryBankBlockEntity(pos, state);
	}
}
