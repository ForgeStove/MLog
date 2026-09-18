package io.github.forgestove.mlog.core.register;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;
/** 方块注册。 */
public final class MLogBlocks {
	public static final Blocks BLOCKS = DeferredRegister.createBlocks(MLog.ID);
	public static final DeferredBlock<MicroProcessorBlock> MICRO_PROCESSOR = BLOCKS.registerBlock(
		"micro_processor",
		MicroProcessorBlock::new,
		Properties.of().strength(3.5F).requiresCorrectToolForDrops().noOcclusion()
	);
}
