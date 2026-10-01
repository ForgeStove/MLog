package io.github.forgestove.mlog.content.memory;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
/**
 * 世界内存元。
 * <p>槽位数与内存库同为 512，区别在于特权：仅世界处理器可读写（见 {@code WorldCellBlockEntity}），
 * 非特权处理器无法与其建立链接。
 * <p>权限处理同 {@code WorldProcessorBlock} 与命令方块：{@link GameMasterBlock} 使非 OP 无法破坏，
 * 物品为 {@code GameMasterBlockItem}（非 OP 无法放置）。
 */
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
