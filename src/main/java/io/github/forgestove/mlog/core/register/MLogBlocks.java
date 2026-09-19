package io.github.forgestove.mlog.core.register;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.memory.MemoryBankBlock;
import io.github.forgestove.mlog.content.memory.MemoryBlock;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlock;
import io.github.forgestove.mlog.content.microprocessor.WorldProcessorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;
/** 方块注册。 */
public final class MLogBlocks {
	public static final Blocks BLOCKS = DeferredRegister.createBlocks(MLog.ID);
	public static final DeferredBlock<MicroProcessorBlock> MICRO_PROCESSOR = BLOCKS.registerBlock(
		"micro_processor",
		MicroProcessorBlock::new,
		Properties.of().strength(3.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops().noOcclusion()
	);
	/** 世界处理器：形状与外观和微型处理器一样，区别是它有特权。 */
	public static final DeferredBlock<WorldProcessorBlock> WORLD_PROCESSOR = BLOCKS.registerBlock(
		"world_processor",
		WorldProcessorBlock::new,
		Properties.of().strength(-1F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops().noOcclusion()
	);
	/** 内存元：64 个槽位，逻辑用 {@code read} / {@code write} 按下标读写。 */
	public static final DeferredBlock<MemoryBlock> MEMORY_CELL = BLOCKS.registerBlock(
		"memory_cell",
		MemoryBlock::new,
		Properties.of().strength(3.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()
	);
	/** 内存库：512 个槽位。和内存元共用一个方块实体类型，只是容量不同。 */
	public static final DeferredBlock<MemoryBankBlock> MEMORY_BANK = BLOCKS.registerBlock(
		"memory_bank",
		MemoryBankBlock::new,
		Properties.of().strength(3.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()
	);
}
