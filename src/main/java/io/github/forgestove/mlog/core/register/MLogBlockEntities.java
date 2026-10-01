package io.github.forgestove.mlog.core.register;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.memory.MemoryBankBlockEntity;
import io.github.forgestove.mlog.content.memory.MemoryCellBlockEntity;
import io.github.forgestove.mlog.content.memory.WorldCellBlockEntity;
import io.github.forgestove.mlog.content.processor.MicroProcessorBlockEntity;
import io.github.forgestove.mlog.content.processor.WorldProcessorBlockEntity;
import io.github.forgestove.mlog.content.display.TileLogicDisplayBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
/** 方块实体注册。 */
@SuppressWarnings("DataFlowIssue")
public final class MLogBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		MLog.ID
	);
	public static final Supplier<BlockEntityType<MicroProcessorBlockEntity>> MICRO_PROCESSOR = BLOCK_ENTITIES.register(
		"micro_processor",
		() -> Builder.of(MicroProcessorBlockEntity::new, MLogBlocks.MICRO_PROCESSOR.get()).build(null)
	);
	public static final Supplier<BlockEntityType<WorldProcessorBlockEntity>> WORLD_PROCESSOR = BLOCK_ENTITIES.register(
		"world_processor",
		() -> Builder.of(WorldProcessorBlockEntity::new, MLogBlocks.WORLD_PROCESSOR.get()).build(null)
	);
	public static final Supplier<BlockEntityType<MemoryCellBlockEntity>> MEMORY_CELL = BLOCK_ENTITIES.register(
		"memory_cell",
		() -> Builder.of(MemoryCellBlockEntity::new, MLogBlocks.MEMORY_CELL.get()).build(null)
	);
	public static final Supplier<BlockEntityType<MemoryBankBlockEntity>> MEMORY_BANK = BLOCK_ENTITIES.register(
		"memory_bank",
		() -> Builder.of(MemoryBankBlockEntity::new, MLogBlocks.MEMORY_BANK.get()).build(null)
	);
	public static final Supplier<BlockEntityType<WorldCellBlockEntity>> WORLD_CELL = BLOCK_ENTITIES.register(
		"world_cell",
		() -> Builder.of(WorldCellBlockEntity::new, MLogBlocks.WORLD_CELL.get()).build(null)
	);
	public static final Supplier<BlockEntityType<TileLogicDisplayBlockEntity>> TILE_LOGIC_DISPLAY = BLOCK_ENTITIES.register(
		"tile_logic_display",
		() -> Builder.of(TileLogicDisplayBlockEntity::new, MLogBlocks.TILE_LOGIC_DISPLAY.get()).build(null)
	);
}
