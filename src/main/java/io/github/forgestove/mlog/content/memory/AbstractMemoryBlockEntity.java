package io.github.forgestove.mlog.content.memory;
import io.github.forgestove.mlog.logic.*;
import io.github.forgestove.mlog.logic.LVarIO.EntityRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
/** 内存方块的方块实体：数字存 {@link #numberMemory}、对象存 {@link #objectMemory}，以 {@link #SENTINEL} 标记该槽存的是数字；合成一个 {@code Object[]} 会把每个数字装箱，故分作两个数组。 */
public abstract class AbstractMemoryBlockEntity extends BlockEntity implements MLogSenseable, Privileged {
	private static final Object SENTINEL = new Object();
	private static final String NBT_SLOTS = "slots";
	private final Object[] objectMemory;
	private final double[] numberMemory;
	protected AbstractMemoryBlockEntity(BlockEntityType<?> type, int capacity, BlockPos pos, BlockState state) {
		super(type, pos, state);
		objectMemory = new Object[capacity];
		numberMemory = new double[capacity];
		Arrays.fill(objectMemory, SENTINEL);
	}
	/** 位置不是数字时返回 -1：按数值取值会把非空对象当作 1 而命中错误槽位。 */
	private static int address(LVar position) {
		return position.isobj ? -1 : (int) position.num();
	}
	public int capacity() {
		return objectMemory.length;
	}
	@Override
	public double sense(String access) {
		if (LAccess.byName(access) == LAccess.memoryCapacity) return capacity();
		return level == null ? 0 : MLogSenseables.generic(level, getBlockPos()).sense(access);
	}
	@Override
	public Object senseObject(String access) {
		return level == null ? NO_SENSED : MLogSenseables.generic(level, getBlockPos()).senseObject(access);
	}
	/** 越界返回 {@code null}：返回 0 会让下标写错时无从察觉；世界内存元仅特权处理器可读。 */
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
	/** 越界直接返回；值未变化则不标脏，否则逻辑每 tick 回写同一值会使区块一直处于脏状态。 */
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
		// 不标脏则写入只在内存，区块不存档，重载后丢失
		setChanged();
		return true;
	}
	@Override
	protected void loadAdditional(CompoundTag tag, Provider registries) {
		super.loadAdditional(tag, registries);
		Arrays.fill(objectMemory, SENTINEL);
		Arrays.fill(numberMemory, 0);
		var slots = tag.getList(NBT_SLOTS, Tag.TAG_COMPOUND);
		// 存档槽位数与当前容量不一致时，多余部分丢弃、缺少部分留 0
		for (var i = 0; i < Math.min(slots.size(), objectMemory.length); i++) {
			var value = LVarIO.read(slots.getCompound(i));
			if (value instanceof Number number) numberMemory[i] = number.doubleValue();
			else objectMemory[i] = value;
		}
	}
	@Override
	protected void saveAdditional(CompoundTag tag, Provider registries) {
		super.saveAdditional(tag, registries);
		var slots = new ListTag();
		for (var i = 0; i < objectMemory.length; i++) {
			var value = objectMemory[i];
			// 数字槽位也走同一套编码：读取时据此判断该槽是数字还是对象
			slots.add(LVarIO.write(value == SENTINEL ? numberMemory[i] : value));
		}
		tag.put(NBT_SLOTS, slots);
	}
}
