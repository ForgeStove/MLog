package io.github.forgestove.mlog.content.memory;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
/**
 * 内存方块。
 * <p>逻辑用 {@code read} / {@code write} 按槽位下标读写，每个槽既可存数字也可存对象。
 * 世界中它是普通方块，值只能由逻辑读出，不在方块上显示。
 * <p>槽位数不由方块持有，改由各自的方块实体定下。
 */
public abstract class AbstractMemoryBlock extends BaseEntityBlock {
	protected AbstractMemoryBlock(Properties properties) {
		super(properties);
	}
	/** 默认的 {@code INVISIBLE} 会一并隐藏方块模型，须显式返回模型。 */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}
}
