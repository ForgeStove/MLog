package io.github.forgestove.mlog.core.register;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.memory.*;
import io.github.forgestove.mlog.content.microprocessor.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;
public final class MLogBlocks {
	public static final Blocks BLOCKS = DeferredRegister.createBlocks(MLog.ID);
	public static final DeferredBlock<MicroProcessorBlock> MICRO_PROCESSOR = BLOCKS.registerBlock(
		"micro_processor",
		MicroProcessorBlock::new,
		Properties.of().strength(3.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops().noOcclusion()
	);
	public static final DeferredBlock<WorldProcessorBlock> WORLD_PROCESSOR = BLOCKS.registerBlock(
		"world_processor",
		WorldProcessorBlock::new,
		Properties.of().strength(-1F).noLootTable().sound(SoundType.AMETHYST).requiresCorrectToolForDrops().noOcclusion()
	);
	public static final DeferredBlock<MemoryBlock> MEMORY_CELL = BLOCKS.registerBlock(
		"memory_cell",
		MemoryBlock::new,
		Properties.of().strength(3.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()
	);
	public static final DeferredBlock<MemoryBankBlock> MEMORY_BANK = BLOCKS.registerBlock(
		"memory_bank",
		MemoryBankBlock::new,
		Properties.of().strength(3.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()
	);
	public static final DeferredBlock<WorldCellBlock> WORLD_CELL = BLOCKS.registerBlock(
		"world_cell",
		WorldCellBlock::new,
		Properties.of().strength(-1F).noLootTable().sound(SoundType.AMETHYST).requiresCorrectToolForDrops()
	);
}
