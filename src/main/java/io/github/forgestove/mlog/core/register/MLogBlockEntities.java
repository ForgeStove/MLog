package io.github.forgestove.mlog.core.register;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlockEntity;
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
}
