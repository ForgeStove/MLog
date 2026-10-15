package io.github.forgestove.mlog.content.memory;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
/** 逻辑用 {@code read} / {@code write} 按槽位下标读写，每个槽可存数字或对象；值不显示在方块上，仅由逻辑读出，槽位数由各自的方块实体定下。 */
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
