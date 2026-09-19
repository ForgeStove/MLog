package io.github.forgestove.mlog.content.memory;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.GameMasterBlock;
/**
 * 世界内存元，对应 Mindustry 的 {@code world-cell}。
 * <p>容量和内存库一样是 512 槽，区别在特权：只有世界处理器读写得动它（见
 * {@code MemoryBlockEntity#privileged()}），非特权的处理器连都连不上。
 * <p>权限那套照 {@code WorldProcessorBlock} 和命令方块：{@link GameMasterBlock} 让非 OP 挖不掉，
 * 物品是 {@code GameMasterBlockItem}（非 OP 放不下）。
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
