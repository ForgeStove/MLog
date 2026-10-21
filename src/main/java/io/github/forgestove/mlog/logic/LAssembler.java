package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.logic.LExecutor.LInstruction;
import io.github.forgestove.mlog.logic.LStatements.JumpStatement;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;
public class LAssembler {
	public final Map<String, LVar> vars = new LinkedHashMap<>();
	public LInstruction[] instructions = {};
	/** 每 tick 指令数的上限，{@code setrate} 的取值以此为界。 */
	public int iptLimit;
	/** 世界处理器（特权）标记：特权语句与 {@code @queries} 以它为生效条件。 */
	public boolean privileged;
	/** 处理器链接的方块，{@code getlink} 按序号取用。 */
	public LogicLink[] links = {};
	/** 编译期即须求得的行号；每条语句恰产出一条指令。 */
	public int index;
	public LAssembler() {
		// 必须是数值变量
		putVar("@counter").isobj = false;
	}
	public static LAssembler assemble(
		String code,
		@Nullable Object self,
		BlockPos pos,
		int ipt,
		int iptLimit,
		List<LogicLink> links,
		boolean privileged
	) {
		var asm = new LAssembler();
		asm.privileged = privileged;
		asm.putConst("@this", self);
		// 坐标与链接数每个处理器各自一份，注册成常量
		asm.putConst("@thisx", pos.getX());
		asm.putConst("@thisy", pos.getY());
		asm.putConst("@thisz", pos.getZ());
		// 链接集合在运行期变化，故为变量
		asm.putVar("@links").setnum(links.size());
		asm.iptLimit = iptLimit;
		asm.links = links.toArray(LogicLink[]::new);
		// setrate 需要修改它，故不作为常量
		asm.putVar("@ipt").setnum(ipt);
		for (var link : links) asm.putConst(link.name(), link);
		// 仅世界处理器持有的结果列表，须在编译语句之前注册，否则代码中的 @queries 会另建空变量
		if (privileged) asm.putConst("@queries", new ArrayList<>());
		// 绑定生物按处理器各自保存，运行期由 ubind 写入
		asm.putVar("@unit");
		var list = new ArrayList<LInstruction>();
		for (var statement : read(code, privileged)) {
			// build 期间须得知自身落点行号，流程控制语句据此跳回自身
			asm.index = list.size();
			var instruction = statement.build(asm);
			if (instruction != null) list.add(instruction);
		}
		asm.instructions = list.toArray(LInstruction[]::new);
		return asm;
	}
	/** {@code privileged} 决定特权语句能否解析，非特权时替换为占位语句。 */
	public static List<MLogStatement> read(String text, boolean privileged) {
		if (text == null || text.isEmpty()) return List.of();
		return new LParser(text, privileged).parse();
	}
	public static List<MLogStatement> read(String text) {
		return read(text, false);
	}
	public static String write(List<MLogStatement> statements) {
		var out = new StringBuilder();
		for (var statement : statements) {
			statement.write(out);
			out.append('\n');
		}
		return out.toString();
	}
	/** 按语句在列表中的位置重算所有 {@code jump} 行号，列表增删或重排后必须调用；断开的目标写成 {@code -1}，否则旧行号残留会让保存下来的代码仍跳向旧目标。 */
	public static void reindex(List<MLogStatement> statements) {
		for (var statement : statements)
			if (statement instanceof JumpStatement jump) {
				var index = jump.dest == null ? -1 : statements.indexOf(jump.dest);
				// 目标语句已被删除时引用本身也须断开，否则卡片标题会一直显示「跳转 -> -1」
				if (index < 0) jump.dest = null;
				jump.destIndex = index;
			}
	}
	/** {@code %rrggbb} / {@code %rrggbbaa} 解析出的颜色值；位数不对或非十六进制时为 {@code null}。 */
	private static @Nullable Double parseColor(String symbol) {
		var hex = symbol.substring(1);
		if (hex.length() != 6 && hex.length() != 8) return null;
		try {
			// 补满八位后最高位即红色通道的最高位，Integer.parseInt 会溢出
			var rgba = Integer.parseUnsignedInt(hex + (hex.length() == 6 ? "ff" : ""), 16);
			return LExecutor.packColor(
				(rgba >>> 24 & 0xFF) / 255D,
				(rgba >>> 16 & 0xFF) / 255D,
				(rgba >>> 8 & 0xFF) / 255D,
				(rgba & 0xFF) / 255D
			);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	/** 解码字符串字面量里的 {@code \n}、{@code \"}、{@code \\} 与 {@code uXXXX}。 */
	static String unescape(String s) {
		if (s.indexOf('\\') == -1) return s;
		var out = new StringBuilder(s.length());
		for (var i = 0; i < s.length(); i++) {
			var c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length()) {
				var next = s.charAt(i + 1);
				if (next == 'n') {
					out.append('\n');
					i++;
					continue;
				}
				if (next == '"' || next == '\\') {
					out.append(next);
					i++;
					continue;
				}
				if (next == 'u' && i + 5 < s.length()) {
					out.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
					i += 5;
					continue;
				}
			}
			out.append(c);
		}
		return out.toString();
	}
	/** 支持 {@code 0x} / {@code 0b} 前缀与正负号，解析失败返回 {@code NaN} 表示这是变量名。 */
	static double parseDouble(String symbol) {
		var body = symbol;
		var sign = 1;
		if (body.startsWith("-")) {
			sign = -1;
			body = body.substring(1);
		} else if (body.startsWith("+")) body = body.substring(1);
		try {
			if (body.startsWith("0b")) return sign * Long.parseLong(body.substring(2), 2);
			if (body.startsWith("0x")) return sign * Long.parseLong(body.substring(2), 16);
			return Double.parseDouble(symbol);
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}
	public LVar putVar(String name) {
		return vars.computeIfAbsent(
			name, n -> {
				var var = new LVar(n);
				var.isobj = true;
				return var;
			}
		);
	}
	public LVar putConst(String name, @Nullable Object value) {
		var var = putVar(name);
		if (value instanceof Number number) {
			var.isobj = false;
			var.numval = number.doubleValue();
			var.objval = null;
		} else {
			var.isobj = true;
			var.objval = value;
		}
		var.constant = true;
		return var;
	}
	/** 占位常量：不进入变量表，读取结果与未设置相同。 */
	public LVar none() {
		return putConst("___none", null);
	}
	/** {@code @} 开头的注册名（声音、粒子等）按字符串常量处理，其余仍按普通操作数。 */
	public LVar namedVar(String symbol, Registry<?> registry) {
		if (symbol.startsWith("@")) {
			var name = symbol.substring(1);
			var id = ResourceLocation.tryParse(name);
			if (id != null && registry.containsKey(id)) return putConst("___" + symbol, name);
		}
		return var(symbol);
	}
	public LVar var(String symbol) {
		var existing = vars.get(symbol);
		if (existing != null) return existing;
		// 内置变量的实例在所有处理器间共享，不得复制一份
		var global = GlobalVars.get(symbol);
		if (global != null) {
			vars.put(symbol, global);
			return global;
		}
		if (symbol.startsWith("@")) {
			var name = symbol.substring(1);
			// 物品与流体名也带 @ 前缀，须按名字读取为字符串；和属性同名时取内容，属性读取侧同样接受字符串
			if (MLogSenseables.isContent(name)) return putConst("___" + symbol, name);
			var access = LAccess.byName(name);
			if (access != null) return putConst("___" + symbol, access);
			// 其余 @ 名字作为普通变量：拼错属性名不得静默变为字符串常量
			return putVar(symbol);
		}
		if (symbol.length() > 1 && symbol.charAt(0) == '"' && symbol.charAt(symbol.length() - 1) == '"')
			return putConst("___" + symbol, unescape(symbol.substring(1, symbol.length() - 1)));
		// 颜色字面量 %rrggbb / %rrggbbaa，6 位时 alpha 补满为 ff
		if (symbol.charAt(0) == '%') {
			var color = parseColor(symbol);
			if (color != null) return putConst("___" + symbol, color);
		}
		var value = parseDouble(symbol);
		if (Double.isNaN(value)) return putVar(symbol);
		return putConst("___" + value, value);
	}
	public @Nullable LVar getVar(String name) {
		return vars.get(name);
	}
}
