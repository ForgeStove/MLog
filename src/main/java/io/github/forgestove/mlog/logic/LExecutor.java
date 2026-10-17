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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/** 不含单位、队伍等概念。 */
public class LExecutor {
	public static final int MAX_INSTRUCTIONS = 1000;
	public static final int MAX_TEXT_BUFFER = 400;
	/** 超出部分丢弃。 */
	public static final int MAX_GRAPHICS_BUFFER = 256;
	/** 面操作数取 0~5，其余按未指定处理。 */
	private static final int FACES = 6;
	/** 每 tick 由方块实体取走并清空。 */
	public final StringBuilder textBuffer = new StringBuilder();
	/** 由 {@code drawflush} 取走并清空。 */
	public final List<DrawCmd> graphicsBuffer = new ArrayList<>();
	private final Map<String, LVar> varIndex = new HashMap<>();
	public LInstruction[] instructions = {};
	/** 排除数字常量与内置变量。 */
	public LVar[] vars = {};
	public LVar counter, thisv, ipt, queries, linksVar;
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
