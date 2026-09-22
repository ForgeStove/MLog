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
	 * <p>{@code end} / {@code wait} / {@code stop} 都要跳到自身，编译期就得问出来。
	 * 注意这里是<b>指令</b>行号而不是语句序号：{@code build} 返回 {@code null} 的语句会被丢掉，
	 * 两者只有在没有空语句时才一致。
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
		// 链接集合会在运行期变（加减链接不重编译），所以是变量而不是常量
		asm.putVar("@links").setnum(links.size());
		asm.iptLimit = ipt;
		asm.links = links.toArray(LogicLink[]::new);
		// 不能做成常量：setrate 要能改它，处理器每 tick 也按它决定执行几条
		asm.putVar("@ipt").setnum(ipt);
		for (var link : links) asm.putConst(link.name(), link);
		// query 的结果列表，每个处理器一份，只有世界处理器才有。
		// 必须赶在编译语句之前注册：代码里的 @queries 走的是 var() 的「已存在就直接拿」这条路，
		// 晚一步就会另建一个空变量
		if (privileged) asm.putConst("@queries", new ArrayList<>());
		var list = new ArrayList<LInstruction>();
		for (var statement : read(code, privileged)) {
			// build 期间要能问到自己会落在哪一行，几个流程控制语句靠它跳回自身
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
	/** @return 解析出的语句序列。 */
	public static List<MLogStatement> read(String text) {
		return read(text, false);
	}
	/** 同上，{@code privileged} 决定特权语句能不能解析出来（非特权时换成占位）。 */
	public static List<MLogStatement> read(String text, boolean privileged) {
		if (text == null || text.isEmpty()) return List.of();
		return new LParser(text, privileged).parse();
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
	 * <p>断开的目标要写成 {@code -1}，那是 {@link JumpI} 认的"不跳转"。
	 * 只跳过没目标的语句会把上一次的行号留在 {@code destIndex} 里，保存出去的代码仍在跳旧目标。
	 */
	public static void reindex(List<MLogStatement> statements) {
		for (var statement : statements)
			if (statement instanceof JumpStatement jump) {
				var index = jump.dest == null ? -1 : statements.indexOf(jump.dest);
				// 目标那条语句已经被删掉了，引用本身也得断开，
				// 否则卡片标题会一直挂着「跳转 -> -1」
				if (index < 0) jump.dest = null;
				jump.destIndex = index;
			}
	}
	/** @return 变量名对应的 {@link LVar}，可能是字面量常量、内置变量或链接。 */
	public LVar var(String symbol) {
		var existing = vars.get(symbol);
		if (existing != null) return existing;
		// 内置变量的实例在所有处理器间共享，不能像字面量那样拷一份
		var global = GlobalVars.get(symbol);
		if (global != null) {
			vars.put(symbol, global);
			return global;
		}
		if (symbol.startsWith("@")) {
			var name = symbol.substring(1);
			var access = LAccess.byName(name);
			if (access != null) return putConst("___" + symbol, access);
			// 物品与流体名（下拉里那两张图标墙）也带 @ 前缀，它们是按名字读的字符串，
			// 当成普通变量的话执行时值是空的，读出来永远是 null
			if (MLogSenseables.isContent(name)) return putConst("___" + symbol, name);
			// 其余认不出来的 @ 名字就是普通变量，不再当字符串常量——打错一个属性名不该悄悄变成别的类型
			return putVar(symbol);
		}
		if (symbol.length() > 1 && symbol.charAt(0) == '"' && symbol.charAt(symbol.length() - 1) == '"')
			return putConst("___" + symbol, unescape(symbol.substring(1, symbol.length() - 1)));
		var value = parseDouble(symbol);
		if (Double.isNaN(value)) return putVar(symbol);
		return putConst("___" + value, value);
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
