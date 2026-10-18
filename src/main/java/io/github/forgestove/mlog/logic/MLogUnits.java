package io.github.forgestove.mlog.logic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
/** unit 语句共用的生物筛选与搜索。 */
public class MLogUnits {
	/** 绑定与雷达的搜索半径。 */
	public static final double RANGE = 32;
	/** 最后一次 {@code ucontrol} 之后保持控制的游戏刻数。 */
	private static final int CONTROL_TIMEOUT = 600;
	/** 逻辑交互后容器保持打开的刻数。 */
	private static final int CONTAINER_OPEN_TICKS = 10;
	/** 被接管的生物：原目标表与失效时刻。 */
	private static final Map<UUID, Takeover> TAKEOVERS = new HashMap<>();
	/** 单位身上的数值 flag，运行期有效。 */
	private static final Map<UUID, Double> FLAGS = new HashMap<>();
	/** 由逻辑交互打开、等待关闭的容器。 */
	private static final Map<Level, Map<BlockPos, Long>> OPEN_CONTAINERS = new IdentityHashMap<>();
	private static final Flag[] CONTROL_FLAGS = {Flag.MOVE, Flag.LOOK, Flag.JUMP};
	/** 与 {@link Bindable#of} 一致，默认的傀儡与已驯服生物自动视为可绑定。 */
	public static boolean bindable(Entity entity) {
		return Bindable.of(entity);
	}
	/** 类型层面是否可能被绑定，不看驯服状态；供界面列出可绑类型。 */
	public static boolean bindableType(Entity entity) {
		return Bindable.typeOf(entity);
	}
	/** 取当前绑定的生物；已失效时一并清空 {@code @unit}。 */
	public static @Nullable LivingEntity bound(LExecutor exec) {
		if (exec.unit == null) return null;
		var value = exec.unit.obj();
		if (value instanceof LivingEntity living && bindable(living)) return living;
		release(value);
		exec.unit.setobj(null);
		return null;
	}
	/** 绑定只改 {@code @unit}，不接管 AI。 */
	public static void bind(LExecutor exec, @Nullable LivingEntity next) {
		if (exec.unit != null) exec.unit.setobj(next);
	}
	/** 每执行一次 {@code ucontrol} 就续期；首次控制时清空目标表并锁住控制标志。 */
	public static void control(LivingEntity living) {
		if (!(living instanceof Mob mob)) return;
		var now = mob.level().getGameTime();
		var takeover = TAKEOVERS.get(mob.getUUID());
		if (takeover == null) {
			takeover = new Takeover(
				mob,
				new ArrayList<>(mob.goalSelector.getAvailableGoals()),
				new ArrayList<>(mob.targetSelector.getAvailableGoals()),
				mob.getBrain()
			);
			mob.goalSelector.removeAllGoals(goal -> true);
			mob.targetSelector.removeAllGoals(goal -> true);
			for (var flag : CONTROL_FLAGS) {
				mob.goalSelector.disableControlFlag(flag);
				mob.targetSelector.disableControlFlag(flag);
			}
			mob.getNavigation().stop();
			mob.setTarget(null);
			// Brain 生物行为不走 GoalSelector，须换掉整个 Brain 才能停下
			var brain = brain(mob);
			if (mob.level() instanceof ServerLevel level) brain.stopAll(level, mob);
			mob.brain = brain.copyWithoutBehaviors();
			TAKEOVERS.put(mob.getUUID(), takeover);
		}
		takeover.until = now + CONTROL_TIMEOUT;
	}
	/** 超过时限未再控制的生物恢复原版目标；由处理器每 tick 调用。 */
	public static void expire(Level level) {
		if (TAKEOVERS.isEmpty()) return;
		var now = level.getGameTime();
		var iterator = TAKEOVERS.values().iterator();
		while (iterator.hasNext()) {
			var takeover = iterator.next();
			if (takeover.until > now) continue;
			iterator.remove();
			restore(takeover);
		}
	}
	/** 显式释放：unbind、死亡、重编译或方块实体移除。 */
	public static void release(@Nullable Object value) {
		if (!(value instanceof Mob mob)) return;
		var takeover = TAKEOVERS.remove(mob.getUUID());
		if (takeover != null) restore(takeover);
		if (mob.isRemoved() || !mob.isAlive()) FLAGS.remove(mob.getUUID());
	}
	public static double flag(Entity entity) {
		return FLAGS.getOrDefault(entity.getUUID(), 0D);
	}
	public static void setFlag(Entity entity, double value) {
		FLAGS.put(entity.getUUID(), value);
	}
	/** 是否正被逻辑接管。 */
	public static boolean controlled(Entity entity) {
		return TAKEOVERS.containsKey(entity.getUUID());
	}
	/** 模拟玩家开合箱子或木桶。 */
	public static void pulseContainer(Level level, BlockPos pos) {
		if (level.isClientSide) return;
		var state = level.getBlockState(pos);
		if (state.getBlock() instanceof BarrelBlock) {
			if (!state.hasProperty(BarrelBlock.OPEN)) return;
			if (state.getValue(BarrelBlock.OPEN)) {
				refresh(level, pos);
				return;
			}
		} else {
			if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;
			if (chest.getOpenNess(0F) > 0F) {
				refresh(level, pos);
				return;
			}
		}
		container(level, pos, state, true);
		OPEN_CONTAINERS.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos, level.getGameTime() + CONTAINER_OPEN_TICKS);
	}
	/** 由关卡 tick 事件调用，关闭到期的容器。 */
	public static void closeContainers(Level level) {
		var pending = OPEN_CONTAINERS.get(level);
		if (pending == null || pending.isEmpty()) return;
		var now = level.getGameTime();
		var iterator = pending.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (entry.getValue() > now) continue;
			var pos = entry.getKey();
			// 区块未加载时保留条目，加载后再关
			if (!level.isLoaded(pos)) continue;
			iterator.remove();
			container(level, pos, level.getBlockState(pos), false);
		}
		if (pending.isEmpty()) OPEN_CONTAINERS.remove(level);
	}
	/** 只续期自己打开的容器，玩家开着的容器不接管。 */
	private static void refresh(Level level, BlockPos pos) {
		var pending = OPEN_CONTAINERS.get(level);
		if (pending != null) pending.computeIfPresent(pos, (ignored, until) -> level.getGameTime() + CONTAINER_OPEN_TICKS);
	}
	private static void container(Level level, BlockPos pos, BlockState state, boolean open) {
		if (state.getBlock() instanceof BarrelBlock && state.hasProperty(BarrelBlock.OPEN)) {
			if (state.getValue(BarrelBlock.OPEN) == open) return;
			level.setBlock(pos, state.setValue(BarrelBlock.OPEN, open), 3);
			return;
		}
		if (!(state.getBlock() instanceof ChestBlock) || !(level.getBlockEntity(pos) instanceof ChestBlockEntity)) return;
		level.blockEvent(pos, state.getBlock(), 1, open ? 1 : 0);
		if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			var other = pos.relative(ChestBlock.getConnectedDirection(state));
			level.blockEvent(other, level.getBlockState(other).getBlock(), 1, open ? 1 : 0);
		}
	}
	/** 编译替换或方块实体移除时释放绑定。 */
	public static void releaseUnit(@Nullable LExecutor exec) {
		if (exec != null && exec.unit != null) release(exec.unit.obj());
	}
	private static void restore(Takeover takeover) {
		var mob = takeover.mob;
		if (mob.isRemoved() || !mob.isAlive()) return;
		mob.brain = takeover.parked;
		for (var goal : takeover.goals) mob.goalSelector.addGoal(goal.getPriority(), goal.getGoal());
		for (var goal : takeover.targets) mob.targetSelector.addGoal(goal.getPriority(), goal.getGoal());
		for (var flag : CONTROL_FLAGS) {
			mob.goalSelector.enableControlFlag(flag);
			mob.targetSelector.enableControlFlag(flag);
		}
	}
	@SuppressWarnings("unchecked")
	private static Brain<Mob> brain(Mob mob) {
		return (Brain<Mob>) mob.getBrain();
	}
	/** 按实体 id 排序后轮换取附近下一只同类型生物；没有匹配时返回 {@code null}。 */
	public static @Nullable LivingEntity next(Level level, Vec3 center, double radius, EntityType<?> type, Map<EntityType<?>, Integer> index) {
		var list = level.getEntities((Entity) null, box(center, radius), entity -> entity.getType() == type && bindable(entity))
			.stream()
			.sorted(Comparator.comparingInt(Entity::getId))
			.toList();
		if (list.isEmpty()) return null;
		var at = Math.floorMod(index.getOrDefault(type, 0), list.size());
		index.put(type, (at + 1) % list.size());
		return (LivingEntity) list.get(at);
	}
	/** 半径内满足筛选的存活生物。 */
	public static List<LivingEntity> around(Level level, Vec3 center, double radius, Predicate<LivingEntity> filter) {
		return level.getEntities((Entity) null, box(center, radius),
				entity -> entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved() && filter.test(living))
			.stream()
			.map(entity -> (LivingEntity) entity)
			.toList();
	}
	private static AABB box(Vec3 center, double radius) {
		return new AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
	}
	private static final class Takeover {
		private final Mob mob;
		private final List<WrappedGoal> goals, targets;
		private final Brain<?> parked;
		private long until;
		private Takeover(Mob mob, List<WrappedGoal> goals, List<WrappedGoal> targets, Brain<?> parked) {
			this.mob = mob;
			this.goals = goals;
			this.targets = targets;
			this.parked = parked;
		}
	}
}
