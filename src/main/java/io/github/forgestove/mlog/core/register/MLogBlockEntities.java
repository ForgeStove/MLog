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
		// 两种处理器共用同一个方块实体类型：代码、链接、变量快照的行为完全一样，只有特权标志不同。
		// 方块实体类型只是「哪种方块能挂这个实体」，多挂一个方块不用再注册一份
		() -> Builder.of(MicroProcessorBlockEntity::new, MLogBlocks.MICRO_PROCESSOR.get(), MLogBlocks.WORLD_PROCESSOR.get())
			.build(null)
	);
}
