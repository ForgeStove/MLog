package io.github.forgestove.mlog.content.memory;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
/** 内存元：64 槽，非特权处理器可读写。 */
public class MemoryCellBlockEntity extends AbstractMemoryBlockEntity {
	/** 槽位数。 */
	public static final int CAPACITY = 64;
	public MemoryCellBlockEntity(BlockPos pos, BlockState state) {
		super(MLogBlockEntities.MEMORY_CELL.get(), CAPACITY, pos, state);
	}
}
