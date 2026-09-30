package io.github.forgestove.mlog.content.memory;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.*;
/**
 * 世界内存元。
 * <p>容量与内存库同为 512 槽，区别在于特权：仅世界处理器可读写（见
 * {@code MemoryBlockEntity#privileged()}），非特权处理器无法与其建立链接。
 * <p>权限处理同 {@code WorldProcessorBlock} 与命令方块：{@link GameMasterBlock} 使非 OP 无法破坏，
 * 物品为 {@code GameMasterBlockItem}（非 OP 无法放置）。
 */
public class WorldCellBlock extends MemoryBlock implements GameMasterBlock {
	public static final MapCodec<WorldCellBlock> CODEC = simpleCodec(WorldCellBlock::new);
	public WorldCellBlock(Properties properties) {
		super(512, properties);
	}
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
}
