package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class Senseables {
	public static final String REDSTONE = "redstone";
	public static final String VALUE = "value";
	public static final String FILTER = "filter";
	private static final Map<String, Object> CONTENTS = new ConcurrentHashMap<>();
	private static final Object NONE = new Object();
	public static @Nullable Senseable at(Level level, BlockPos pos) {
		return at(level, pos, null);
	}
	public static @Nullable Senseable at(Level level, BlockPos pos, @Nullable Direction side) {
		if (!level.isLoaded(pos)) return null;
		var be = level.getBlockEntity(pos);
		if (be instanceof Senseable senseable) return senseable;
		return decorate(new BlockAdapter(level, pos, be), level, pos, be, side);
	}
	/** 跳过方块实体的 Senseable 实现，否则 {@link #at} 查出自身并递归。 */
	public static Senseable generic(Level level, BlockPos pos) {
		var be = level.getBlockEntity(pos);
		return decorate(new BlockAdapter(level, pos, be), level, pos, be, null);
	}
	public static Senseable of(Entity entity) {
		Senseable senseable = new EntityAdapter(entity);
		for (var sense : AccessRegistry.entitySenses()) senseable = sense.wrap(senseable, entity);
		return senseable;
	}
	private static Senseable decorate(Senseable base, Level level, BlockPos pos, @Nullable BlockEntity be, @Nullable Direction side) {
		for (var sense : AccessRegistry.blockSenses()) base = sense.wrap(base, level, pos, be, side);
		return base;
	}
	public static boolean isContent(String name) {
		return contentType(name) != null;
	}
	/** 同名时按物品算，顺序要和 {@code BlockAdapter.content} 一致。 */
	public static @Nullable VarType contentType(String name) {
		var id = ResourceLocation.tryParse(name);
		if (id == null) return null;
		if (BuiltInRegistries.ITEM.containsKey(id)) return VarType.ITEM;
		if (BuiltInRegistries.FLUID.containsKey(id)) return VarType.FLUID;
		if (BuiltInRegistries.BLOCK.containsKey(id)) return VarType.BLOCK;
		return null;
	}
	public static double property(BlockState state, String name) {
		for (var property : state.getProperties()) {
			if (!property.getName().equals(name)) continue;
			return read(state, property);
		}
		return 0;
	}
	private static <T extends Comparable<T>> double read(BlockState state, Property<T> property) {
		Comparable<?> value = state.getValue(property);
		return switch (value) {
			case Boolean v -> v ? 1 : 0;
			case Direction v -> v.get3DDataValue();
			case Enum<?> v -> v.ordinal();
			case Number v -> v.doubleValue();
			default -> 0;
		};
	}
	/**
	 * 取值按可选值下标回绕。
	 * 无该属性或无候选值返回 {@code false}。
	 */
	public static boolean setProperty(Level level, BlockPos pos, BlockState state, String name, double value) {
		for (var property : state.getProperties()) {
			if (!property.getName().equals(name)) continue;
			return apply(level, pos, state, property, (int) value);
		}
		return false;
	}
	private static <T extends Comparable<T>> boolean apply(Level level, BlockPos pos, BlockState state, Property<T> property, int index) {
		var values = List.copyOf(property.getPossibleValues());
		if (values.isEmpty()) return false;
		// 状态变更要触发相邻方块和渲染更新，所以用 setBlockAndUpdate
		level.setBlockAndUpdate(pos, state.setValue(property, values.get(Math.floorMod(index, values.size()))));
		return true;
	}
	/**
	 * 兼容模组提供的每槽空间，语义同 Create 的存量转信器。
	 * 0 表示该槽不参与统计。
	 */
	public interface SlotSpace {
		long of(IItemHandler items, int slot);
	}
	/** 方块读取不使用面方向。 */
	private record BlockAdapter(Level level, BlockPos pos, @Nullable BlockEntity be) implements Senseable {
		private static Object content(String name) {
			var id = ResourceLocation.tryParse(name);
			if (id == null) return NONE;
			if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.get(id);
			if (BuiltInRegistries.FLUID.containsKey(id)) return BuiltInRegistries.FLUID.get(id);
			if (BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.get(id);
			return NONE;
		}
		private static double itemCapacity(IItemHandler items, @Nullable SlotSpace compat) {
			var capacity = 0L;
			for (var i = 0; i < items.getSlots(); i++) capacity += slotSpace(compat, items, i);
			return capacity;
		}
		/** 与 Create 的存量转信器一致，兼容模组优先，其余按 min(物品最大堆叠，缺省 64, 槽上限)。 */
		private static long slotSpace(@Nullable SlotSpace compat, IItemHandler items, int slot) {
			if (compat != null) return compat.of(items, slot);
			var stack = items.getStackInSlot(slot);
			return Math.min(stack.getOrDefault(DataComponents.MAX_STACK_SIZE, 64), items.getSlotLimit(slot));
		}
		@Override
		public double sense(String access) {
			var state = level.getBlockState(pos);
			var known = VanillaAccess.byName(access);
			// 非内置属性，先按物品 / 流体 / 方块名读储量，再按方块状态属性名读
			if (known == null) {
				var stored = stored(access);
				return stored >= 0 ? stored : property(state, access);
			}
			return switch (known) {
				case x -> lower().x;
				case y -> lower().y;
				case z -> lower().z;
				case id -> BuiltInRegistries.BLOCK.getId(state.getBlock());
				case solid -> state.getCollisionShape(level, pos).isEmpty() ? 0 : 1;
				case air -> state.isAir() ? 1 : 0;
				case hardness -> state.getDestroySpeed(level, pos);
				case hasBlockEntity -> state.hasBlockEntity() ? 1 : 0;
				case raining -> level.isRainingAt(pos) ? 1 : 0;
				case light -> state.getLightEmission(level, pos);
				case blockLight -> level.getBrightness(LightLayer.BLOCK, pos);
				case skyLight -> level.getBrightness(LightLayer.SKY, pos);
				// 红石，邻近最强信号 / 本方块的输出强度 / 比较器读数
				case redstone -> level.getBestNeighborSignal(pos);
				case emittedRedstone -> emittedRedstone(state);
				case comparator -> state.getAnalogOutputSignal(level, pos);
				case progress -> progress();
				case totalItems -> totalItems();
				case totalLiquids -> totalLiquids();
				case itemCapacity -> itemCapacity();
				case emptySlots -> emptySlots();
				case hasFluid -> state.getFluidState().isEmpty() ? 0 : 1;
				case liquidCapacity -> liquidCapacity();
				case totalPower -> power(false);
				case powerCapacity -> power(true);
				default -> property(state, access);
			};
		}
		@Override
		public Object senseObject(String access) {
			var block = level.getBlockState(pos).getBlock();
			var known = VanillaAccess.byName(access);
			if (known == null) return NO_SENSED;
			return switch (known) {
				case type -> block;
				case name -> block.getName().getString();
				case firstItem -> firstItem();
				default -> NO_SENSED;
			};
		}
		@Override
		public @Nullable Item itemAt(int slot) {
			var items = items();
			if (items == null || slot < 0 || slot >= items.getSlots()) return null;
			var stack = items.getStackInSlot(slot);
			return stack.isEmpty() ? null : stack.getItem();
		}
		@Override
		public @Nullable Fluid fluidAt(int tank) {
			var handler = face(FluidHandler.BLOCK);
			if (handler == null || tank < 0 || tank >= handler.getTanks()) return null;
			var stack = handler.getFluidInTank(tank);
			// 空罐的 getFluid() 是 Fluids.EMPTY（真实注册项），要排除掉
			return stack.isEmpty() ? null : stack.getFluid();
		}
		@Override
		public @Nullable IItemHandler itemHandler() {
			return items();
		}
		@Override
		public boolean control(
			String access,
			LVar value,
			@Nullable Direction face,
			@Nullable BlockPos owner,
			boolean privileged,
			int index
		) {
			// 非特权处理器受白名单约束，否则任意方块的任意状态都可被修改
			if (!privileged && !AccessRegistry.controlAllowed().contains(access)) return false;
			// redstone 非方块状态，写入虚拟源表表示该坐标应被多少红石充能，由 Mixin 参与信号判定
			if (REDSTONE.equals(access)) {
				if (!(level instanceof ServerLevel serverLevel) || owner == null) return false;
				// 红石强度范围是 0 到 15，越界时按属性下标的同一套规则回绕
				var strength = Math.floorMod((int) value.num(), 16);
				// 未指定方向时六个面都接入信号源，指定方向时只接入该面
				return face == null
					? RedstoneSources.charge(serverLevel, pos, owner, strength)
					: RedstoneSources.set(serverLevel, pos, face, owner, strength);
			}
			return setProperty(level, pos, level.getBlockState(pos), access, value.num());
		}
		@Override
		public void setProp(String content, LVar value) {
			var id = ResourceLocation.tryParse(content);
			if (id == null) return;
			var amount = (int) value.num();
			if (amount < 0) return;
			if (BuiltInRegistries.ITEM.containsKey(id)) {
				setItems(BuiltInRegistries.ITEM.get(id), amount);
				return;
			}
			if (BuiltInRegistries.FLUID.containsKey(id)) setFluid(BuiltInRegistries.FLUID.get(id), amount);
		}
		@Override
		public void print(String text) {
			if (!(be instanceof SignBlockEntity sign)) return;
			// 告示牌正面仅四行，多余部分丢弃
			// 内容未变则不写入，避免每 tick 推送一次方块更新
			var lines = text.split("\n", -1);
			var current = sign.getFrontText();
			var next = current;
			for (var i = 0; i < current.getMessages(false).length; i++) {
				var want = Component.literal(i < lines.length ? lines[i] : "");
				if (want.equals(current.getMessage(i, false))) continue;
				next = next.setMessage(i, want, want);
			}
			if (next != current) sign.setText(next, true);
		}
		private void setItems(Item item, int amount) {
			var items = items();
			if (items == null) return;
			var current = (int) countOf(item);
			if (amount < current) {
				var remaining = current - amount;
				for (var i = items.getSlots() - 1; i >= 0 && remaining > 0; i--) {
					if (!items.getStackInSlot(i).is(item)) continue;
					remaining -= items.extractItem(i, remaining, false).getCount();
				}
				return;
			}
			ItemHandlerHelper.insertItemStacked(items, new ItemStack(item, amount - current), false);
		}
		private void setFluid(Fluid fluid, int amount) {
			var handler = face(FluidHandler.BLOCK);
			if (handler == null) return;
			var current = (int) amountOf(fluid);
			if (amount < current) {
				var remaining = current - amount;
				for (var i = 0; i < handler.getTanks() && remaining > 0; i++) {
					if (handler.getFluidInTank(i).getFluid() != fluid) continue;
					remaining -= handler.drain(new FluidStack(fluid, remaining), FluidAction.EXECUTE).getAmount();
				}
				return;
			}
			if (amount > current) handler.fill(new FluidStack(fluid, amount - current), FluidAction.EXECUTE);
		}
		/** 名字不是注册项时返回 {@code -1}，以区别于数量为零的 {@code 0}。 */
		private double stored(String name) {
			var content = CONTENTS.computeIfAbsent(name, BlockAdapter::content);
			if (content == NONE) return -1;
			if (content instanceof Item item) return countOf(item);
			if (content instanceof Fluid fluid) return amountOf(fluid);
			// 方块按其物品形态计数，无物品形态者不计数
			var item = ((Block) content).asItem();
			return item == Items.AIR ? -1 : countOf(item);
		}
		private double emittedRedstone(BlockState state) {
			var best = 0;
			for (var direction : Direction.values()) best = Math.max(best, state.getSignal(level, pos, direction));
			return best;
		}
		/**
		 * 熔炉系的烧炼进度，归一化到 0~1。
		 * 其他方块返回 0。
		 */
		private double progress() {
			if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return 0;
			var total = furnace.dataAccess.get(AbstractFurnaceBlockEntity.DATA_COOKING_TOTAL_TIME);
			return total <= 0 ? 0 : (double) furnace.dataAccess.get(AbstractFurnaceBlockEntity.DATA_COOKING_PROGRESS) / total;
		}
		private double totalItems() {
			var items = items();
			if (items == null) return 0;
			var compat = compatSpace();
			var total = 0;
			for (var i = 0; i < items.getSlots(); i++) {
				// 空间为 0 的槽与存量转信器一样不参与统计
				if (slotSpace(compat, items, i) == 0) continue;
				total += items.getStackInSlot(i).getCount();
			}
			return Math.min(total, itemCapacity(items, compat));
		}
		private double itemCapacity() {
			var items = items();
			return items == null ? 0 : itemCapacity(items, compatSpace());
		}
		private @Nullable SlotSpace compatSpace() {
			for (var provider : AccessRegistry.slotSpaces()) {
				var space = provider.of(be);
				if (space != null) return space;
			}
			return null;
		}
		private Vec3 lower() {
			return Vec3.atLowerCornerOf(pos);
		}
		private double emptySlots() {
			var items = items();
			if (items == null) return 0;
			var empty = 0;
			for (var i = 0; i < items.getSlots(); i++) if (items.getStackInSlot(i).isEmpty()) empty++;
			return empty;
		}
		private double power(boolean capacity) {
			var storage = face(EnergyStorage.BLOCK);
			if (storage == null) return 0;
			return capacity ? storage.getMaxEnergyStored() : storage.getEnergyStored();
		}
		private double countOf(Item item) {
			var items = items();
			if (items == null) return 0;
			var compat = compatSpace();
			var count = 0;
			for (var i = 0; i < items.getSlots(); i++) {
				// 空间为 0 的槽与存量转信器一样不参与统计
				if (slotSpace(compat, items, i) == 0) continue;
				var stack = items.getStackInSlot(i);
				if (stack.is(item)) count += stack.getCount();
			}
			return Math.min(count, itemCapacity(items, compat));
		}
		private double amountOf(Fluid fluid) {
			var handler = face(FluidHandler.BLOCK);
			if (handler == null) return 0;
			var amount = 0;
			var capacity = 0;
			for (var i = 0; i < handler.getTanks(); i++) {
				var space = handler.getTankCapacity(i);
				// 容量为 0 的罐与存量转信器一样不参与统计
				if (space == 0) continue;
				capacity += space;
				var stack = handler.getFluidInTank(i);
				if (stack.getFluid() == fluid) amount += stack.getAmount();
			}
			return Math.min(amount, capacity);
		}
		private double totalLiquids() {
			var handler = face(FluidHandler.BLOCK);
			if (handler == null) return 0;
			var total = 0;
			var capacity = 0;
			for (var i = 0; i < handler.getTanks(); i++) {
				var space = handler.getTankCapacity(i);
				// 容量为 0 的罐与存量转信器一样不参与统计
				if (space == 0) continue;
				capacity += space;
				total += handler.getFluidInTank(i).getAmount();
			}
			return Math.min(total, capacity);
		}
		private double liquidCapacity() {
			var handler = face(FluidHandler.BLOCK);
			if (handler == null) return 0;
			var capacity = 0;
			for (var i = 0; i < handler.getTanks(); i++) capacity += handler.getTankCapacity(i);
			return capacity;
		}
		private @Nullable Item firstItem() {
			var items = items();
			if (items == null) return null;
			for (var i = 0; i < items.getSlots(); i++) {
				var stack = items.getStackInSlot(i);
				if (!stack.isEmpty()) return stack.getItem();
			}
			return null;
		}
		private @Nullable IItemHandler items() {
			var capability = face(ItemHandler.BLOCK);
			if (capability != null) return capability;
			return be instanceof Container container ? new InvWrapper(container) : null;
		}
		private <T> @Nullable T face(BlockCapability<T, @Nullable Direction> capability) {
			// 方块状态与方块实体都已知，交给带它们的重载，否则每次查询都要内部重查
			var state = level.getBlockState(pos);
			var unsided = level.getCapability(capability, pos, state, be, null);
			if (unsided != null) return unsided;
			for (var direction : Direction.values()) {
				var sided = level.getCapability(capability, pos, state, be, direction);
				if (sided != null) return sided;
			}
			return null;
		}
	}
	private record EntityAdapter(Entity entity) implements Senseable {
		@Override
		public double sense(String access) {
			var known = VanillaAccess.byName(access);
			if (known == null) return 0;
			return switch (known) {
				case x -> entity.position().x;
				case y -> entity.position().y;
				case z -> entity.position().z;
				case velocityX -> entity.getDeltaMovement().x;
				case velocityY -> entity.getDeltaMovement().y;
				case velocityZ -> entity.getDeltaMovement().z;
				case rotation -> entity.getYRot();
				case id -> BuiltInRegistries.ENTITY_TYPE.getId(entity.getType());
				case health -> health(false);
				case maxHealth -> health(true);
				case armor -> entity instanceof LivingEntity living ? living.getAttributeValue(Attributes.ARMOR) : 0;
				case shield -> entity instanceof LivingEntity living ? living.getAbsorptionAmount() : 0;
				case flying -> entity.onGround() ? 0 : 1;
				case boosting -> entity.isSprinting() ? 1 : 0;
				case controlled -> MLogUnits.controlled(entity) ? 1 : 0;
				case totalItems -> items(false);
				case itemCapacity -> items(true);
				case flag -> MLogUnits.flag(entity);
				case dead -> entity.isRemoved() || entity instanceof LivingEntity living && living.isDeadOrDying() ? 1 : 0;
				default -> 0;
			};
		}
		@Override
		public Object senseObject(String access) {
			var known = VanillaAccess.byName(access);
			if (known == null) return NO_SENSED;
			return switch (known) {
				case type -> entity.getType();
				case name -> entity.getDisplayName().getString();
				case firstItem -> firstItem();
				default -> NO_SENSED;
			};
		}
		@Override
		public @Nullable Item itemAt(int slot) {
			if (!(entity instanceof InventoryCarrier carrier)) return null;
			var container = carrier.getInventory();
			if (slot < 0 || slot >= container.getContainerSize()) return null;
			var stack = container.getItem(slot);
			return stack.isEmpty() ? null : stack.getItem();
		}
		@Override
		public void setProp(LAccess access, LVar value) {
			if (!(access instanceof VanillaAccess known)) return;
			var number = value.num();
			switch (known) {
				case x -> entity.teleportTo(number, entity.getY(), entity.getZ());
				case y -> entity.teleportTo(entity.getX(), number, entity.getZ());
				case z -> entity.teleportTo(entity.getX(), entity.getY(), number);
				case velocityX, velocityY, velocityZ -> {
					var movement = entity.getDeltaMovement();
					entity.setDeltaMovement(
						known == VanillaAccess.velocityX ? number : movement.x,
						known == VanillaAccess.velocityY ? number : movement.y,
						known == VanillaAccess.velocityZ ? number : movement.z
					);
				}
				case health -> {
					if (!(entity instanceof LivingEntity living)) return;
					living.setHealth(Mth.clamp((float) number, 0, living.getMaxHealth()));
				}
				case armor -> {
					if (!(entity instanceof LivingEntity living)) return;
					var attribute = living.getAttribute(Attributes.ARMOR);
					if (attribute == null) return;
					attribute.setBaseValue(number);
				}
				case flag -> MLogUnits.setFlag(entity, number);
				default -> {}
			}
		}
		@Override
		public void setProp(String content, LVar value) {
			if (!(entity instanceof LivingEntity living)) return;
			var id = ResourceLocation.tryParse(content);
			if (id == null) return;
			// 如果单位有物品栏，就把物品放进第一格
			if (BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != Items.AIR) {
				var item = BuiltInRegistries.ITEM.get(id);
				if (!(living instanceof InventoryCarrier carrier)) return;
				var count = Math.clamp((int) value.num(), 0, item.getDefaultMaxStackSize());
				var container = carrier.getInventory();
				container.setItem(0, count == 0 ? ItemStack.EMPTY : new ItemStack(item, count));
				container.setChanged();
				return;
			}
			var effect = BuiltInRegistries.MOB_EFFECT.getHolder(id);
			if (effect.isEmpty()) return;
			// 时长以秒计，换算为刻
			var duration = (int) Math.round(value.num() * 20);
			if (duration <= 0) return;
			living.addEffect(new MobEffectInstance(effect.get(), duration));
		}
		/**
		 * 物品总数。
		 * 容量与存量转信器同口径，空格按 64，已装物品按自身最大堆叠，且不超过容器槽上限。
		 */
		private double items(boolean capacity) {
			if (!(entity instanceof InventoryCarrier carrier)) return 0;
			var container = carrier.getInventory();
			var total = 0;
			for (var i = 0; i < container.getContainerSize(); i++) {
				var stack = container.getItem(i);
				total += capacity
					? Math.min(stack.getOrDefault(DataComponents.MAX_STACK_SIZE, 64), container.getMaxStackSize())
					: stack.getCount();
			}
			return total;
		}
		private @Nullable Item firstItem() {
			if (!(entity instanceof InventoryCarrier carrier)) return null;
			var container = carrier.getInventory();
			for (var i = 0; i < container.getContainerSize(); i++) {
				var stack = container.getItem(i);
				if (!stack.isEmpty()) return stack.getItem();
			}
			return null;
		}
		private double health(boolean max) {
			if (!(entity instanceof LivingEntity living)) return 0;
			return max ? living.getMaxHealth() : living.getHealth();
		}
	}
}
