package io.github.forgestove.mlog.content.memory;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
/**
 * 内存方块。
 * <p>逻辑用 {@code read} / {@code write} 按槽位下标读写它，每个槽既能存数字也能存对象。
 * 世界里它就是一块普通方块，值只能靠逻辑读出来，方块上不画。
 */
public class MemoryBlock extends BaseEntityBlock {
	public static final MapCodec<MemoryBlock> CODEC = simpleCodec(MemoryBlock::new);
	/** 槽位数。子类在构造里给别的值。 */
	public final int memoryCapacity;
	public MemoryBlock(Properties properties) {
		this(64, properties);
	}
	protected MemoryBlock(int memoryCapacity, Properties properties) {
		super(properties);
		this.memoryCapacity = memoryCapacity;
	}
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	/** 默认的 {@code INVISIBLE} 会把方块模型也吃掉，整方块得显式要回模型。 */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}
	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MemoryBlockEntity(pos, state);
	}
}
