package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/** 逻辑代码的运行时。不含单位、队伍等概念。 */
public class LExecutor {
	public static final int MAX_INSTRUCTIONS = 1000;
	public static final int MAX_TEXT_BUFFER = 400;
	/** 绘图缓冲区的条数上限，超出部分丢弃。 */
	public static final int MAX_GRAPHICS_BUFFER = 256;
	/** 面操作数取 0~5，其余按未指定处理。 */
	private static final int FACES = 6;
	/** {@code print} 指令的输出缓冲区，每 tick 由方块实体取走并清空。 */
	public final StringBuilder textBuffer = new StringBuilder();
	/** {@code draw} 指令的绘图缓冲区，由 {@code drawflush} 取走并清空。 */
	public final List<DrawCmd> graphicsBuffer = new ArrayList<>();
	public LInstruction[] instructions = {};
	/** 参与同步的变量（排除数字常量与内置变量）。 */
	public LVar[] vars = {};
	/** 变量名到变量，是 {@link #vars} 的索引，供按名读写使用。 */
	private final Map<String, LVar> varIndex = new HashMap<>();
	public LVar counter, thisv, ipt, queries, linksVar;
	/** 每 tick 的指令数上限，装载时由 {@code @ipt} 的初值定下；{@code setrate} 只能在这个范围内调。 */
	public int iptLimit;
	/** 链接的方块，{@code getlink} 按序号取用。 */
	public LogicLink[] links = {};
	public boolean yield;
	/** 这段代码是否运行于特权处理器。特权方块据此拒绝非特权的读写。 */
	public boolean privileged;
	/** 执行所在的维度，用于把链接解析成实体方块。 */
	public @Nullable Level level;
	/** 处理器自身的世界坐标，用来把链接的相对坐标还原成绝对坐标。 */
	public @Nullable BlockPos selfPos;
	/** @return 面操作数对应的面；对象值表示不指定面，数值越界按六面回绕。 */
	private static @Nullable Direction direction(LVar facing) {
		if (facing.isobj) return null;
		return Direction.from3DDataValue(Math.floorMod((int) facing.numval, FACES));
	}
	public boolean initialized() {
		return instructions.length > 0;
	}
	/** 执行指令表中的当前指令。{@code @counter} 越界时归零，因此代码会自然循环。 */
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
		iptLimit = builder.iptLimit;
		links = builder.links;
		privileged = builder.privileged;
	}
	/** 链接集合变化后就地重绑：链接数组、{@code @links} 计数，以及按名字引用的链接变量。 */
	public void updateLinks(List<LogicLink> links) {
		this.links = links.toArray(LogicLink[]::new);
		linksVar.setnum(this.links.length);
		// 链接数无上限，先按名字建索引，免得每个变量都把链接表重扫一遍
		var byName = new HashMap<String, LogicLink>();
		for (var link : links) byName.put(link.name(), link);
		for (var var : vars) {
			// 名称匹配则重绑；原为链接且已不在名单内的降级为普通变量。链接变量为常量，须用 setlink
			var linked = byName.get(var.name);
			if (linked != null) var.setlink(linked);
			else if (var.objval instanceof LogicLink) var.setlink(null);
		}
	}
	/** 把链接解析成可感测对象。 */
	public @Nullable MLogSenseable resolve(@Nullable Object target) {
		return resolve(target, null);
	}
	/**
	 * @param side 从哪一面读，{@code null} 表示不带面
	 * @return 目标对应的可感测对象，解析不了时返回 {@code null}
	 */
	public @Nullable MLogSenseable resolve(@Nullable Object target, @Nullable Direction side) {
		if (target instanceof MLogSenseable senseable) return senseable;
		if (target instanceof Entity entity) return MLogSenseables.of(entity);
		if (target instanceof BlockPos pos && level != null) return MLogSenseables.at(level, pos, side);
		if (target instanceof LogicLink link && level != null && selfPos != null)
			// 失效的链接按读不到处理，与目标未加载同等对待
			return link.valid() ? MLogSenseables.at(level, link.absolute(selfPos), side) : null;
		return null;
	}
	/** @return 变量池里叫这个名字的变量，没有则返回 {@code null}。供 {@code read} / {@code write} 查别人的变量用。 */
	public @Nullable LVar optionalVar(String name) {
		return varIndex.get(name);
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
			// 严格相等需比较类型（数值或对象），double 签名的 OpLambda2 无法表达，只能在此特判
			if (op == LogicOp.strictEqual)
				dest.setnum(a.isobj == b.isobj && (a.isobj ? Objects.equals(a.objval, b.objval) : a.numval == b.numval) ? 1 : 0);
				// LogicOp 保证一元运算非空的是 function1、其余情况是 function2
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
			// 按序号取容器内容，与 @size 一样在指令里单独处理
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
	 * <p>颜色是 32 位整数，而变量只有 double 一种载体，所以按位塞进 double 的低 32 位，
	 * 解包时按同样方式取回来。
	 */
	public record PackColorI(LVar result, LVar r, LVar g, LVar b, LVar a) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			result.setnum(packColor(r.num(), g.num(), b.num(), a.num()));
		}
	}
	/** 把一个颜色值拆回四个 0~1 的分量，是 {@link PackColorI} 的逆运算。 */
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
	/**
	 * 把四个 0~1 分量打包成一个颜色值：ARGB 整数按原样塞进 double 的位里。
	 * <p>{@code packcolor}、{@code unpackcolor}、{@code draw col} 与 {@code %rrggbb} 字面量共用这一套编码。
	 */
	static double packColor(double red, double green, double blue, double alpha) {
		var argb = ARGB32.color(channel(alpha), channel(red), channel(green), channel(blue));
		return Double.longBitsToDouble(Integer.toUnsignedLong(argb));
	}
	/** @return 打包值里的 ARGB 整数。 */
	static int unpackColor(double packed) {
		return (int) Double.doubleToRawLongBits(packed);
	}
	/** @return 0~1 的分量折算成 0~255。 */
	private static int channel(double value) {
		return (int) Math.clamp(value * 255, 0, 255);
	}
	/** 跳至指令表末尾，本 tick 其余指令不再执行。{@code @counter} 越界后下一 tick 自然归零。 */
	public record EndI() implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			exec.counter.numval = exec.instructions.length;
		}
	}
	/**
	 * 等够指定秒数再往下走。
	 * <p>时间未到则将 {@code @counter} 拉回自身并让出本 tick，下一 tick 再次检查；
	 * 每次检查累计 1/20 秒，累计至 {@link #value} 即清空计时、正常继续。
	 */
	public static class WaitI implements LInstruction {
		private final LVar value;
		private final int address;
		/** 已等待的秒数。等待期间处理器停在此条上，因此每条指令只需一份各自的计时。 */
		private float waited;
		public WaitI(LVar value, int address) {
			this.value = value;
			this.address = address;
		}
		@Override
		public void run(LExecutor exec) {
			var seconds = value.num();
			if (seconds <= 0) {
				// 等待 0 秒也至少让出本 tick，避免处理器停在此条上空转
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
	/** 停在此处不再继续。与 {@code wait} 的区别在于不会放行。 */
	public record StopI(int address) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			exec.counter.numval = address;
		}
	}
	/** 改本处理器每 tick 执行的指令数，超出方块的速率时按速率封顶。 */
	public record SetRateI(LVar amount) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			exec.ipt.numval = Math.clamp((int) amount.num(), 1, exec.iptLimit);
		}
	}
	/** 按序号取链接，越界返回 {@code null}。 */
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
			// 结果为活引用，每 tick 重新生成——保留上一轮已失效的对象没有意义
			results.clear();
			var box = box();
			if (type == QueryType.unit) {
				// 单位为生物与玩家：末地水晶、矿车一类不计入「单位」
				results.addAll(level.getEntities(
					(Entity) null,
					box,
					entity -> entity instanceof LivingEntity && inside(box, entity.getX(), entity.getY(), entity.getZ())
				));
				return;
			}
			// 建筑仅遍历盒内已加载区块的方块实体表，不为一次查询加载区块
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
						// 存坐标而非适配器：坐标不会因方块实体卸载或替换而过期，
						// 读取时由 resolve 现取，变量表也能显示为方块名
						results.add(pos);
					}
				}
		}
		/** @return {@code @queries} 里那个结果列表；变量没了、或里面不是列表时返回 {@code null}。 */
		@SuppressWarnings("unchecked")
		private static @Nullable List<Object> results(@Nullable LVar queries) {
			// 类型擦除：运行期只能识别为 List，其中元素始终为 Object
			return queries != null && queries.objval instanceof List<?> list ? (List<Object>) list : null;
		}
		/** @return 形状的包围盒。圆是中心 ± 半径，长方体是最小角 + 三边；负边长由 {@code AABB} 自行归一。 */
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
			// result 可能为字面量常量，而常量实例在所有处理器间共享，写入等同于修改全局
			if (result.constant) return;
			var index = (int) id.num();
			result.setobj(index >= 0 && index < type.registry.size() ? type.registry.byId(index) : null);
		}
	}
	/** {@code read <结果> = <目标> at <位置>}：从目标读一个值。位置的解释方式由目标自行决定。 */
	public record ReadI(LVar target, LVar position, LVar output) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			// output 可能为字面量常量，而常量实例在所有处理器间共享，写入等同于修改全局
			if (output.constant) return;
			var targetObj = target.obj();
			// 非方块可读对象时的回退：字符串按字符码取值，列表（@queries）按序号取下标
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
			// 目标不接受此次读取（包括「它是特权方块、而我不是特权处理器」）时将结果置空
			if (senseable == null || !senseable.read(position, output, exec.privileged)) output.setobj(null);
		}
	}
	/** {@code write <值> to <目标> at <位置>}：把值写进目标，目标不接受写入时不作任何处理。 */
	public record WriteI(LVar target, LVar position, LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var senseable = exec.resolve(target.obj());
			if (senseable != null) senseable.write(position, value, exec.privileged);
		}
	}
	/** 控制建筑，可写内容由目标决定；属性名为方块状态时走通用适配器，非特权处理器另受白名单限制。 */
	public record ControlI(String type, LVar target, LVar value, LVar facing) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var senseable = exec.resolve(target.obj());
			if (senseable == null) return;
			// 位置与特权均须传入：红石充能一类效果需记录来源，可修改的范围取决于处理器是否有特权。
			// 末尾的值按属性有两种读法：redstone 用作接入源的面，按行号写入的用作行号
			senseable.control(
				type,
				value,
				direction(facing),
				exec.selfPos,
				exec.privileged,
				facing.isobj ? 0 : (int) facing.numval
			);
		}
	}
	public record JumpI(ConditionOp op, LVar value, LVar compare, int address) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (address != -1 && op.test(value, compare)) exec.counter.numval = address;
		}
	}
	/** {@code printchar 65}：把一个字符追加进打印缓冲区。 */
	public record PrintCharI(LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (exec.textBuffer.length() >= MAX_TEXT_BUFFER) return;
			// 对象值的字形需贴物品图标，此处无对应资源，跳过
			if (value.isobj) return;
			exec.textBuffer.append((char) Math.floor(value.numval));
		}
	}
	/**
	 * {@code format "..."}：把打印缓冲区里编号最小的 {@code {N}} 占位符换成这个值；
	 * 每次替换一个，因此使用几个值就须写几条。
	 */
	public record FormatI(LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			var buffer = exec.textBuffer;
			var index = -1;
			var lowest = 10;
			// 跳着找占位符起点即可，不必逐字符过一遍缓冲区
			for (var i = buffer.indexOf("{"); i >= 0 && buffer.length() - i > 2; i = buffer.indexOf("{", i + 1)) {
				var digit = buffer.charAt(i + 1);
				if (digit < '0' || digit > '9' || buffer.charAt(i + 2) != '}') continue;
				if (digit - '0' >= lowest) continue;
				lowest = digit - '0';
				index = i;
			}
			if (index == -1) return;
			// 与 print 共用同一份格式化，两处显示才一致
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
	/**
	 * {@code draw <类型> …}：把一条绘制命令追加进绘图缓冲区。
	 * <p>{@code print} 把打印缓冲区里的整段文本作为一条命令带走：字形尺寸只有客户端掌握，
	 * 展开成逐字符留到渲染时做。
	 */
	public record DrawI(GraphicsType type, LVar x, LVar y, LVar p1, LVar p2, LVar p3, LVar p4) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (exec.graphicsBuffer.size() >= MAX_GRAPHICS_BUFFER) return;
			if (type == GraphicsType.col) {
				// 打包色携带位模式，不能截断，也不进入缓冲区：在指令层即拆成普通 color 命令，与 MDT 一致
				var argb = unpackColor(x.num());
				exec.graphicsBuffer.add(
					new DrawCmd(GraphicsType.color, ARGB32.red(argb), ARGB32.green(argb), ARGB32.blue(argb), ARGB32.alpha(argb), 0, 0)
				);
				return;
			}
			// 其余各分量一律截为整数，MDT 打包命令使用 numi()；画布本无抗锯齿，小数只会使两侧相差一格。
			// 未沿用其 10 位符号幅值（±511 回绕），画布最大仅 500 像素，越界回绕只会更糟
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
		/** @return 内容编号与内容类型折成的值，认不出内容时返回 -1。 */
		private static int content(@Nullable Object value) {
			if (!(value instanceof String name)) return -1;
			var id = ResourceLocation.tryParse(name.startsWith("@") ? name.substring(1) : name);
			if (id == null) return -1;
			// 物品与方块分别编号，低 5 位记录类型
			if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.get(id)) << 5;
			if (BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.getId(BuiltInRegistries.BLOCK.get(id)) << 5 | 1;
			return -1;
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
		@Override
		public void run(LExecutor exec) {
			if (exec.textBuffer.length() >= MAX_TEXT_BUFFER) return;
			var str = format(exec, value);
			exec.textBuffer.append(str, 0, Math.min(str.length(), MAX_TEXT_BUFFER - exec.textBuffer.length()));
		}
		/**
		 * 把变量的值转成文本。
		 * <p>{@code print} 与变量表共用同一份，两处显示才一致。
		 */
		public static String format(LExecutor exec, LVar var) {
			if (!var.isobj) {
				// 整数不显示小数点
				var numval = var.numval;
				return Math.abs(numval - Math.round(numval)) < 0.00001 ? String.valueOf(Math.round(numval)) : String.valueOf(numval);
			}
			return formatValue(exec, var.objval);
		}
		/** 对象转成有意义的名字，无法识别的统一为 {@code [object]}。 */
		private static String formatValue(LExecutor exec, @Nullable Object obj) {
			return switch (obj) {
				case null -> "null";
				case String text -> text;
				// 带命名空间的完整注册名，避免不同模组同名的方块混淆
				case Block block -> BuiltInRegistries.BLOCK.getKey(block).toString();
				case Item item -> BuiltInRegistries.ITEM.getKey(item).toString();
				case Fluid fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString();
				// 单位显示其类型
				case Entity entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
				case EntityType<?> type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
				// query 查出的建筑存的是坐标，显示为该处的方块名
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
			if (!link.valid() || exec.level == null || exec.selfPos == null) return "null";
			var pos = link.absolute(exec.selfPos);
			if (!exec.level.isLoaded(pos)) return "null";
			return BuiltInRegistries.BLOCK.getKey(exec.level.getBlockState(pos).getBlock()).toString();
		}
	}
}
