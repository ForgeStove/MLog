package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.compat.create.CreateSenseables;
import io.github.forgestove.mlog.core.MLogMods;
import net.minecraft.core.*;
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
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** {@link MLogSenseable} 适配：方块实体自身实现的直接采用，否则用通用适配器。 */
public class MLogSenseables {
	/** 红石输入强度的属性名。它不是方块状态，单独走 {@link RedstoneSources}。 */
	public static final String REDSTONE = "redstone";
	/** Create 值设置的属性名。 */
	public static final String VALUE = "value";
	/** Create 过滤槽的属性名。 */
	public static final String FILTER = "filter";
	/** 名字到内容的解析缓存，未解析出时为 {@link #NONE}；注册表运行期不变，可长期复用。 */
	private static final Map<String, Object> CONTENTS = new ConcurrentHashMap<>();
	private static final Object NONE = new Object();
	/** Create 是否加载；整个进程不变，缓存以避免每次感测都查询模组列表。 */
	private static @Nullable Boolean createLoaded;
	public static @Nullable MLogSenseable at(Level level, BlockPos pos) {
		return at(level, pos, null);
	}
	/** 目前只有 Create 的过滤槽按面区分，其余读数与面无关。 */
	public static @Nullable MLogSenseable at(Level level, BlockPos pos, @Nullable Direction side) {
		if (!level.isLoaded(pos)) return null;
		var be = level.getBlockEntity(pos);
		if (be instanceof MLogSenseable senseable) return senseable;
		// Create 未加载时此分支不执行，compat 里直接引用 Create 的类因此不会被加载
		if (createLoaded()) {
			var create = CreateSenseables.at(level, pos, be, side);
			if (create != null) return create;
		}
		return new BlockAdapter(level, pos, be);
	}
	private static boolean createLoaded() {
		var cached = createLoaded;
		if (cached == null) createLoaded = cached = MLogMods.create.isLoaded();
		return cached;
	}
	/** 跳过方块实体的 MLogSenseable 实现，否则 {@link #at} 查出自身并递归。 */
	public static MLogSenseable generic(Level level, BlockPos pos) {
		return new BlockAdapter(level, pos, level.getBlockEntity(pos));
	}
	public static MLogSenseable of(Entity entity) {
		return new EntityAdapter(entity);
	}
	/** 名字是否为注册项内容；汇编期据此把 {@code @create:honey} 一类识别为字符串常量，执行期据此在 {@code stored()} 中查注册表，两处判据须一致。 */
	public static boolean isContent(String name) {
		return contentType(name) != null;
	}
	/** 同名时按物品算，顺序须与 {@code BlockAdapter.content} 一致。 */
	public static @Nullable VarType contentType(String name) {
		var id = ResourceLocation.tryParse(name);
		if (id == null) return null;
		if (BuiltInRegistries.ITEM.containsKey(id)) return VarType.ITEM;
		if (BuiltInRegistries.FLUID.containsKey(id)) return VarType.FLUID;
		if (BuiltInRegistries.BLOCK.containsKey(id)) return VarType.BLOCK;
		return null;
	}
	/** 方块没有该属性时返回 0。 */
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
	/** 取值按可选值下标回绕；无该属性或无候选值返回 {@code false}。 */
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
		// 状态变更须触发相邻方块与渲染更新，故用 setBlockAndUpdate
		level.setBlockAndUpdate(pos, state.setValue(property, values.get(Math.floorMod(index, values.size()))));
		return true;
	}
	/** 面不参与读数。 */
	private record BlockAdapter(Level level, BlockPos pos, @Nullable BlockEntity be) implements MLogSenseable {
		/** 都不是时返回 {@link #NONE}。 */
		private static Object content(String name) {
			var id = ResourceLocation.tryParse(name);
			if (id == null) return NONE;
			if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.get(id);
			if (BuiltInRegistries.FLUID.containsKey(id)) return BuiltInRegistries.FLUID.get(id);
			if (BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.get(id);
			return NONE;
		}
		@Override
		public double sense(String access) {
			var state = level.getBlockState(pos);
			var known = LAccess.byName(access);
			// 非内置属性：先按物品 / 流体 / 方块名读储量，再按方块状态属性名读
			if (known == null) {
				var stored = stored(access);
				return stored >= 0 ? stored : property(state, access);
			}
			return switch (known) {
				case x -> pos.getX();
				case y -> pos.getY();
				case z -> pos.getZ();
				case id -> BuiltInRegistries.BLOCK.getId(state.getBlock());
				case solid -> state.getCollisionShape(level, pos).isEmpty() ? 0 : 1;
				case air -> state.isAir() ? 1 : 0;
				case hardness -> state.getDestroySpeed(level, pos);
				case hasBlockEntity -> state.hasBlockEntity() ? 1 : 0;
				case raining -> level.isRainingAt(pos) ? 1 : 0;
				case light -> state.getLightEmission(level, pos);
				case blockLight -> level.getBrightness(LightLayer.BLOCK, pos);
				case skyLight -> level.getBrightness(LightLayer.SKY, pos);
				// 红石：邻近最强信号 / 本方块的输出强度 / 比较器读数
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
				// 其余为方块状态属性：按同名属性读取
				default -> property(state, access);
			};
		}
		@Override
		public Object senseObject(String access) {
			var block = level.getBlockState(pos).getBlock();
			if (!(LAccess.byName(access) instanceof LAccess known)) return NO_SENSED;
			return switch (known) {
				case type -> block;
				case name -> block.getName().getString();
				case firstItem -> firstItem();
				default -> NO_SENSED;
			};
		}
		/** 非容器、越界或空槽返回 {@code null} */
		@Override
		public @Nullable Item itemAt(int slot) {
			var items = items();
			if (items == null || slot < 0 || slot >= items.getSlots()) return null;
			var stack = items.getStackInSlot(slot);
			return stack.isEmpty() ? null : stack.getItem();
		}
		/** 无流体能力、越界或空罐返回 {@code null} */
		@Override
		public @Nullable Fluid fluidAt(int tank) {
			var handler = face(FluidHandler.BLOCK);
			if (handler == null || tank < 0 || tank >= handler.getTanks()) return null;
			var stack = handler.getFluidInTank(tank);
			// 空罐的 getFluid() 为 Fluids.EMPTY（真实注册项），须排除
			return stack.isEmpty() ? null : stack.getFluid();
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
			if (!privileged && !LAccess.controlAllowed().contains(access)) return false;
			// redstone 非方块状态：写入虚拟源表表示该坐标应被多少红石充能，由 Mixin 参与信号判定
			if (REDSTONE.equals(access)) {
				if (!(level instanceof ServerLevel serverLevel) || owner == null) return false;
				// 强度为 0~15，越界与属性下标同一套回绕
				var strength = Math.floorMod((int) value.num(), 16);
				// 未给出方向时六个面均接入源，给出时仅该面接入
				return face == null
					? RedstoneSources.charge(serverLevel, pos, owner, strength)
					: RedstoneSources.set(serverLevel, pos, face, owner, strength);
			}
			return setProperty(level, pos, level.getBlockState(pos), access, value.num());
		}
		@Override
		public @Nullable IItemHandler itemHandler() {
			return items();
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
		/** 把该物品的总数设为 {@code amount}，受容器容量与单格上限约束。 */
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
		/** 把该流体的总量设为 {@code amount}，受罐容量约束。 */
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
		@Override
		public void print(String text) {
			if (!(be instanceof SignBlockEntity sign)) return;
			// 告示牌正面仅四行，多余部分丢弃；内容未变则不写入，避免每 tick 推送一次方块更新
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
		/** 熔炉系的烧炼进度，归一化到 0~1；其他方块返回 0。 */
		private double progress() {
			if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return 0;
			var total = furnace.dataAccess.get(AbstractFurnaceBlockEntity.DATA_COOKING_TOTAL_TIME);
			return total <= 0 ? 0 : (double) furnace.dataAccess.get(AbstractFurnaceBlockEntity.DATA_COOKING_PROGRESS) / total;
		}
		private double totalItems() {
			var items = items();
			if (items == null) return 0;
			var total = 0;
			for (var i = 0; i < items.getSlots(); i++) total += items.getStackInSlot(i).getCount();
			return total;
		}
		private double itemCapacity() {
			var items = items();
			if (items == null) return 0;
			var capacity = 0;
			for (var i = 0; i < items.getSlots(); i++) capacity += items.getSlotLimit(i);
			return capacity;
		}
		private double emptySlots() {
			var items = items();
			if (items == null) return 0;
			var empty = 0;
			for (var i = 0; i < items.getSlots(); i++) if (items.getStackInSlot(i).isEmpty()) empty++;
			return empty;
		}
		/** 没有该能力时返回 0。 */
		private double power(boolean capacity) {
			var storage = face(EnergyStorage.BLOCK);
			if (storage == null) return 0;
			return capacity ? storage.getMaxEnergyStored() : storage.getEnergyStored();
		}
		private double countOf(Item item) {
			var items = items();
			if (items == null) return 0;
			var count = 0;
			for (var i = 0; i < items.getSlots(); i++) {
				var stack = items.getStackInSlot(i);
				if (stack.is(item)) count += stack.getCount();
			}
			return count;
		}
		private double amountOf(Fluid fluid) {
			var handler = face(FluidHandler.BLOCK);
			if (handler == null) return 0;
			var amount = 0;
			for (var i = 0; i < handler.getTanks(); i++) {
				var stack = handler.getFluidInTank(i);
				if (stack.getFluid() == fluid) amount += stack.getAmount();
			}
			return amount;
		}
		/** 无流体能力时为 0。 */
		private double totalLiquids() {
			var handler = face(FluidHandler.BLOCK);
			if (handler == null) return 0;
			var total = 0;
			for (var i = 0; i < handler.getTanks(); i++) total += handler.getFluidInTank(i).getAmount();
			return total;
		}
		/** 无流体能力时为 0。 */
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
		/** 原版容器用 {@link InvWrapper} 包装，方块自身的能力（如 Create 的 {@code SmartInventory}）直接使用。 */
		private @Nullable IItemHandler items() {
			if (be instanceof Container container) return new InvWrapper(container);
			return face(ItemHandler.BLOCK);
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
	private record EntityAdapter(Entity entity) implements MLogSenseable {
		@Override
		public double sense(String access) {
			if (!(LAccess.byName(access) instanceof LAccess known)) return 0;
			return switch (known) {
				case x -> entity.getX();
				case y -> entity.getY();
				case z -> entity.getZ();
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
		public void setProp(LAccess access, LVar value) {
			var number = value.num();
			switch (access) {
				case x -> entity.teleportTo(number, entity.getY(), entity.getZ());
				case y -> entity.teleportTo(entity.getX(), number, entity.getZ());
				case z -> entity.teleportTo(entity.getX(), entity.getY(), number);
				case velocityX, velocityY, velocityZ -> {
					var movement = entity.getDeltaMovement();
					entity.setDeltaMovement(
						access == LAccess.velocityX ? number : movement.x,
						access == LAccess.velocityY ? number : movement.y,
						access == LAccess.velocityZ ? number : movement.z
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
			// 有物品栏的单位写入第一格
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
		@Override
		public Object senseObject(String access) {
			if (!(LAccess.byName(access) instanceof LAccess known)) return NO_SENSED;
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
		/** 物品总数；容量按每格 64 估算。 */
		private double items(boolean capacity) {
			if (!(entity instanceof InventoryCarrier carrier)) return 0;
			var container = carrier.getInventory();
			if (capacity) return container.getContainerSize() * 64D;
			var total = 0;
			for (var i = 0; i < container.getContainerSize(); i++) total += container.getItem(i).getCount();
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
		/** 不是生物时都是 0。 */
		private double health(boolean max) {
			if (!(entity instanceof LivingEntity living)) return 0;
			return max ? living.getMaxHealth() : living.getHealth();
		}
	}
}
