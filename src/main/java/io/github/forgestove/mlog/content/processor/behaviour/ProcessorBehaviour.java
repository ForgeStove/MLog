package io.github.forgestove.mlog.content.processor.behaviour;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
public abstract class ProcessorBehaviour {
	protected final AbstractProcessorBlockEntity blockEntity;
	private int lazyTickRate = 10;
	private int lazyTickCounter = 10;
	protected ProcessorBehaviour(AbstractProcessorBlockEntity blockEntity) {
		this.blockEntity = blockEntity;
	}
	public static <T extends ProcessorBehaviour> @Nullable T get(BlockGetter level, BlockPos pos, BehaviourType<T> type) {
		return get(level.getBlockEntity(pos), type);
	}
	public static <T extends ProcessorBehaviour> @Nullable T get(@Nullable BlockEntity be, BehaviourType<T> type) {
		return be instanceof AbstractProcessorBlockEntity processor ? processor.getBehaviour(type) : null;
	}
	public abstract BehaviourType<?> getType();
	public void initialize() {}
	public void tick() {
		if (--lazyTickCounter > 0) return;
		lazyTickCounter = lazyTickRate;
		lazyTick();
	}
	public void lazyTick() {}
	public void read(CompoundTag tag, Provider registries, boolean clientPacket) {}
	public void write(CompoundTag tag, Provider registries, boolean clientPacket) {}
	public void unload() {}
	public void destroy() {}
	public void setLazyTickRate(int lazyTickRate) {
		this.lazyTickRate = lazyTickRate;
		lazyTickCounter = lazyTickRate;
	}
	public BlockPos getPos() {
		return blockEntity.getBlockPos();
	}
	public @Nullable Level getWorld() {
		return blockEntity.getLevel();
	}
	public AbstractProcessorBlockEntity getBlockEntity() {
		return blockEntity;
	}
}
