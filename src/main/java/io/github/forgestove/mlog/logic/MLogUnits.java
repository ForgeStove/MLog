package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.core.util.MLogUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;
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
	/** {@code boost} 施加的移速修饰符。 */
	public static final ResourceLocation BOOST = MLogUtil.getMLogRes("unit_boost");
	/** 被接管的生物：原目标表与失效时刻。 */
	private static final Map<UUID, Takeover> TAKEOVERS = new HashMap<>();
	/** 单位身上的数值 flag，运行期有效。 */
	private static final Map<UUID, Double> FLAGS = new HashMap<>();
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
				new ArrayList<>(mob.targetSelector.getAvailableGoals())
			);
			mob.goalSelector.removeAllGoals(goal -> true);
			mob.targetSelector.removeAllGoals(goal -> true);
			for (var flag : CONTROL_FLAGS) {
				mob.goalSelector.disableControlFlag(flag);
				mob.targetSelector.disableControlFlag(flag);
			}
			mob.getNavigation().stop();
			mob.setTarget(null);
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
	/** 编译替换或方块实体移除时释放绑定。 */
	public static void releaseUnit(@Nullable LExecutor exec) {
		if (exec != null && exec.unit != null) release(exec.unit.obj());
	}
	private static void restore(Takeover takeover) {
		var mob = takeover.mob;
		if (mob.isRemoved() || !mob.isAlive()) return;
		for (var goal : takeover.goals) mob.goalSelector.addGoal(goal.getPriority(), goal.getGoal());
		for (var goal : takeover.targets) mob.targetSelector.addGoal(goal.getPriority(), goal.getGoal());
		for (var flag : CONTROL_FLAGS) {
			mob.goalSelector.enableControlFlag(flag);
			mob.targetSelector.enableControlFlag(flag);
		}
		var speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) speed.removeModifier(BOOST);
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
		private long until;
		private Takeover(Mob mob, List<WrappedGoal> goals, List<WrappedGoal> targets) {
			this.mob = mob;
			this.goals = goals;
			this.targets = targets;
		}
	}
}
