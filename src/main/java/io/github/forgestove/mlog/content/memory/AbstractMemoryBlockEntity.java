package io.github.forgestove.mlog.content.memory;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.LVarIO.EntityRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
/**
 * 内存方块的方块实体。
 * <p>每个槽二选一：{@code numberMemory} 里的数字，或 {@code objectMemory} 里的对象。
 * 两者若合成一个 {@code Object[]}，每个数字都要装箱，故分为两个数组，
 * 以 {@link #SENTINEL} 标记该槽存的是数字。
 * <p>值全部由逻辑侧经 {@code read} / {@code write} 读写；世界中不显示，故不同步给客户端——
 * 内存库有 512 个槽，全部塞进方块更新包开销不小。
 */
public abstract class AbstractMemoryBlockEntity extends BlockEntity implements MLogSenseable, Privileged {
	/** 占据 {@link #objectMemory} 中该下标的槽位，表示该槽存的是 {@link #numberMemory} 里的数字。 */
	private static final Object SENTINEL = new Object();
	private static final String NBT_SLOTS = "slots";
	private final Object[] objectMemory;
	private final double[] numberMemory;
	/** @param type 三种内存方块各挂各的类型 */
	protected AbstractMemoryBlockEntity(BlockEntityType<?> type, int capacity, BlockPos pos, BlockState state) {
		super(type, pos, state);
		objectMemory = new Object[capacity];
		numberMemory = new double[capacity];
		Arrays.fill(objectMemory, SENTINEL);
	}
	public int capacity() {
		return objectMemory.length;
	}
	/**
	 * 读一个槽位。
	 * <p>越界返回空值：若返回 0，下标写错时会读到 0 而无从察觉。
	 * <p>世界内存元仅特权处理器可读。
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
			// 实体槽存的是 UUID：读取时才去世界中查找，读档时实体通常尚未加载
		else if (value instanceof EntityRef ref) output.setobj(ref.resolve(level));
		else output.setobj(value);
		return true;
	}
	/**
	 * @return 位置对应的槽位下标，位置不是数字时返回 -1。
	 * 	<p>若按数值取值，非空对象会被当作 1，{@code write x to cell1 "foo"}
	 * 	便会写入 1 号槽。此处按非数字即不处理。
	 */
	private static int address(LVar position) {
		return position.isobj ? -1 : (int) position.num();
	}
	/**
	 * 写一个槽位，越界什么都不做。
	 * <p>值未变化则不标脏：逻辑每 tick 回写同一个值是常态，不加此判断区块会一直处于脏状态。
	 */
	@Override
	public boolean write(LVar position, LVar value, boolean callerPrivileged) {
		if (privileged() && !callerPrivileged) return false;
		var address = address(position);
		if (address < 0 || address >= objectMemory.length) return false;
		if (value.isobj) {
			// 实体只存 UUID 与类型：槽位可能比实体存续更久，持有实体实例会使其无法卸载
			var stored = value.objval instanceof Entity entity ? new EntityRef(entity) : value.objval;
			if (objectMemory[address] == stored) return true;
			objectMemory[address] = stored;
		} else {
			var number = value.num();
			if (objectMemory[address] == SENTINEL && numberMemory[address] == number) return true;
			objectMemory[address] = SENTINEL;
			numberMemory[address] = number;
		}
		// 不标脏时该次写入只存在于内存，区块不会存档，重载后即丢失
		setChanged();
		return true;
	}
	/** 仅容量是本类的读数，其余退回方块本体的通用读数（{@code @x} / {@code @id} 等）。 */
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
			// 数字槽位也走同一套编码：读回时可据此判断该槽是数字还是对象
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
		// 存档槽位数与当前容量不一致时（换过方块、改过容量），多余部分丢弃、缺少部分留 0
		for (var i = 0; i < Math.min(slots.size(), objectMemory.length); i++) {
			var value = LVarIO.read(slots.getCompound(i));
			if (value instanceof Number number) numberMemory[i] = number.doubleValue();
			else objectMemory[i] = value;
		}
	}
}
