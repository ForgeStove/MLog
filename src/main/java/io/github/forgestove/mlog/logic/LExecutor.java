package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.MLog;
import net.minecraft.core.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.items.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/** 不含队伍等概念；单位指世界中可绑定的生物实体。 */
public class LExecutor {
	public static final int MAX_INSTRUCTIONS = 1000;
	public static final int MAX_TEXT_BUFFER = 400;
	/** 超出部分丢弃。 */
	public static final int MAX_GRAPHICS_BUFFER = 256;
	/** 面操作数取 0~5，其余按未指定处理。 */
	private static final int FACES = 6;
	/** 每 tick 由方块实体取走并清空。 */
	public final StringBuilder textBuffer = new StringBuilder();
	/** 单条 {@code effect} 的粒子数量上限。 */
	private static final int MAX_PARTICLES = 64;
	/** 单次爆炸的半径上限。 */
	private static final double MAX_EXPLOSION_RADIUS = 100;
	/** 由 {@code drawflush} 取走并清空。 */
	public final List<DrawCmd> graphicsBuffer = new ArrayList<>();
	private final Map<String, LVar> varIndex = new HashMap<>();
	public LInstruction[] instructions = {};
	/** 排除数字常量与内置变量。 */
	public LVar[] vars = {};
	public LVar counter, thisv, ipt, unit, queries, linksVar;
	/** 每种实体类型的轮换下标，供 {@code ubind} 依次切换目标。 */
	public final Map<EntityType<?>, Integer> bindIndex = new HashMap<>();
	/** 由 {@code @ipt} 的初值定下，{@code setrate} 的取值以此为界。 */
	public int iptLimit;
	/** {@code getlink} 按序号取用。 */
	public LogicLink[] links = {};
	public boolean yield;
	/** 特权方块据此拒绝非特权的读写。 */
	public boolean privileged;
	public @Nullable Level level;
	/** 用来把链接的相对坐标还原成绝对坐标。 */
	public @Nullable BlockPos selfPos;
	/** 对象值表示不指定面，数值越界按六面回绕。 */
	private static @Nullable Direction direction(LVar facing) {
		if (facing.isobj) return null;
		return Direction.from3DDataValue(Math.floorMod((int) facing.numval, FACES));
	}
	private static String weatherName(@Nullable Object value) {
		return value instanceof String name ? name.startsWith("@") ? name.substring(1) : name : "";
	}
	/** {@code packcolor}、{@code unpackcolor}、{@code draw col} 与 {@code %rrggbb} 字面量共用这一套编码。 */
	static double packColor(double red, double green, double blue, double alpha) {
		var argb = ARGB32.color(channel(alpha), channel(red), channel(green), channel(blue));
		return Double.longBitsToDouble(Integer.toUnsignedLong(argb));
	}
	static int unpackColor(double packed) {
		return (int) Double.doubleToRawLongBits(packed);
	}
	private static int channel(double value) {
		return (int) Math.clamp(value * 255, 0, 255);
	}
	public boolean initialized() {
		return instructions.length > 0;
	}
	/** {@code @counter} 越界时归零，因此代码会自然循环。 */
	public void runOnce() {
		if (unit != null && unit.objval instanceof Entity entity && (entity.isRemoved() || !entity.isAlive())) {
			MLogUnits.release(entity);
			unit.setobj(null);
		}
		if (counter.numval >= instructions.length || counter.numval < 0) counter.numval = 0;
		if (counter.numval < instructions.length) {
			counter.isobj = false;
			instructions[(int) counter.numval++].run(this);
		}
	}
	public void load(LAssembler builder) {
		textBuffer.setLength(0);
		graphicsBuffer.clear();
		var list = new ArrayList<LVar>();
		// 链接变量为常量，但名字不以 _ / @ 开头，需保留以供界面显示
		for (var v : builder.vars.values()) if (!v.constant || v.name.charAt(0) != '_' && v.name.charAt(0) != '@') list.add(v);
		vars = list.toArray(LVar[]::new);
		varIndex.clear();
		for (var var : vars) varIndex.put(var.name, var);
		instructions = builder.instructions;
		counter = builder.getVar("@counter");
		thisv = builder.getVar("@this");
		ipt = builder.getVar("@ipt");
		queries = builder.getVar("@queries");
		linksVar = builder.getVar("@links");
		unit = builder.getVar("@unit");
		iptLimit = builder.iptLimit;
		links = builder.links;
		privileged = builder.privileged;
	}
	/** 链接集合变化后就地重绑，不必重新编译。 */
	public void updateLinks(List<LogicLink> links) {
		this.links = links.toArray(LogicLink[]::new);
		linksVar.setnum(this.links.length);
		// 链接数无上限，先按名字建索引，以免逐个变量重扫链接表
		var byName = new HashMap<String, LogicLink>();
		for (var link : links) byName.put(link.name(), link);
		for (var var : vars) {
			// 链接变量为常量，重绑须用 setlink
			var linked = byName.get(var.name);
			if (linked != null) var.setlink(linked);
			else if (var.objval instanceof LogicLink) var.setlink(null);
		}
	}
	public @Nullable MLogSenseable resolve(@Nullable Object target) {
		return resolve(target, null);
	}
	/** {@code side} 为 {@code null} 表示不带面。 */
	public @Nullable MLogSenseable resolve(@Nullable Object target, @Nullable Direction side) {
		if (target instanceof MLogSenseable senseable) return senseable;
		if (target instanceof Entity entity) return MLogSenseables.of(entity);
		if (target instanceof BlockPos pos && level != null) return MLogSenseables.at(level, pos, side);
		if (target instanceof LogicLink link && level != null && selfPos != null)
			// 失效链接按读不到处理，与目标未加载同等对待
			return link.valid() ? MLogSenseables.at(level, link.absolute(selfPos), side) : null;
		return null;
	}
	/** 供 {@code read} / {@code write} 查另一处理器的变量。 */
	public @Nullable LVar optionalVar(String name) {
		return varIndex.get(name);
	}
	public String drainText() {
		var text = textBuffer.toString();
		textBuffer.setLength(0);
		return text;
	}
	public interface LInstruction {
		void run(LExecutor exec);
	}
	public record SetI(LVar from, LVar to) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (!to.constant) to.set(from);
		}
	}
	public record OpI(LogicOp op, LVar a, LVar b, LVar dest) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			// 严格相等需比较类型，OpLambda2 的 double 签名无法表达，故在此特判
			if (op == LogicOp.strictEqual)
				dest.setnum(a.isobj == b.isobj && (a.isobj ? Objects.equals(a.objval, b.objval) : a.numval == b.numval) ? 1 : 0);
				// 一元运算非空的是 function1，其余情况是 function2
			else if (op.unary) dest.setnum(Objects.requireNonNull(op.function1).get(a.num()));
			else if (op.objFunction2 != null && a.isobj && b.isobj) dest.setnum(op.objFunction2.get(a.obj(), b.obj()));
			else dest.setnum(Objects.requireNonNull(op.function2).get(a.num(), b.num()));
		}
	}
	public record SenseI(LVar from, LVar to, LVar type, LVar slot, LVar face) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			// 属性名可能是内置的 LAccess，也可能是任意的方块状态属性名
			var key = type.obj();
			var access = key instanceof LAccess builtin ? builtin.name() : key instanceof String name ? name : null;
			if (access == null) {
				to.setobj(null);
				return;
			}
			// 列表（query 写进 @queries 的结果）只量得出长度
			if (from.obj() instanceof List<?> list) {
				if (key == LAccess.size) to.setnum(list.size());
				else to.setobj(null);
				return;
			}
			// 面操作数交给适配器，分面的读数（Create 过滤槽）按它取
			var senseable = exec.resolve(from.obj(), direction(face));
			if (senseable == null) {
				to.setobj(null);
				return;
			}
			// 按序号取容器内容，不进适配器，在此单独处理
			if (key instanceof LAccess known && LAccess.usesSlot(known)) {
				var index = (int) slot.num();
				to.setobj(known == LAccess.slotItem ? senseable.itemAt(index) : senseable.fluidAt(index));
				return;
			}
			var objOut = senseable.senseObject(access);
			// 没有对象输出时退回数值
			if (objOut == MLogSenseable.NO_SENSED) to.setnum(senseable.sense(access));
			else to.setobj(objOut);
		}
	}
	public record SelectI(ConditionOp op, LVar result, LVar comp0, LVar comp1, LVar yes, LVar no) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (result.constant) return;
			result.set(op.test(comp0, comp1) ? yes : no);
		}
	}
	/** 变量只有 double 一种载体，故把 32 位颜色按位存入 double 的低 32 位。 */
	public record PackColorI(LVar result, LVar r, LVar g, LVar b, LVar a) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			result.setnum(packColor(r.num(), g.num(), b.num(), a.num()));
		}
	}
	public record UnpackColorI(LVar r, LVar g, LVar b, LVar a, LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var argb = unpackColor(value.num());
			r.setnum(ARGB32.red(argb) / 255.0);
			g.setnum(ARGB32.green(argb) / 255.0);
			b.setnum(ARGB32.blue(argb) / 255.0);
			a.setnum(ARGB32.alpha(argb) / 255.0);
		}
	}
	/** {@code @counter} 越界后下一 tick 自然归零。 */
	public record EndI() implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			exec.counter.numval = exec.instructions.length;
		}
	}
	/** 未到时间则把 {@code @counter} 拉回自身并让出本 tick；每 tick 累计 1/20 秒，满 {@link #value} 即清空计时。 */
	public static class WaitI implements LInstruction {
		private final LVar value;
		private final int address;
		/** 等待期间处理器停在此条指令上，计时随指令各持一份。 */
		private float waited;
		public WaitI(LVar value, int address) {
			this.value = value;
			this.address = address;
		}
		@Override
		public void run(LExecutor exec) {
			var seconds = value.num();
			if (seconds <= 0) {
				// 等待 0 秒也须让出本 tick，避免停在此条上空转
				waited = 0F;
				exec.yield = true;
				return;
			}
			if (waited >= seconds) {
				waited = 0F;
				return;
			}
			exec.counter.numval = address;
			exec.yield = true;
			waited += 1F / 20F;
		}
	}
	/** 停在此处不再继续，也不放行。 */
	public record StopI(int address) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			exec.counter.numval = address;
		}
	}
	/** 超出方块的速率时按速率封顶。 */
	public record SetRateI(LVar amount) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			exec.ipt.numval = Math.clamp((int) amount.num(), 1, exec.iptLimit);
		}
	}
	/** 越界返回 {@code null}。 */
	public record GetLinkI(LVar output, LVar index) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var address = (int) index.num();
			output.setobj(address >= 0 && address < exec.links.length ? exec.links[address] : null);
		}
	}
	/** 占位语句：不执行任何操作，但占住一个指令槽，使行号与指令序号保持一致。 */
	public record NoopI() implements LInstruction {
		@Override
		public void run(LExecutor exec) {}
	}
	/** {@code query <形状> <类型> <x> <y> <z> …}：把区域里的单位或建筑查进 {@code @queries}。 */
	public record QueryI(QueryShape shape, QueryType type, LVar x, LVar y, LVar z, LVar w, LVar h, LVar d) implements LInstruction {
		/** 变量没了、或里面不是列表时返回 {@code null}。 */
		@SuppressWarnings("unchecked")
		private static @Nullable List<Object> results(@Nullable LVar queries) {
			// 类型擦除：运行期只能识别为 List，元素始终为 Object
			return queries != null && queries.objval instanceof List<?> list ? (List<Object>) list : null;
		}
		@Override
		public void run(LExecutor exec) {
			var level = exec.level;
			var results = results(exec.queries);
			if (level == null || results == null) return;
			// 结果为每 tick 重新生成的活引用，上一轮对象已失效
			results.clear();
			var box = box();
			if (type == QueryType.unit) {
				// 单位仅指生物与玩家
				results.addAll(level.getEntities(
					(Entity) null,
					box,
					entity -> entity instanceof LivingEntity && inside(box, entity.getX(), entity.getY(), entity.getZ())
				));
				return;
			}
			// 建筑仅遍历已加载区块的方块实体表，不为查询加载区块
			var minX = Mth.floor(box.minX) >> 4;
			var minZ = Mth.floor(box.minZ) >> 4;
			var maxX = Mth.floor(box.maxX) >> 4;
			var maxZ = Mth.floor(box.maxZ) >> 4;
			for (var cx = minX; cx <= maxX; cx++)
				for (var cz = minZ; cz <= maxZ; cz++) {
					var chunk = level.getChunkSource().getChunkNow(cx, cz);
					if (chunk == null) continue;
					for (var pos : chunk.getBlockEntities().keySet()) {
						if (!inside(box, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) continue;
						// 存坐标而非适配器：坐标不因方块实体卸载而失效，读取时由 resolve 现取
						results.add(pos);
					}
				}
		}
		/** 圆是中心 ± 半径，长方体是最小角 + 三边；负边长由 {@code AABB} 自行归一。 */
		private AABB box() {
			var px = x.num();
			var py = y.num();
			var pz = z.num();
			if (shape == QueryShape.rect) return new AABB(px, py, pz, px + w.num(), py + h.num(), pz + d.num());
			var radius = Math.abs(w.num());
			return new AABB(px - radius, py - radius, pz - radius, px + radius, py + radius, pz + radius);
		}
		/** 包围盒只是预筛；圆还须再按到球心的距离判一次。 */
		private boolean inside(AABB box, double px, double py, double pz) {
			if (!box.contains(px, py, pz)) return false;
			if (shape == QueryShape.rect) return true;
			var dx = px - x.num();
			var dy = py - y.num();
			var dz = pz - z.num();
			var radius = Math.abs(w.num());
			return dx * dx + dy * dy + dz * dz <= radius * radius;
		}
	}
	/** {@code getblock <结果> <x> <y> <z>}：读出该处方块的注册名，未加载时为 {@code null}。 */
	public record GetBlockI(LVar output, LVar x, LVar y, LVar z) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var level = exec.level;
			if (level == null) {
				output.setobj(null);
				return;
			}
			var pos = BlockPos.containing(x.num(), y.num(), z.num());
			output.setobj(level.isLoaded(pos) ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString() : null);
		}
	}
	/** {@code setblock <x> <y> <z> <方块>}：设置该处的方块，认不出方块名或未加载时不动。 */
	public record SetBlockI(LVar x, LVar y, LVar z, LVar block) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var level = exec.level;
			if (level == null || !(block.obj() instanceof String name)) return;
			var id = ResourceLocation.tryParse(name);
			if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return;
			var pos = BlockPos.containing(x.num(), y.num(), z.num());
			if (!level.isLoaded(pos)) return;
			level.setBlock(pos, BuiltInRegistries.BLOCK.get(id).defaultBlockState(), Block.UPDATE_ALL);
		}
	}
	/** {@code getflag <结果> <标记>}：标记存在时为 1。 */
	public record GetFlagI(LVar output, LVar flag) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var flags = MLogFlags.of(exec.level);
			output.setnum(flags != null && flag.obj() instanceof String name && flags.has(name) ? 1 : 0);
		}
	}
	/** {@code setflag <标记> <值>}：值为 0 或空对象时清除。 */
	public record SetFlagI(LVar flag, LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var flags = MLogFlags.of(exec.level);
			if (flags != null && flag.obj() instanceof String name) flags.set(name, value.num() != 0);
		}
	}
	/** {@code message <位置>}：把打印缓存的内容发给所有玩家。 */
	public record MessageI(MessageType type) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var text = exec.drainText();
			if (text.isEmpty() || !(exec.level instanceof ServerLevel level)) return;
			var message = Component.literal(text);
			for (var player : level.getServer().getPlayerList().getPlayers())
				if (type == MessageType.actionbar) player.displayClientMessage(message, true);
				else player.sendSystemMessage(message);
		}
	}
	/** {@code playsound <声音> <音量> <音高> <x> <y> <z>}：在指定位置播放注册表中的声音。 */
	public record PlaySoundI(LVar sound, LVar volume, LVar pitch, LVar x, LVar y, LVar z) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var level = exec.level;
			if (level == null || !(sound.obj() instanceof String name)) return;
			var id = ResourceLocation.tryParse(name);
			if (id == null) return;
			var event = BuiltInRegistries.SOUND_EVENT.get(id);
			if (event == null) return;
			level.playSound(
				null,
				x.num(),
				y.num(),
				z.num(),
				event,
				SoundSource.MASTER,
				(float) Math.clamp(volume.num(), 0, 2),
				(float) pitch.num()
			);
		}
	}
	/** {@code effect <粒子> <x> <y> <z> <数量>}：生成粒子，单次数量有上限。 */
	public record EffectI(LVar particle, LVar x, LVar y, LVar z, LVar count, LVar dx, LVar dy, LVar dz, LVar speed) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (!(exec.level instanceof ServerLevel level) || !(particle.obj() instanceof String name)) return;
			var id = ResourceLocation.tryParse(name);
			if (id == null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(id)) return;
			if (!(BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof SimpleParticleType type)) return;
			var amount = Math.clamp((int) count.num(), 0, MAX_PARTICLES);
			// 数量为 0 时按原版语义发射单颗粒子，速度由方向与 speed 相乘得到
			level.sendParticles(type, x.num(), y.num(), z.num(), amount, dx.num(), dy.num(), dz.num(), speed.num());
		}
	}
	/** {@code spawn <类型> <x> <y> <z> <朝向> <结果>}：按类型生成实体，结果写入变量。 */
	public record SpawnI(LVar type, LVar x, LVar y, LVar z, LVar rotation, LVar result) implements LInstruction {
		private static @Nullable EntityType<?> typeOf(@Nullable Object value) {
			if (value instanceof EntityType<?> type) return type;
			if (!(value instanceof String name)) return null;
			var id = ResourceLocation.tryParse(name);
			return id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
		}
		@Override
		public void run(LExecutor exec) {
			var entityType = typeOf(type.obj());
			if (entityType == null || !(exec.level instanceof ServerLevel level)) {
				result.setobj(null);
				return;
			}
			var entity = entityType.create(
				level,
				created -> created.moveTo(x.num(), y.num(), z.num(), (float) rotation.num(), 0),
				BlockPos.containing(x.num(), y.num(), z.num()),
				MobSpawnType.COMMAND,
				false,
				false
			);
			if (entity == null) {
				result.setobj(null);
				return;
			}
			if (!level.tryAddFreshEntityWithPassengers(entity)) {
				result.setobj(null);
				return;
			}
			result.setobj(entity);
		}
	}
	/** {@code status <apply|clear> <效果> <目标> <秒数>}：施加或清除状态效果。 */
	public record StatusI(boolean clear, LVar effect, LVar target, LVar duration) implements LInstruction {
		private static @Nullable Holder<MobEffect> effectOf(@Nullable Object value) {
			if (!(value instanceof String name)) return null;
			var id = ResourceLocation.tryParse(name);
			return id == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
		}
		@Override
		public void run(LExecutor exec) {
			var holder = effectOf(effect.obj());
			if (holder == null || !(target.obj() instanceof LivingEntity living)) return;
			if (clear) {
				living.removeEffect(holder);
				return;
			}
			var ticks = (int) Math.round(duration.num() * 20);
			if (ticks <= 0) return;
			living.addEffect(new MobEffectInstance(holder, ticks));
		}
	}
	/** {@code weathersense <结果> <天气>}：天气成立时写 1。 */
	public record WeatherSenseI(LVar result, LVar weather) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var level = exec.level;
			var name = weatherName(weather.obj());
			var on = level != null && switch (name) {
				case "clear" -> !level.isRaining() && !level.isThundering();
				case "rain" -> level.isRaining() && !level.isThundering();
				case "thunder" -> level.isThundering();
				default -> false;
			};
			result.setnum(on ? 1 : 0);
		}
	}
	/** {@code weatherset <天气> <状态>}：开关天气。 */
	public record WeatherSetI(LVar weather, LVar state) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (!(exec.level instanceof ServerLevel level)) return;
			var on = state.num() != 0;
			var raining = level.isRaining();
			var thundering = level.isThundering();
			switch (weatherName(weather.obj())) {
				case "clear" -> {
					if (!on) return;
					raining = false;
					thundering = false;
				}
				case "rain" -> {
					raining = on;
					if (!on) thundering = false;
				}
				case "thunder" -> {
					thundering = on;
					if (on) raining = true;
				}
				default -> {
					return;
				}
			}
			level.setWeatherParameters(6000, raining ? 6000 : 0, raining, thundering);
		}
	}
	/** {@code explosion <x> <y> <z> <半径> <破坏>}：在指定位置引爆。 */
	public record ExplosionI(LVar x, LVar y, LVar z, LVar radius, LVar destroy) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (!(exec.level instanceof ServerLevel level)) return;
			var interaction = destroy.num() != 0 ? ExplosionInteraction.TNT : ExplosionInteraction.NONE;
			level.explode(null, x.num(), y.num(), z.num(), (float) Mth.clamp(radius.num(), 0, MAX_EXPLOSION_RADIUS), interaction);
		}
	}
	/** {@code localeprint <键>}：把本地化文本写入打印缓存。 */
	public record LocalePrintI(LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (!(value.obj() instanceof String key) || exec.textBuffer.length() >= MAX_TEXT_BUFFER) return;
			var text = Component.translatable(key).getString();
			exec.textBuffer.append(text, 0, Math.min(text.length(), MAX_TEXT_BUFFER - exec.textBuffer.length()));
		}
	}
	/** {@code ubind <类型>}：绑定附近该类型最近的非敌对生物。 */
	public record UnitBindI(LVar type) implements LInstruction {
		private static @Nullable EntityType<?> resolve(@Nullable Object value) {
			if (value instanceof EntityType<?> type) return type;
			if (!(value instanceof String name)) return null;
			var id = ResourceLocation.tryParse(name);
			return id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
		}
		@Override
		public void run(LExecutor exec) {
			var level = exec.level;
			if (exec.unit == null || level == null) return;
			var value = type.obj();
			if (value instanceof Entity entity) {
				MLogUnits.bind(exec, MLogUnits.bindable(entity, exec.privileged) ? (LivingEntity) entity : null);
				return;
			}
			var wanted = resolve(value);
			MLogUnits.bind(exec, wanted == null || exec.selfPos == null
				? null : MLogUnits.next(level, Vec3.atCenterOf(exec.selfPos), MLogUnits.RANGE, wanted, exec.bindIndex, exec.privileged)
			);
		}
	}
	/** {@code ucontrol <动作> …}：用原版寻路控制绑定的生物。 */
	public static class UnitControlI implements LInstruction {
		/** 寻路速度倍数。 */
		private static final double SPEED = 1;
		/** 近身攻击的间隔（游戏刻）。 */
		private static final int ATTACK_INTERVAL = 20;
		/** 小体型单位很难贴到碰撞箱相交，近战额外给一点触及距离。 */
		private static final double MELEE_REACH = 2;
		/** 空手也能远程攻击的生物，由 data pack 扩充。 */
		private static final TagKey<EntityType<?>> RANGED_WITHOUT_WEAPON =
			TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(MLog.ID, "ranged_without_weapon"));
		/** 远程攻击的距离上限；原版各生物的射程不同，取覆盖常见远程单位的半径。 */
		private static final double RANGED_RANGE = 16;
		/** 与容器交互的距离上限。 */
		private static final double TRANSFER_RANGE = 5;
		/** 读取方块的距离上限。 */
		private static final double READ_RANGE = 5;
		private final UnitControlType type;
		private final LVar p1, p2, p3, p4, p5;
		private boolean hasPath;
		public UnitControlI(UnitControlType type, LVar p1, LVar p2, LVar p3, LVar p4, LVar p5) {
			this.type = type;
			this.p1 = p1;
			this.p2 = p2;
			this.p3 = p3;
			this.p4 = p4;
			this.p5 = p5;
		}
		/** 弓 20 刻；弩用原版蓄力时长（受魔咒影响）。 */
		private static int drawTicks(Mob mob) {
			var stack = mob.getMainHandItem();
			return stack.getItem() instanceof CrossbowItem ? CrossbowItem.getChargeDuration(stack, mob) : 20;
		}
		/** 直线移动，不绕障碍。 */
		private void moveDirect(LivingEntity living, double x, double y, double z) {
			if (!(living instanceof Mob mob)) return;
			if (hasPath) {
				mob.getNavigation().stop();
				hasPath = false;
			}
			mob.getMoveControl().setWantedPosition(x, y, z, SPEED);
			mob.getLookControl().setLookAt(x, y, z);
		}
		/**
		 * 原版三维寻路，飞行与地面单位各用自身的 {@link net.minecraft.world.entity.ai.navigation.PathNavigation}。
		 * 目标未变且路径未完成时 {@code moveTo} 会复用现有路径，故每次执行都下发即可。
		 */
		private void navigate(LivingEntity living, double x, double y, double z) {
			if (!(living instanceof Mob mob)) return;
			hasPath = true;
			mob.getNavigation().moveTo(x, y, z, SPEED);
		}
		/** 把单位携带的第一种物品送入容器，目标为空气时掉在脚下。 */
		private static void itemDrop(LExecutor exec, LivingEntity living, LVar to, LVar amountVar) {
			if (!(living instanceof Mob mob) || !(living instanceof InventoryCarrier carrier)) return;
			var remaining = Math.max(0, (int) amountVar.num());
			if (remaining == 0) return;
			var container = carrier.getInventory();
			var hand = mob.getItemBySlot(EquipmentSlot.MAINHAND);
			var hadHand = !hand.isEmpty();
			var sources = new ArrayList<ItemStack>();
			if (!hand.isEmpty()) sources.add(hand);
			for (var i = 0; i < container.getContainerSize(); i++) {
				var stack = container.getItem(i);
				if (!stack.isEmpty()) sources.add(stack);
			}
			var target = to.obj();
			var moved = false;
			if (isAir(target)) {
				for (var stack : sources) {
					if (remaining <= 0) break;
					var taken = stack.split(Math.min(remaining, stack.getCount()));
					remaining -= taken.getCount();
					living.spawnAtLocation(taken);
					moved = true;
				}
				sync(mob, container, hand, hadHand);
				if (moved) living.swing(InteractionHand.MAIN_HAND);
				return;
			}
			var handler = handler(exec, living, target);
			if (handler == null) return;
			for (var stack : sources) {
				if (remaining <= 0) break;
				var take = Math.min(remaining, stack.getCount());
				// 先模拟，只按目标实际接收的数量扣减；拒收的叠跳到下一叠
				var remainder = ItemHandlerHelper.insertItemStacked(handler, stack.copyWithCount(take), true);
				var accepted = take - remainder.getCount();
				if (accepted <= 0) continue;
				var left = ItemHandlerHelper.insertItemStacked(handler, stack.split(accepted), false);
				remaining -= accepted;
				moved = true;
				if (!left.isEmpty()) living.spawnAtLocation(left);
			}
			sync(mob, container, hand, hadHand);
			if (moved) {
				living.swing(InteractionHand.MAIN_HAND);
				var pos = position(exec, target);
				if (pos != null && exec.level != null) MLogUnits.pulseContainer(exec.level, pos);
			}
		}
		/** 主手物品被就地拆分，须回写并同步装备。 */
		private static void sync(Mob mob, SimpleContainer container, ItemStack hand, boolean hadHand) {
			if (hadHand) mob.setItemSlot(EquipmentSlot.MAINHAND, hand.isEmpty() ? ItemStack.EMPTY : hand);
			container.setChanged();
		}
		/** 从容器中取出物品放入单位物品栏。 */
		private static void itemTake(LExecutor exec, LivingEntity living, LVar from, LVar itemVar, LVar amountVar) {
			if (!(living instanceof InventoryCarrier carrier)) return;
			var handler = handler(exec, living, from.obj());
			var item = item(itemVar.obj());
			if (handler == null || item == null) return;
			var container = carrier.getInventory();
			var remaining = Math.max(0, (int) amountVar.num());
			var sample = ItemStack.EMPTY;
			for (var i = 0; i < handler.getSlots() && remaining > 0; i++) {
				var stack = handler.getStackInSlot(i);
				if (!stack.is(item)) continue;
				var extracted = handler.extractItem(i, Math.min(remaining, stack.getCount()), true);
				if (extracted.isEmpty()) continue;
				var leftover = container.addItem(extracted.copy());
				var inserted = extracted.getCount() - leftover.getCount();
				if (inserted <= 0) continue;
				handler.extractItem(i, inserted, false);
				remaining -= inserted;
				if (sample.isEmpty()) sample = extracted;
			}
			if (!sample.isEmpty()) {
				showInHand(living, sample);
				pickUpEffect(living);
				var pos = position(exec, from.obj());
				if (pos != null && exec.level != null) MLogUnits.pulseContainer(exec.level, pos);
			}
		}
		/** 与玩家拾取物品相同的音量与音高。 */
		private static void pickUpEffect(LivingEntity living) {
			var level = living.level();
			var random = level.random;
			level.playSound(
				null,
				living.getX(),
				living.getY(),
				living.getZ(),
				SoundEvents.ITEM_PICKUP,
				SoundSource.NEUTRAL,
				0.2F,
				(random.nextFloat() - random.nextFloat()) * 0.7F + 1.0F
			);
			living.swing(InteractionHand.MAIN_HAND);
		}
		/** 主手空着时从物品栏移出一件同组件物品显示。 */
		private static void showInHand(LivingEntity living, ItemStack sample) {
			if (!(living instanceof Mob mob) || !(living instanceof InventoryCarrier carrier)) return;
			if (!mob.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return;
			var container = carrier.getInventory();
			for (var i = 0; i < container.getContainerSize(); i++) {
				var stack = container.getItem(i);
				if (!ItemStack.isSameItemSameComponents(stack, sample)) continue;
				var hand = stack.split(1);
				container.setChanged();
				mob.setItemSlot(EquipmentSlot.MAINHAND, hand);
				return;
			}
		}
		private static @Nullable IItemHandler handler(LExecutor exec, LivingEntity living, @Nullable Object target) {
			var pos = position(exec, target);
			if (pos != null && living.distanceToSqr(Vec3.atCenterOf(pos)) > TRANSFER_RANGE * TRANSFER_RANGE) return null;
			var senseable = exec.resolve(target);
			return senseable == null ? null : senseable.itemHandler();
		}
		private static @Nullable BlockPos position(LExecutor exec, @Nullable Object target) {
			if (target instanceof BlockPos pos) return pos;
			if (target instanceof BlockEntity be) return be.getBlockPos();
			if (target instanceof LogicLink link && link.valid() && exec.selfPos != null) return link.absolute(exec.selfPos);
			return null;
		}
		private static @Nullable Item item(@Nullable Object value) {
			if (value instanceof Item item) return item;
			if (value instanceof String name) {
				var id = ResourceLocation.tryParse(name);
				return id == null ? null : BuiltInRegistries.ITEM.get(id);
			}
			return null;
		}
		private static boolean isAir(@Nullable Object value) {
			return value == Items.AIR || "air".equals(value) || "minecraft:air".equals(value);
		}
		private void stop(LivingEntity living) {
			if (living instanceof CrossbowAttackMob crossbow) crossbow.setChargingCrossbow(false);
			if (living.isUsingItem()) living.stopUsingItem();
			if (living instanceof Mob mob) {
				mob.getNavigation().stop();
				mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), SPEED);
				mob.setTarget(null);
				mob.setAggressive(false);
			}
			var motion = living.getDeltaMovement();
			living.setDeltaMovement(0, motion.y, 0);
		}
		/** 远程能力不等于手上有武器：持投射武器才算；空手时只有标签里的自带投射物生物。 */
		private static boolean canUseRanged(Mob mob) {
			if (!(mob instanceof RangedAttackMob)) return false;
			var item = mob.getMainHandItem().getItem();
			if (item instanceof ProjectileWeaponItem || item instanceof TridentItem) return true;
			return mob.getMainHandItem().isEmpty()
				&& BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(mob.getType()).is(RANGED_WITHOUT_WEAPON);
		}
		/** 弓/弩的蓄力姿势由“正在使用物品”驱动，和挥手动画无关。 */
		private static boolean holdsDrawable(Mob mob) {
			var item = mob.getMainHandItem().getItem();
			return item instanceof BowItem || item instanceof CrossbowItem;
		}
		@Override
		public void run(LExecutor exec) {
			var living = MLogUnits.bound(exec);
			if (living == null) return;
			// 与 MDT 一致：接管发生在 ucontrol 时，并按时限续期
			if (type != UnitControlType.unbind && type != UnitControlType.within) MLogUnits.control(living, exec.selfPos);
			switch (type) {
				case idle, stop -> {
					hasPath = false;
					stop(living);
				}
				case move -> moveDirect(living, p1.num(), p2.num(), p3.num());
				case pathfind -> navigate(living, p1.num(), p2.num(), p3.num());
				case approach -> {
					var radius = Math.abs(p4.num());
					if (living.distanceToSqr(p1.num(), p2.num(), p3.num()) <= radius * radius) {
						hasPath = false;
						stop(living);
					} else navigate(living, p1.num(), p2.num(), p3.num());
				}
				case target -> {
					if (!(living instanceof Mob mob)) return;
					mob.setTarget(null);
					var x = p1.num();
					var y = p2.num();
					var z = p3.num();
					mob.getLookControl().setLookAt(x, y, z);
					var draws = holdsDrawable(mob);
					if (p4.num() == 0 || !(mob instanceof RangedAttackMob ranged) || !canUseRanged(mob)) {
						stopDrawing(mob);
						mob.setAggressive(false);
						return;
					}
					mob.setAggressive(true);
					var now = mob.level().getGameTime();
					var ready = MLogUnits.canAttack(living, now, rangedInterval(mob));
					if (!draws) {
						if (ready) {
							MLogUnits.markAttack(living, now);
							fireAt(mob, ranged, x, y, z);
						}
						return;
					}
					if (!mob.isUsingItem()) {
						if (!ready) return;
						mob.startUsingItem(InteractionHand.MAIN_HAND);
						if (mob instanceof CrossbowAttackMob crossbow) crossbow.setChargingCrossbow(true);
						return;
					}
					if (!ready || mob.getTicksUsingItem() < drawTicks(mob)) return;
					if (mob instanceof CrossbowAttackMob crossbow) crossbow.setChargingCrossbow(false);
					// 弩必须走 releaseUsingItem 才会装填弹药
					mob.releaseUsingItem();
					MLogUnits.markAttack(living, now);
					fireAt(mob, ranged, x, y, z);
				}
				case targetp -> {
					if (!(living instanceof Mob mob)) return;
					var target = p1.obj();
					if (target instanceof LivingEntity entity) {
						if (entity == living || !entity.isAlive() || entity.isRemoved()) {
							stopDrawing(mob);
							mob.setAggressive(false);
							return;
						}
						mob.getLookControl().setLookAt(entity.getX(), entity.getEyeY(), entity.getZ());
						if (p2.num() != 0) {
							mob.setTarget(entity);
							// 骷髅的拉弓姿势按 isAggressive 判定，不设就没有动画
							mob.setAggressive(true);
							// 接管后原版攻击目标已被移除，攻击在这里节流执行
							var now = mob.level().getGameTime();
							var interval = mob instanceof RangedAttackMob && canUseRanged(mob) ? rangedInterval(mob) : ATTACK_INTERVAL;
							var ready = MLogUnits.canAttack(living, now, interval);
							var distance = mob.distanceToSqr(entity);
							if (mob instanceof RangedAttackMob ranged && canUseRanged(mob)) {
								var draws = holdsDrawable(mob);
								if (distance > RANGED_RANGE * RANGED_RANGE || !mob.getSensing().hasLineOfSight(entity)) {
									stopDrawing(mob);
									return;
								}
								if (!draws) {
									if (ready) {
										MLogUnits.markAttack(living, now);
										// 与 RangedAttackGoal 相同：距离越远弹速越高
										ranged.performRangedAttack(entity, (float) Mth.clamp(Math.sqrt(distance) / RANGED_RANGE, 0.1, 1));
									}
									return;
								}
								if (!mob.isUsingItem()) {
									if (!ready) return;
									mob.startUsingItem(InteractionHand.MAIN_HAND);
									if (mob instanceof CrossbowAttackMob crossbow) crossbow.setChargingCrossbow(true);
									return;
								}
								if (!ready || mob.getTicksUsingItem() < drawTicks(mob)) return;
								if (mob instanceof CrossbowAttackMob crossbow) crossbow.setChargingCrossbow(false);
								// 弩必须走 releaseUsingItem 才会装填弹药
								mob.releaseUsingItem();
								MLogUnits.markAttack(living, now);
								// 与 RangedAttackGoal 相同：距离越远弹速越高
								ranged.performRangedAttack(entity, (float) Mth.clamp(Math.sqrt(distance) / RANGED_RANGE, 0.1, 1));
							} else if (ready && mob.getAttribute(Attributes.ATTACK_DAMAGE) != null
								// 部分动物没有攻击力属性，doHurtTarget 会直接抛异常
								&& (mob.isWithinMeleeAttackRange(entity) || distance <= MELEE_REACH * MELEE_REACH)) {
								MLogUnits.markAttack(living, now);
								mob.swing(InteractionHand.MAIN_HAND);
								mob.doHurtTarget(entity);
							}
							} else {
								mob.setAggressive(false);
								stopDrawing(mob);
							}
						return;
					}
					// 方块与建筑按坐标瞄准；方块无法被攻击，shoot 只对单位生效
					var pos = position(exec, target);
					if (pos == null) return;
					mob.setTarget(null);
					mob.setAggressive(false);
					stopDrawing(mob);
					mob.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
				}
				case flag -> MLogUnits.setFlag(living, p1.num());
				case itemDrop -> itemDrop(exec, living, p1, p2);
				case itemTake -> itemTake(exec, living, p1, p2, p3);
				case getBlock -> getBlock(living, p1, p2, p3, p4, p5);
				case within -> {
					var radius = Math.abs(p4.num());
					p5.setnum(living.distanceToSqr(p1.num(), p2.num(), p3.num()) <= radius * radius ? 1 : 0);
				}
				case unbind -> {
					hasPath = false;
					stop(living);
					MLogUnits.release(living);
					exec.unit.setobj(null);
				}
			}
		}
		/** 取消蓄力姿势。 */
		private void stopDrawing(Mob mob) {
			if (mob instanceof CrossbowAttackMob crossbow) crossbow.setChargingCrossbow(false);
			if (mob.isUsingItem()) mob.stopUsingItem();
		}
		/** 用一个不加入世界的临时目标，把坐标交给原版远程攻击。 */
		private static void fireAt(Mob mob, RangedAttackMob ranged, double x, double y, double z) {
			var aim = new ArmorStand(EntityType.ARMOR_STAND, mob.level());
			aim.setPos(x, y, z);
			ranged.performRangedAttack(aim, (float) Mth.clamp(Math.sqrt(mob.distanceToSqr(x, y, z)) / RANGED_RANGE, 0.1, 1));
		}
		/** 原版没有公开远程攻击间隔，按生物类型取各自的射速；未知类型按 20 刻。 */
		private static int rangedInterval(Mob mob) {
			var type = mob.getType();
			if (type == EntityType.BLAZE || type == EntityType.GHAST || type == EntityType.WITCH) return 60;
			if (type == EntityType.DROWNED || type == EntityType.WITHER) return 40;
			return 20;
		}
		/** 读取附近的方块与方块实体，超出距离或越界时两个结果都写 null。 */
		private static void getBlock(LivingEntity living, LVar x, LVar y, LVar z, LVar type, LVar building) {
			var level = living.level();
			var pos = BlockPos.containing(x.num(), y.num(), z.num());
			if (living.distanceToSqr(x.num(), y.num(), z.num()) > READ_RANGE * READ_RANGE
				|| !level.isInWorldBounds(pos)
				|| !level.isLoaded(pos)) {
				type.setobj(null);
				building.setobj(null);
				return;
			}
			type.setobj(level.getBlockState(pos).getBlock());
			building.setobj(level.getBlockEntity(pos));
		}
	}
	/** {@code uradar …}：在绑定生物周围按筛选与排序选出一只生物。 */
	public record UnitRadarI(RadarFilter target1, RadarFilter target2, RadarFilter target3, RadarSort sort, LVar order, LVar result) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var living = MLogUnits.bound(exec);
			var level = exec.level;
			if (living == null || level == null) {
				result.setobj(null);
				return;
			}
			var list = new ArrayList<>(MLogUnits.around(level, living.position(), MLogUnits.RANGE,
				entity -> entity != living && target1.test(entity) && target2.test(entity) && target3.test(entity)));
			Comparator<LivingEntity> comparator = sort == RadarSort.health
				? Comparator.comparingDouble(LivingEntity::getHealth)
				: Comparator.comparingDouble((LivingEntity entity) -> -entity.distanceToSqr(living));
			// 与 MDT 相同：排序值为 0 时反向
			if (order.num() != 0) comparator = comparator.reversed();
			list.sort(comparator);
			result.setobj(list.isEmpty() ? null : list.getFirst());
		}
	}
	/** {@code ulocate …}：按类型寻找方块、出生点或受伤生物，结果最多每 20 tick 重算一次。 */
	public static class UnitLocateI implements LInstruction {
		private static final int INTERVAL = 20, MAX_RADIUS = 16;
		private final LocateType type;
		private final LVar target, radius, outX, outY, outZ, outFound;
		private long checkedAt = Long.MIN_VALUE;
		private @Nullable BlockPos cached;
		public UnitLocateI(LocateType type, LVar target, LVar radius, LVar outX, LVar outY, LVar outZ, LVar outFound) {
			this.type = type;
			this.target = target;
			this.radius = radius;
			this.outX = outX;
			this.outY = outY;
			this.outZ = outZ;
			this.outFound = outFound;
		}
		@Override
		public void run(LExecutor exec) {
			var living = MLogUnits.bound(exec);
			var level = exec.level;
			if (living == null || level == null) {
				outFound.setnum(0);
				return;
			}
			var now = level.getGameTime();
			if (checkedAt == Long.MIN_VALUE || now - checkedAt >= INTERVAL) {
				checkedAt = now;
				cached = locate(level, living);
			}
			if (cached == null) {
				outX.setnum(0);
				outY.setnum(0);
				outZ.setnum(0);
				outFound.setnum(0);
				return;
			}
			outX.setnum(cached.getX());
			outY.setnum(cached.getY());
			outZ.setnum(cached.getZ());
			outFound.setnum(1);
		}
		private @Nullable BlockPos locate(Level level, LivingEntity living) {
			var range = Math.clamp(Math.abs(radius.num()), 0, MAX_RADIUS);
			return switch (type) {
				case spawn -> level.getSharedSpawnPos();
				case injured -> MLogUnits.around(level, living.position(), range,
						entity -> entity != living && entity.getHealth() < entity.getMaxHealth())
					.stream()
					.min(Comparator.comparingDouble(entity -> entity.distanceToSqr(living)))
					.map(Entity::blockPosition)
					.orElse(null);
				case block -> block(level, living.blockPosition(), range);
			};
		}
		private @Nullable BlockPos block(Level level, BlockPos center, double range) {
			var value = target.obj();
			Block block = value instanceof Block b ? b : null;
			if (block == null && value instanceof String name) {
				var id = ResourceLocation.tryParse(name);
				if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) block = BuiltInRegistries.BLOCK.get(id);
			}
			if (block == null) return null;
			var offset = Mth.ceil(range);
			BlockPos best = null;
			var bestDistance = Double.MAX_VALUE;
			for (var pos : BlockPos.betweenClosed(center.offset(-offset, -offset, -offset), center.offset(offset, offset, offset))) {
				if (!level.isLoaded(pos) || !level.getBlockState(pos).is(block)) continue;
				var distance = center.distSqr(pos);
				if (distance > range * range || distance >= bestDistance) continue;
				bestDistance = distance;
				best = pos.immutable();
			}
			return best;
		}
	}
	/** {@code lookup <类型> <结果> <编号>}：按编号在注册表里查一项内容，查不到给 {@code null}。 */
	public record LookupI(LookupType type, LVar result, LVar id) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			// 常量实例在所有处理器间共享，写入等同于修改全局
			if (result.constant) return;
			var index = (int) id.num();
			result.setobj(index >= 0 && index < type.registry.size() ? type.registry.byId(index) : null);
		}
	}
	/** {@code read <结果> = <目标> at <位置>}：位置的解释方式由目标自行决定。 */
	public record ReadI(LVar target, LVar position, LVar output) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			// 常量实例在所有处理器间共享，写入等同于修改全局
			if (output.constant) return;
			var targetObj = target.obj();
			// 非方块目标的回退：字符串按字符码取值，列表按序号取下标
			if (targetObj instanceof String text) {
				var address = (int) position.num();
				output.setnum(address < 0 || address >= text.length() ? Double.NaN : text.charAt(address));
				return;
			}
			if (targetObj instanceof List<?> list) {
				var address = (int) position.num();
				output.setobj(address < 0 || address >= list.size() ? null : list.get(address));
				return;
			}
			var senseable = exec.resolve(targetObj);
			// 目标拒绝本次读取（含特权校验未过）时将结果置空
			if (senseable == null || !senseable.read(position, output, exec.privileged)) output.setobj(null);
		}
	}
	/** {@code write <值> to <目标> at <位置>}：目标不接受写入时不作任何处理。 */
	public record WriteI(LVar target, LVar position, LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var senseable = exec.resolve(target.obj());
			if (senseable != null) senseable.write(position, value, exec.privileged);
		}
	}
	/** 可写内容由目标决定；方块状态属性走通用适配器，非特权处理器另受白名单限制。 */
	public record ControlI(String type, LVar target, LVar value, LVar facing) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var senseable = exec.resolve(target.obj());
			if (senseable == null) return;
			// 位置与特权均须传入：红石充能须记录来源，可修改范围取决于调用方是否特权
			// 末尾的值有两种读法：redstone 用作接入源的面，按行号写入的用作行号
			senseable.control(type, value, direction(facing), exec.selfPos, exec.privileged, facing.isobj ? 0 : (int) facing.numval);
		}
	}
	/** {@code setprop <属性> <目标> <值>}：写入单位属性或容器内容，属性名与 {@code sensor} 相同。 */
	public record SetPropI(LVar type, LVar target, LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var senseable = exec.resolve(target.obj());
			if (senseable == null) return;
			var key = type.obj();
			if (key instanceof LAccess access) senseable.setProp(access, value);
			else if (key instanceof String content) senseable.setProp(content, value);
		}
	}
	public record JumpI(ConditionOp op, LVar value, LVar compare, int address) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (address != -1 && op.test(value, compare)) exec.counter.numval = address;
		}
	}
	/** {@code printchar 65}：{@code value} 是字符码。 */
	public record PrintCharI(LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (exec.textBuffer.length() >= MAX_TEXT_BUFFER) return;
			// 对象值的字形需贴物品图标，该处无资源，跳过
			if (value.isobj) return;
			exec.textBuffer.append((char) Math.floor(value.numval));
		}
	}
	/** {@code format "..."}：替换缓冲区中编号最小的 {@code {N}} 占位符，用几个值须写几条。 */
	public record FormatI(LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var buffer = exec.textBuffer;
			var index = -1;
			var lowest = 10;
			// 跳过非占位符片段，无须逐字符扫描缓冲区
			for (var i = buffer.indexOf("{"); i >= 0 && buffer.length() - i > 2; i = buffer.indexOf("{", i + 1)) {
				var digit = buffer.charAt(i + 1);
				if (digit < '0' || digit > '9' || buffer.charAt(i + 2) != '}') continue;
				if (digit - '0' >= lowest) continue;
				lowest = digit - '0';
				index = i;
			}
			if (index == -1) return;
			// 与 print 共用同一份格式化，否则两处显示不一致
			buffer.replace(index, index + 3, PrintI.format(exec, value));
		}
	}
	/** {@code printflush <目标>}：把 {@code print} 攒下的文本交给目标。 */
	public record PrintFlushI(LVar target) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var senseable = exec.resolve(target.obj());
			// 无论目标是否接收，缓冲区都须清空
			var text = exec.drainText();
			if (senseable != null) senseable.print(text);
		}
	}
	/** {@code draw <类型> …}：文本按整段传入，字形尺寸由客户端掌握，故逐字符展开留到渲染时做。 */
	public record DrawI(GraphicsType type, LVar x, LVar y, LVar p1, LVar p2, LVar p3, LVar p4) implements LInstruction {
		/** 认不出内容时返回 -1。 */
		private static int content(@Nullable Object value) {
			if (!(value instanceof String name)) return -1;
			var id = ResourceLocation.tryParse(name.startsWith("@") ? name.substring(1) : name);
			if (id == null) return -1;
			// 物品与方块分别编号，低 5 位记录类型
			if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.get(id)) << 5;
			if (BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.getId(BuiltInRegistries.BLOCK.get(id)) << 5 | 1;
			return -1;
		}
		@Override
		public void run(LExecutor exec) {
			if (exec.graphicsBuffer.size() >= MAX_GRAPHICS_BUFFER) return;
			if (type == GraphicsType.col) {
				// 打包色携带位模式，不得截断，故在指令层即拆成普通 color 命令
				var argb = unpackColor(x.num());
				exec.graphicsBuffer.add(new DrawCmd(
					GraphicsType.color,
					ARGB32.red(argb),
					ARGB32.green(argb),
					ARGB32.blue(argb),
					ARGB32.alpha(argb),
					0,
					0
				));
				return;
			}
			// 画布无抗锯齿，各分量一律截为整数
			if (type == GraphicsType.print) {
				var text = exec.drainText();
				if (text.isEmpty()) return;
				exec.graphicsBuffer.add(new DrawCmd(type, (int) x.num(), (int) y.num(), (int) p1.num(), 0, 0, 0, text));
				return;
			}
			var first = (int) p1.num();
			var last = (int) p4.num();
			var xval = (int) x.num();
			var yval = (int) y.num();
			if (type == GraphicsType.image) {
				// 内容折为编号与类型两部分，分别占用第一个与最后一个操作数
				var packed = content(p1.obj());
				first = packed & 0x3FF;
				last = packed >> 10;
			} else if (type == GraphicsType.scale) {
				// 缩放量为小数，按步长折算为整数
				xval = (int) (x.num() / GraphicsType.SCALE_STEP);
				yval = (int) (y.num() / GraphicsType.SCALE_STEP);
			}
			exec.graphicsBuffer.add(new DrawCmd(type, xval, yval, first, (int) p2.num(), (int) p3.num(), last));
		}
	}
	/** {@code drawflush <目标>}：把 {@code draw} 攒下的绘制命令交给目标。 */
	public record DrawFlushI(LVar target) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var senseable = exec.resolve(target.obj());
			if (senseable instanceof LDrawable drawable && drawable.drawable(exec)) drawable.draw(exec.graphicsBuffer);
			// 无论目标是否接收，缓冲区都须清空
			exec.graphicsBuffer.clear();
		}
	}
	public record PrintI(LVar value) implements LInstruction {
		/** {@code print} 与变量表共用同一份，否则两处显示不一致。 */
		public static String format(LExecutor exec, LVar var) {
			if (!var.isobj) {
				// 整数不显示小数点
				var numval = var.numval;
				return Math.abs(numval - Math.round(numval)) < 0.00001 ? String.valueOf(Math.round(numval)) : String.valueOf(numval);
			}
			return formatValue(exec, var.objval);
		}
		/** 坐标与链接的显示随世界状态变化，须退回格式化结果，否则方块被换掉时界面不刷新。 */
		public static Object changeKey(LExecutor exec, LVar var) {
			if (!var.isobj) return var.numval;
			if (var.objval instanceof BlockPos || var.objval instanceof LogicLink) return format(exec, var);
			return var.objval;
		}
		/** 认不出的对象显示为 {@code [object]}。 */
		private static String formatValue(LExecutor exec, @Nullable Object obj) {
			return switch (obj) {
				case null -> "null";
				case String text -> text;
				// 取带命名空间的完整注册名，避免不同模组的同名方块混淆
				case Block block -> BuiltInRegistries.BLOCK.getKey(block).toString();
				case Item item -> BuiltInRegistries.ITEM.getKey(item).toString();
				case Fluid fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString();
				case Entity entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
				case EntityType<?> type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
				// 方块实体按坐标显示为该处方块名，{@code @this} 即处理器自身
				case BlockEntity be -> formatBlock(exec, be.getBlockPos());
				// query 查出的建筑存坐标，显示为该处方块名
				case BlockPos pos -> formatBlock(exec, pos);
				case Enum<?> value -> value.name();
				case LogicLink link -> formatLink(exec, link);
				default -> "[object]";
			};
		}
		/** 维度不在时退回坐标本身。 */
		private static String formatBlock(LExecutor exec, BlockPos pos) {
			if (exec.level == null) return pos.toShortString();
			return BuiltInRegistries.BLOCK.getKey(exec.level.getBlockState(pos).getBlock()).toString();
		}
		/** 没有目标时是 {@code null}。 */
		private static String formatLink(LExecutor exec, LogicLink link) {
			if (!link.valid() || exec.level == null || exec.selfPos == null) return "null";
			var pos = link.absolute(exec.selfPos);
			if (!exec.level.isLoaded(pos)) return "null";
			return BuiltInRegistries.BLOCK.getKey(exec.level.getBlockState(pos).getBlock()).toString();
		}
		@Override
		public void run(LExecutor exec) {
			if (exec.textBuffer.length() >= MAX_TEXT_BUFFER) return;
			var str = format(exec, value);
			exec.textBuffer.append(str, 0, Math.min(str.length(), MAX_TEXT_BUFFER - exec.textBuffer.length()));
		}
	}
}
