package io.github.forgestove.mlog.content.processor;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import io.github.forgestove.mlog.core.rule.MLogRules.Rule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
/** 世界处理器：速率上限更高，不受连接范围限制，且可连接特权方块。 */
public class WorldProcessorBlockEntity extends AbstractProcessorBlockEntity {
	public static final int INSTRUCTIONS_PER_TICK = 1000;
	public WorldProcessorBlockEntity(BlockPos pos, BlockState state) {
		super(MLogBlockEntities.WORLD_PROCESSOR.get(), pos, state);
	}
	@Override
	protected int instructionsPerTick() {
		return INSTRUCTIONS_PER_TICK;
	}
	@Override
	protected Rule rule() {
		return Rule.disableWorldProcessor;
	}
	@Override
	protected boolean inRange(BlockPos target, boolean outside) {
		return true;
	}
	@Override
	protected boolean linkable(Block target) {
		return true;
	}
	@Override
	protected boolean accessAllowed(boolean callerPrivileged) {
		return callerPrivileged;
	}
	@Override
	public boolean privileged() {
		return true;
	}
}
