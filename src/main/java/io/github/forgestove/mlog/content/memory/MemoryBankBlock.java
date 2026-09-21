package io.github.forgestove.mlog.content.memory;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
/**
 * 内存库方块。
 * <p>形状、读写行为、方块实体都和内存元一样，只有容量大一圈（512 槽），
 * 所以链接名叫 {@code bank1} 而不是 {@code cell1}。
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
