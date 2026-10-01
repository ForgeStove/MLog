package io.github.forgestove.mlog.content.processor;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import io.github.forgestove.mlog.core.rule.MLogRules.Rule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
/** 微型逻辑处理器：链接受连接范围限制，速率固定且只能向下调整。 */
public class MicroProcessorBlockEntity extends AbstractProcessorBlockEntity {
	public static final int INSTRUCTIONS_PER_TICK = 6;
	public MicroProcessorBlockEntity(BlockPos pos, BlockState state) {
		super(MLogBlockEntities.MICRO_PROCESSOR.get(), pos, state);
	}
	@Override
	protected int instructionsPerTick() {
		return INSTRUCTIONS_PER_TICK;
	}
	@Override
	protected Rule rule() {
		return Rule.disableMicroProcessor;
	}
}
