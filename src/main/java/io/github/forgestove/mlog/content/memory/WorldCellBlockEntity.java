package io.github.forgestove.mlog.content.memory;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
/** 世界内存元：512 槽，仅特权处理器可读写。 */
public class WorldCellBlockEntity extends AbstractMemoryBlockEntity {
	/** 槽位数。 */
	public static final int CAPACITY = 512;
	public WorldCellBlockEntity(BlockPos pos, BlockState state) {
		super(MLogBlockEntities.WORLD_CELL.get(), CAPACITY, pos, state);
	}
	@Override
	public boolean privileged() {
		return true;
	}
}
