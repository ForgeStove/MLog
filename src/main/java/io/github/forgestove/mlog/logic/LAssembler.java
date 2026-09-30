package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.logic.LExecutor.*;
import io.github.forgestove.mlog.logic.LStatements.JumpStatement;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/** 把语句序列编译成指令序列，并管理变量表。 */
public class LAssembler {
	/** 名字到变量的映射，链接与内置变量都在这里。 */
	public final Map<String, LVar> vars = new LinkedHashMap<>();
	public LInstruction[] instructions = {};
	/** 编进 {@code @ipt} 的初始速率，{@link LExecutor} 拿它当每 tick 指令数的上限。 */
	public int iptLimit;
	/** 世界处理器（特权）标记：特权语句与 {@code @queries} 都只在它上面成立。 */
	public boolean privileged;
	/** 处理器链接的方块，{@code getlink} 按序号取用。 */
	public LogicLink[] links = {};
	/**
	 * 正在编译的这条语句会落在哪一行。
	 * <p>{@code end} / {@code wait} / {@code stop} 都要跳到自身，编译期即须求得该行号。
	 * 注意这里是指令行号而不是语句序号：{@code build} 返回 {@code null} 的语句会被丢弃，
	 * 两者仅在没有空语句时一致。
	 */
	public int index;
	public LAssembler() {
		// 指令计数器，必须是数值变量
		putVar("@counter").isobj = false;
	}
	/** 注册一个变量名，已存在则直接返回。 */
	public LVar putVar(String name) {
		return vars.computeIfAbsent(
			name, n -> {
				var var = new LVar(n);
				// 变量默认是空对象
				var.isobj = true;
				return var;
			}
		);
	}
	/** 编译一个处理器的完整代码。 */
	public static LAssembler assemble(
		String code,
		@Nullable Object self,
		BlockPos pos,
		int ipt,
		List<LogicLink> links,
		boolean privileged
	) {
		var asm = new LAssembler();
		asm.privileged = privileged;
		asm.putConst("@this", self);
		// 坐标和链接数都是每个处理器自己的，注册成常量
		asm.putConst("@thisx", pos.getX());
		asm.putConst("@thisy", pos.getY());
		asm.putConst("@thisz", pos.getZ());
		// 链接集合会在运行期变化（增减链接不重新编译），故为变量而非常量
		asm.putVar("@links").setnum(links.size());
		asm.iptLimit = ipt;
		asm.links = links.toArray(LogicLink[]::new);
		// 不能作为常量：setrate 需要修改它，处理器每 tick 也据此决定执行条数
		asm.putVar("@ipt").setnum(ipt);
		for (var link : links) asm.putConst(link.name(), link);
		// query 的结果列表，每个处理器一份，仅世界处理器持有。
		// 必须在编译语句之前注册：代码中的 @queries 依赖 var() 的存在即复用逻辑，
		// 注册过晚将另建一个空变量
		if (privileged) asm.putConst("@queries", new ArrayList<>());
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
	/** 注册一个常量变量。 */
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
	/**
	 * @return 占位常量，供语句中暂未使用的操作数占位。
	 * 	<p>为常量，不进变量表；值为空对象，读取结果与未设置相同。
	 */
	public LVar none() {
		return putConst("___none", null);
	}
	/** 同上，{@code privileged} 决定特权语句能否解析（非特权时替换为占位语句）。 */
	public static List<MLogStatement> read(String text, boolean privileged) {
		if (text == null || text.isEmpty()) return List.of();
		return new LParser(text, privileged).parse();
	}
	/** @return 解析出的语句序列。 */
	public static List<MLogStatement> read(String text) {
		return read(text, false);
	}
	/** 把语句序列写回逻辑代码。 */
	public static String write(List<MLogStatement> statements) {
		var out = new StringBuilder();
		for (var statement : statements) {
			statement.write(out);
			out.append('\n');
		}
		return out.toString();
	}
	/**
	 * 按语句在列表中的位置重算所有 {@code jump} 的行号。列表增删或重排后必须调用。
	 * <p>断开的目标要写成 {@code -1}，即 {@link JumpI} 认可的不跳转。
	 * 若只跳过无目标的语句，旧行号会残留在 {@code destIndex} 中，保存的代码仍跳向旧目标。
	 */
	public static void reindex(List<MLogStatement> statements) {
		for (var statement : statements)
			if (statement instanceof JumpStatement jump) {
				var index = jump.dest == null ? -1 : statements.indexOf(jump.dest);
				// 目标语句已被删除时引用本身也须断开，
				// 否则卡片标题会一直显示「跳转 -> -1」
				if (index < 0) jump.dest = null;
				jump.destIndex = index;
			}
	}
	/** @return 变量名对应的 {@link LVar}，可能是字面量常量、内置变量或链接。 */
	public LVar var(String symbol) {
		var existing = vars.get(symbol);
		if (existing != null) return existing;
		// 内置变量的实例在所有处理器间共享，不能像字面量那样复制一份
		var global = GlobalVars.get(symbol);
		if (global != null) {
			vars.put(symbol, global);
			return global;
		}
		if (symbol.startsWith("@")) {
			var name = symbol.substring(1);
			var access = LAccess.byName(name);
			if (access != null) return putConst("___" + symbol, access);
			// 物品与流体名（下拉列表中的图标网格）也带 @ 前缀，是按名字读取的字符串；
			// 若作为普通变量，执行时值为空，读取结果恒为 null
			if (MLogSenseables.isContent(name)) return putConst("___" + symbol, name);
			// 其余无法识别的 @ 名字按普通变量处理，不再视为字符串常量：拼错属性名不应静默变为其他类型
			return putVar(symbol);
		}
		if (symbol.length() > 1 && symbol.charAt(0) == '"' && symbol.charAt(symbol.length() - 1) == '"')
			return putConst("___" + symbol, unescape(symbol.substring(1, symbol.length() - 1)));
		// 颜色字面量：%rrggbb / %rrggbbaa，6 位时 alpha 补满为 ff
		if (symbol.charAt(0) == '%') {
			var color = parseColor(symbol);
			if (color != null) return putConst("___" + symbol, color);
		}
		var value = parseDouble(symbol);
		if (Double.isNaN(value)) return putVar(symbol);
		return putConst("___" + value, value);
	}
	/** @return {@code %rrggbb} / {@code %rrggbbaa} 解析出的颜色值；位数不对或不是十六进制时返回 {@code null}。 */
	private static @Nullable Double parseColor(String symbol) {
		var hex = symbol.substring(1);
		if (hex.length() != 6 && hex.length() != 8) return null;
		try {
			// 按无符号解析：补满八位后最高位即红色通道的最高位，Integer.parseInt 会溢出
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
	public @Nullable LVar getVar(String name) {
		return vars.get(name);
	}
}
