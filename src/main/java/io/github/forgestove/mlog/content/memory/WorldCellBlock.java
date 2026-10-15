package io.github.forgestove.mlog.content.memory;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
/** 世界内存元：槽位数与内存库同为 512，仅世界处理器可读写；{@link GameMasterBlock} 使非 OP 无法破坏或放置。 */
public class WorldCellBlock extends AbstractMemoryBlock implements GameMasterBlock {
	public static final MapCodec<WorldCellBlock> CODEC = simpleCodec(WorldCellBlock::new);
	public WorldCellBlock(Properties properties) {
		super(properties);
	}
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WorldCellBlockEntity(pos, state);
	}
}
