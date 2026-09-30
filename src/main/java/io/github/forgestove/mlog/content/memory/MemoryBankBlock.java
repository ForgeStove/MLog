package io.github.forgestove.mlog.content.memory;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
/**
 * 内存库方块。
 * <p>形状、读写行为与方块实体均与内存元相同，仅容量为 512 槽，
 * 链接名为 {@code bank1} 而非 {@code cell1}。
 */
public class MemoryBankBlock extends MemoryBlock {
	public static final MapCodec<MemoryBankBlock> CODEC = simpleCodec(MemoryBankBlock::new);
	public MemoryBankBlock(Properties properties) {
		super(512, properties);
	}
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
}
