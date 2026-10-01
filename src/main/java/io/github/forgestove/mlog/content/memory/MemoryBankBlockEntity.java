package io.github.forgestove.mlog.content.memory;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
/** 内存库：512 槽，非特权处理器可读写。 */
public class MemoryBankBlockEntity extends AbstractMemoryBlockEntity {
	/** 槽位数。 */
	public static final int CAPACITY = 512;
	public MemoryBankBlockEntity(BlockPos pos, BlockState state) {
		super(MLogBlockEntities.MEMORY_BANK.get(), CAPACITY, pos, state);
	}
}
