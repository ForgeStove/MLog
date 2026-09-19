package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/** 逻辑代码的运行时。移植自 Mindustry 的 {@code LExecutor}，去掉了单位、队伍等概念。 */
public class LExecutor {
	public static final int MAX_INSTRUCTIONS = 1000;
	public static final int MAX_TEXT_BUFFER = 400;
	/** {@code print} 指令的输出缓冲区，每 tick 由方块实体取走并清空。 */
	public final StringBuilder textBuffer = new StringBuilder();
	public LInstruction[] instructions = {};
	/** 参与同步的变量（排除数字常量与内置变量）。 */
	public LVar[] vars = {};
	public LVar counter, thisv, ipt, queries;
	/** 每 tick 的指令数上限，装载时由 {@code @ipt} 的初值定下；{@code setrate} 只能在这个范围内调。 */
	public int iptLimit;
	/** 链接的方块，{@code getlink} 按序号取用。 */
	public LogicLink[] links = {};
	public boolean yield;
	/** 这段代码是不是特权处理器在跑。特权方块靠它挡下非特权的读写，对齐 Mindustry 的 {@code exec.privileged}。 */
	public boolean privileged;
	/** 执行所在的维度，用于把链接解析成实体方块。 */
	public @Nullable Level level;
	/** 处理器自身的世界坐标，用来把链接的相对坐标还原成绝对坐标。 */
	public @Nullable BlockPos selfPos;
	public boolean initialized() {
		return instructions.length > 0;
	}
	/** 执行一条指令。{@code @counter} 越界时归零，因此代码会自然循环。 */
	public void runOnce() {
		if (counter.numval >= instructions.length || counter.numval < 0) counter.numval = 0;
		if (counter.numval < instructions.length) {
			counter.isobj = false;
			instructions[(int) counter.numval++].run(this);
		}
	}
	/** 装载已编译的代码，重置全部变量。 */
	public void load(LAssembler builder) {
		textBuffer.setLength(0);
		var list = new ArrayList<LVar>();
		// 链接变量是常量但名字不以 _ / @ 开头，需要保留下来供界面显示
		for (var v : builder.vars.values()) if (!v.constant || v.name.charAt(0) != '_' && v.name.charAt(0) != '@') list.add(v);
		vars = list.toArray(LVar[]::new);
		instructions = builder.instructions;
		counter = builder.getVar("@counter");
		thisv = builder.getVar("@this");
		ipt = builder.getVar("@ipt");
		queries = builder.getVar("@queries");
		iptLimit = builder.iptLimit;
		links = builder.links;
		privileged = builder.privileged;
	}
	/** 把链接解析成可感测对象。 */
	public @Nullable MLogSenseable resolve(@Nullable Object target) {
		if (target instanceof MLogSenseable senseable) return senseable;
		if (target instanceof Entity entity) return MLogSenseables.of(entity);
		if (target instanceof BlockPos pos && level != null) return MLogSenseables.at(level, pos);
		if (target instanceof LogicLink link && level != null && selfPos != null) return MLogSenseables.at(level, link.absolute(selfPos));
		return null;
	}
	/** @return 变量池里叫这个名字的变量，没有则返回 {@code null}。供 {@code read} / {@code write} 查别人的变量用。 */
	public @Nullable LVar optionalVar(String name) {
		for (var var : vars) if (var.name.equals(name)) return var;
		return null;
	}
	/** 取出并清空 {@code print} 缓冲区。 */
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
			// 严格相等要比类型（数值还是对象），double 签名的 OpLambda2 表达不了，只能在这里特判，
			// 和 Mindustry 的 OpI.run 一样
			if (op == LogicOp.strictEqual)
				dest.setnum(a.isobj == b.isobj && (a.isobj ? Objects.equals(a.objval, b.objval) : a.numval == b.numval) ? 1 : 0);
			// LogicOp 保证一元运算非空的是 function1、其余情况是 function2
			else if (op.unary) dest.setnum(Objects.requireNonNull(op.function1).get(a.num()));
			else if (op.objFunction2 != null && a.isobj && b.isobj) dest.setnum(op.objFunction2.get(a.obj(), b.obj()));
			else dest.setnum(Objects.requireNonNull(op.function2).get(a.num(), b.num()));
		}
	}
	public record SenseI(LVar from, LVar to, LVar type) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			// 属性名可能是内置的 LAccess，也可能是任意的方块状态属性名
			var key = type.obj();
			var access = key instanceof LAccess builtin ? builtin.name() : key instanceof String name ? name : null;
			if (access == null) {
				to.setobj(null);
				return;
			}
			// 列表（query 写进 @queries 的结果）只量得出长度，对齐 Mindustry 的 SenseI 对 Seq 的处理
			if (from.obj() instanceof List<?> list) {
				if (key == LAccess.size) to.setnum(list.size());
				else to.setobj(null);
				return;
			}
			var senseable = exec.resolve(from.obj());
			if (senseable == null) {
				to.setobj(null);
				return;
			}
			var objOut = senseable.senseObject(access);
			// 没有对象输出时退回数值
			if (objOut == MLogSenseable.NO_SENSED) to.setnum(senseable.sense(access));
			else to.setobj(objOut);
		}
	}
	/** 三元：条件成立取 {@code yes}，否则取 {@code no}。 */
	public record SelectI(ConditionOp op, LVar result, LVar comp0, LVar comp1, LVar yes, LVar no) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (result.constant) return;
			result.set(op.test(comp0, comp1) ? yes : no);
		}
	}
	/**
	 * 四个 0~1 的分量打包成一个颜色值。
	 * <p>颜色是 32 位整数，而变量只有 double 一种载体，所以按位塞进 double 的低 32 位——
	 * Mindustry 的 {@code Color.toDoubleBits} 也是这个做法。解包时按同样方式取回来。
	 */
	public record PackColorI(LVar result, LVar r, LVar g, LVar b, LVar a) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var packed = ARGB32.color(channel(a.num()), channel(r.num()), channel(g.num()), channel(b.num()));
			result.setnum(Double.longBitsToDouble(Integer.toUnsignedLong(packed)));
		}
		private static int channel(double value) {
			return (int) Math.clamp(value * 255, 0, 255);
		}
	}
	/** 把一个颜色值拆回四个 0~1 的分量，是 {@link PackColorI} 的逆运算。 */
	public record UnpackColorI(LVar r, LVar g, LVar b, LVar a, LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var packed = (int) Double.doubleToRawLongBits(value.num());
			r.setnum(ARGB32.red(packed) / 255.0);
			g.setnum(ARGB32.green(packed) / 255.0);
			b.setnum(ARGB32.blue(packed) / 255.0);
			a.setnum(ARGB32.alpha(packed) / 255.0);
		}
	}
	/** 跳到指令表末尾，这一 tick 剩下的都不跑了。{@code @counter} 越界后下一 tick 会自然归零。 */
	public record EndI() implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			exec.counter.numval = exec.instructions.length;
		}
	}
	/**
	 * 等够指定秒数再往下走。
	 * <p>时间没到就把 {@code @counter} 拉回自身并让出本 tick，下一 tick 再来看一眼；
	 * 每看一眼累计 1/20 秒，攒够 {@link #value} 就清空计时、正常往下。
	 */
	public static class WaitI implements LInstruction {
		private final LVar value;
		private final int address;
		/** 已经等了多少秒。等待期间处理器停在这条上，所以每条指令只需要一份自己的计时。 */
		private float waited;
		public WaitI(LVar value, int address) {
			this.value = value;
			this.address = address;
		}
		@Override
		public void run(LExecutor exec) {
			var seconds = value.num();
			if (seconds <= 0) {
				// 等 0 秒也至少让出本 tick，免得处理器停在这条上空转
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
	/** 停在这里不再往下走。和 {@code wait} 的区别是它不会放行。 */
	public record StopI(int address) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			exec.counter.numval = address;
		}
	}
	/** 改本处理器每 tick 执行的指令数，超出方块的速率就按速率封顶。 */
	public record SetRateI(LVar amount) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			exec.ipt.numval = Math.clamp((int) amount.num(), 1, exec.iptLimit);
		}
	}
	/** 按序号取一条链接，越界给 {@code null}。 */
	public record GetLinkI(LVar output, LVar index) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var address = (int) index.num();
			output.setobj(address >= 0 && address < exec.links.length ? exec.links[address] : null);
		}
	}
	/** {@code query <形状> <类型> <x> <y> <z> …}：把区域里的单位或建筑查进 {@code @queries}。 */
	public record QueryI(QueryShape shape, QueryType type, LVar x, LVar y, LVar z, LVar w, LVar h, LVar d) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var level = exec.level;
			var results = results(exec.queries);
			if (level == null || results == null) return;
			// 结果是活引用，每 tick 重来一遍——留着上一轮已经死掉的对象没有意义
			results.clear();
			var box = box();
			if (type == QueryType.unit) {
				// 单位就是生物与玩家：末地水晶、矿车这类不算是“单位”
				results.addAll(level.getEntities(
					(Entity) null,
					box,
					entity -> entity instanceof LivingEntity && inside(box, entity.getX(), entity.getY(), entity.getZ())
				));
				return;
			}
			// 建筑只翻盒子里**已加载**的区块的方块实体表，不为一次查询去加载区块
			var minX = Mth.floor(box.minX) >> 4;
			var minZ = Mth.floor(box.minZ) >> 4;
			var maxX = Mth.floor(box.maxX) >> 4;
			var maxZ = Mth.floor(box.maxZ) >> 4;
			for (var cx = minX; cx <= maxX; cx++) for (var cz = minZ; cz <= maxZ; cz++) {
				var chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) continue;
				for (var pos : chunk.getBlockEntities().keySet()) {
					if (!inside(box, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) continue;
					// 存坐标而不是适配器：坐标不会因为方块实体后来卸载/换掉而过期，
					// 要读的时候由 resolve 现取，变量表里也能显示成方块名
					results.add(pos);
				}
			}
		}
		/** @return {@code @queries} 里那个结果列表；变量没了、或里面不是列表时返回 {@code null}。 */
		@SuppressWarnings("unchecked")
		private static @Nullable List<Object> results(@Nullable LVar queries) {
			// 类型擦除：运行期只看得出是个 List，往里装的始终是 Object
			return queries != null && queries.objval instanceof List<?> list ? (List<Object>) list : null;
		}
		/** @return 形状的包围盒。圆是中心 ± 半径，长方体是最小角 + 三边；负的边长由 {@code AABB} 自己归一。 */
		private AABB box() {
			var px = x.num();
			var py = y.num();
			var pz = z.num();
			if (shape == QueryShape.rect) return new AABB(px, py, pz, px + w.num(), py + h.num(), pz + d.num());
			var radius = Math.abs(w.num());
			return new AABB(px - radius, py - radius, pz - radius, px + radius, py + radius, pz + radius);
		}
		/**
		 * @return 点是否落在形状里。
		 * 	<p>包围盒只是预筛：查方块实体时它是唯一的粗筛，圆还得再按到球心的距离判一次。
		 */
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
	/** {@code lookup <类型> <结果> <编号>}：按编号在注册表里查一项内容，查不到给 {@code null}。 */
	public record LookupI(LookupType type, LVar result, LVar id) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			// result 可能是字面量常量，而常量实例在所有处理器间共享，写进去等于改全局
			if (result.constant) return;
			var index = (int) id.num();
			result.setobj(index >= 0 && index < type.registry.size() ? type.registry.byId(index) : null);
		}
	}
	/** {@code read <结果> = <目标> at <位置>}：从目标读一个值。位置怎么解释由目标自己定。 */
	public record ReadI(LVar target, LVar position, LVar output) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			// output 可能是字面量常量，而常量实例在所有处理器间共享，写进去等于改全局
			if (output.constant) return;
			var targetObj = target.obj();
			// 不是方块可读对象时的兜底，对齐 Mindustry：字符串按字符码取，列表（@queries）按序号取下标
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
			// 目标不认这次读取（包括「它是特权方块、而我不是特权处理器」）时把结果置空
			if (senseable == null || !senseable.read(position, output, exec.privileged)) output.setobj(null);
		}
	}
	/** {@code write <值> to <目标> at <位置>}：把值写进目标，目标不认写入就什么都不做。 */
	public record WriteI(LVar target, LVar position, LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var senseable = exec.resolve(target.obj());
			if (senseable != null) senseable.write(position, value, exec.privileged);
		}
	}
	/** 控制建筑，能写什么由目标自己决定；属性名是方块状态的话走通用适配器。 */
	public record ControlI(String type, LVar target, LVar value, LVar facing, LVar strong) implements LInstruction {
		/** 六个面：{@code facing} 取 0~5，别的一律按没指定算。 */
		private static final int FACES = 6;
		@Override
		public void run(LExecutor exec) {
			var senseable = exec.resolve(target.obj());
			// 带上自己的位置：需要跟随处理器生灭的效果（红石充能）得记住是谁下的
			if (senseable != null) senseable.control(type, value.num(), direction(facing), strong.num() != 0, exec.selfPos);
		}
		/**
		 * @return {@code facing} 对应的面。{@code null}（以及别的对象、越界值）都按没指定算，
		 * 	也就是六个面都接上；越界不往外抛
		 */
		private static @Nullable Direction direction(LVar facing) {
			if (facing.isobj) return null;
			var index = (int) facing.numval % FACES;
			return Direction.from3DDataValue(index);
		}
	}
	public record JumpI(ConditionOp op, LVar value, LVar compare, int address) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (address != -1 && op.test(value, compare)) exec.counter.numval = address;
		}
	}
	/** {@code printchar 65}：把一个字符追加进打印缓冲区，对齐 Mindustry 的 {@code PrintCharI}。 */
	public record PrintCharI(LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (exec.textBuffer.length() >= MAX_TEXT_BUFFER) return;
			// 对象值那边 Mindustry 是贴物品图标的字形，我们没有对应的东西，跳过
			if (value.isobj) return;
			exec.textBuffer.append((char) Math.floor(value.numval));
		}
	}
	/**
	 * {@code format "..."}：把打印缓冲区里编号最小的 {@code {N}} 占位符换成这个值，
	 * 对齐 Mindustry 的 {@code FormatI}；一次换一个，所以要用几个值就写几条。
	 */
	public record FormatI(LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var index = -1;
			var lowest = 10;
			for (var i = 0; i < exec.textBuffer.length(); i++) {
				if (exec.textBuffer.charAt(i) != '{' || exec.textBuffer.length() - i <= 2) continue;
				var digit = exec.textBuffer.charAt(i + 1);
				if (digit < '0' || digit > '9' || exec.textBuffer.charAt(i + 2) != '}') continue;
				if (digit - '0' >= lowest) continue;
				lowest = digit - '0';
				index = i;
			}
			if (index == -1) return;
			// 和 print 共用同一份格式化，两处显示才会一致
			exec.textBuffer.replace(index, index + 3, PrintI.format(exec, value));
		}
	}
	/** {@code printflush <目标>}：把 {@code print} 攒下的文本交给目标，对齐 Mindustry 的 {@code PrintFlushI}。 */
	public record PrintFlushI(LVar target) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var senseable = exec.resolve(target.obj());
			// 缓冲区不管目标收没收都要清（Mindustry 那边也是先给再清）
			var text = exec.drainText();
			if (senseable != null) senseable.print(text);
		}
	}
	public record PrintI(LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (exec.textBuffer.length() >= MAX_TEXT_BUFFER) return;
			var str = format(exec, value);
			exec.textBuffer.append(str, 0, Math.min(str.length(), MAX_TEXT_BUFFER - exec.textBuffer.length()));
		}
		/**
		 * 把变量的值转成文本。
		 * <p>{@code print} 与变量表共用这一份，两处显示才会一致（Mindustry 也是这么做的）。
		 */
		public static String format(LExecutor exec, LVar var) {
			if (!var.isobj) {
				// 整数就不显示小数点
				var numval = var.numval;
				return Math.abs(numval - Math.round(numval)) < 0.00001 ? String.valueOf(Math.round(numval)) : String.valueOf(numval);
			}
			return formatValue(exec, var.objval);
		}
		/** 对齐 Mindustry 的 {@code PrintI.toString}：对象转成有意义的名字，认不出来的一律 {@code [object]}。 */
		private static String formatValue(LExecutor exec, @Nullable Object obj) {
			return switch (obj) {
				case null -> "null";
				case String text -> text;
				// 带命名空间的完整注册名，避免不同模组同名的方块混淆
				case Block block -> BuiltInRegistries.BLOCK.getKey(block).toString();
				case Item item -> BuiltInRegistries.ITEM.getKey(item).toString();
				case Fluid fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString();
				// 单位显示它的类型，对齐 Mindustry 显示 {@code unit.type.name}
				case Entity entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
				case EntityType<?> type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
				// query 查出来的建筑存的是坐标，显示成那里的方块名
				case BlockPos pos -> formatBlock(exec, pos);
				case Enum<?> value -> value.name();
				case LogicLink link -> formatLink(exec, link);
				default -> "[object]";
			};
		}
		/** @return 坐标上的方块名，维度不在时退回坐标本身。 */
		private static String formatBlock(LExecutor exec, BlockPos pos) {
			if (exec.level == null) return pos.toShortString();
			return BuiltInRegistries.BLOCK.getKey(exec.level.getBlockState(pos).getBlock()).toString();
		}
		/** 链接输出它指向的方块名，没有目标时是 {@code null}。 */
		private static String formatLink(LExecutor exec, LogicLink link) {
			if (exec.level == null || exec.selfPos == null) return "null";
			var pos = link.absolute(exec.selfPos);
			if (!exec.level.isLoaded(pos)) return "null";
			return BuiltInRegistries.BLOCK.getKey(exec.level.getBlockState(pos).getBlock()).toString();
		}
	}
}
