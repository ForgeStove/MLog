package io.github.forgestove.mlog.content.memory;
import io.github.forgestove.mlog.core.register.MLogBlockEntities;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.LVarIO.EntityRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
/**
 * 内存方块的方块实体。
 * <p>每个槽二选一：{@code numberMemory} 里的数字，或者 {@code objectMemory} 里的对象。
 * 两者合成一个 {@code Object[]} 的话每个数字都要装箱，所以分成两个数组，
 * 拿 {@link #SENTINEL} 当「这个槽存的是数字」的标记。
 * <p>值全部由逻辑侧经 {@code read} / {@code write} 读写；世界里不显示，也就不同步给客户端——
 * 内存库有 512 个槽，塞进方块更新包不是个小数目。
 */
public class MemoryBlockEntity extends BlockEntity implements MLogSenseable {
	/** 占住 {@link #objectMemory} 的那个槽，表示该槽存的是 {@link #numberMemory} 里的数字。 */
	private static final Object SENTINEL = new Object();
	private static final String NBT_SLOTS = "slots";
	private final Object[] objectMemory;
	private final double[] numberMemory;
	public MemoryBlockEntity(BlockPos pos, BlockState state) {
		super(MLogBlockEntities.MEMORY.get(), pos, state);
		objectMemory = new Object[capacity()];
		numberMemory = new double[capacity()];
		Arrays.fill(objectMemory, SENTINEL);
	}
	/** @return 槽位数，由方块给。几种内存方块共用一个方块实体类型，容量只能现问方块。 */
	public int capacity() {
		return getBlockState().getBlock() instanceof MemoryBlock memory ? memory.memoryCapacity : 0;
	}
	/**
	 * 读一个槽位。
	 * <p>越界给空值：给 0 的话下标写错了会悄悄读到 0，看不出问题。
	 * <p>世界内存元只有特权处理器读得动。
	 */
	@Override
	public boolean read(LVar position, LVar output, boolean callerPrivileged) {
		if (privileged() && !callerPrivileged) return false;
		var address = address(position);
		if (address < 0 || address >= objectMemory.length) {
			output.setobj(null);
			return true;
		}
		var value = objectMemory[address];
		if (value == SENTINEL) output.setnum(numberMemory[address]);
			// 实体槽里存的是 UUID：读到的时候才去世界里找，读档时实体多半还没进来
		else if (value instanceof EntityRef ref) output.setobj(ref.resolve(level));
		else output.setobj(value);
		return true;
	}
	/**
	 * @return 是不是世界内存元。几种内存方块共用一个方块实体类型，特权只看挂的是哪个方块，
	 * 	和 {@code MicroProcessorBlockEntity#privileged()} 一个口径。
	 */
	public boolean privileged() {
		return getBlockState().getBlock() instanceof WorldCellBlock;
	}
	/**
	 * @return 位置对应的槽位下标，位置不是数字时返回 -1。
	 * 	<p>按数值取的话，非空对象会被当成 1，于是 {@code write x to cell1 "foo"}
	 * 	会莫名写进 1 号槽。这里按「不是数字就不认」处理。
	 */
	private static int address(LVar position) {
		return position.isobj ? -1 : (int) position.num();
	}
	/**
	 * 写一个槽位，越界什么都不做。
	 * <p>值没变就不标脏：逻辑每 tick 把同一个值写回来是常态，不挡一下的话区块会一直是脏的。
	 */
	@Override
	public boolean write(LVar position, LVar value, boolean callerPrivileged) {
		if (privileged() && !callerPrivileged) return false;
		var address = address(position);
		if (address < 0 || address >= objectMemory.length) return false;
		if (value.isobj) {
			// 实体只留 UUID 和类型：槽位可能活得比实体久，攥着实体实例等于替它续命
			var stored = value.objval instanceof Entity entity ? new EntityRef(entity) : value.objval;
			if (objectMemory[address] == stored) return true;
			objectMemory[address] = stored;
		} else {
			var number = value.num();
			if (objectMemory[address] == SENTINEL && numberMemory[address] == number) return true;
			objectMemory[address] = SENTINEL;
			numberMemory[address] = number;
		}
		// 不标脏的话这次写入只在内存里，区块不会因此存档，重载就回去了
		setChanged();
		return true;
	}
	/** 只有容量是自己的读数，其余退回方块本体的通用读数（{@code @x} / {@code @id} 这些）。 */
	@Override
	public double sense(String access) {
		if (LAccess.byName(access) == LAccess.memoryCapacity) return capacity();
		return level == null ? 0 : MLogSenseables.generic(level, getBlockPos()).sense(access);
	}
	@Override
	public Object senseObject(String access) {
		return level == null ? NO_SENSED : MLogSenseables.generic(level, getBlockPos()).senseObject(access);
	}
	@Override
	protected void saveAdditional(CompoundTag tag, Provider registries) {
		super.saveAdditional(tag, registries);
		var slots = new ListTag();
		for (var i = 0; i < objectMemory.length; i++) {
			var value = objectMemory[i];
			// 数字槽位也走同一套编码：读回来一眼能看出这个槽是数字还是对象
			slots.add(LVarIO.write(value == SENTINEL ? numberMemory[i] : value));
		}
		tag.put(NBT_SLOTS, slots);
	}
	@Override
	protected void loadAdditional(CompoundTag tag, Provider registries) {
		super.loadAdditional(tag, registries);
		Arrays.fill(objectMemory, SENTINEL);
		Arrays.fill(numberMemory, 0);
		var slots = tag.getList(NBT_SLOTS, Tag.TAG_COMPOUND);
		// 存档里的槽位数和当前容量对不上时（换过方块、改过容量）多的丢掉、缺的留 0
		for (var i = 0; i < Math.min(slots.size(), objectMemory.length); i++) {
			var value = LVarIO.read(slots.getCompound(i));
			if (value instanceof Number number) numberMemory[i] = number.doubleValue();
			else objectMemory[i] = value;
		}
	}
}
