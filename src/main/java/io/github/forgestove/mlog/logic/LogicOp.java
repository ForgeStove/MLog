package io.github.forgestove.mlog.logic;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
/** {@code op} 指令支持的运算。 */
public enum LogicOp {
	add("+", Double::sum),
	sub("-", (a, b) -> a - b),
	mul("*", (a, b) -> a * b),
	div("/", (a, b) -> a / b),
	idiv("//", (a, b) -> Math.floor(a / b)),
	mod("%", (a, b) -> a % b),
	emod("%%", (a, b) -> (a % b + b) % b),
	pow("^", Math::pow),
	equal("==", (a, b) -> Math.abs(a - b) < 0.000001 ? 1 : 0, (a, b) -> Objects.equals(a, b) ? 1 : 0),
	notEqual("not", (a, b) -> Math.abs(a - b) < 0.000001 ? 0 : 1, (a, b) -> !Objects.equals(a, b) ? 1 : 0),
	land("and", (a, b) -> a != 0 && b != 0 ? 1 : 0),
	lessThan("<", (a, b) -> a < b ? 1 : 0),
	lessThanEq("<=", (a, b) -> a <= b ? 1 : 0),
	greaterThan(">", (a, b) -> a > b ? 1 : 0),
	greaterThanEq(">=", (a, b) -> a >= b ? 1 : 0),
	strictEqual("===", (a, b) -> 0), // 实际比较在 LExecutor.OpI 里特判，这个 lambda 用不上
	shl("<<", (a, b) -> (long) a << (long) b),
	shr(">>", (a, b) -> (long) a >> (long) b),
	ushr(">>>", (a, b) -> (long) a >>> (long) b),
	or("or", (a, b) -> (long) a | (long) b),
	and("b-and", (a, b) -> (long) a & (long) b),
	xor("xor", (a, b) -> (long) a ^ (long) b),
	not("flip", a -> ~(long) a),
	max("max", true, Math::max),
	min("min", true, Math::min),
	angle("angle", true, (x, y) -> Math.toDegrees(Math.atan2(y, x))),
	angleDiff("anglediff", true, LogicOp::angleDist),
	len("len", true, Math::hypot),
	noise("noise", true, SimplexNoise::raw2d),
	abs("abs", Math::abs),
	sign("sign", Math::signum),
	log("log", Math::log),
	logn("logn", (x, y) -> Math.log(x) / Math.log(y)),
	log10("log10", Math::log10),
	floor("floor", Math::floor),
	ceil("ceil", Math::ceil),
	round("round", Math::round),
	sqrt("sqrt", Math::sqrt),
	rand("rand", d -> ThreadLocalRandom.current().nextDouble() * d),
	sin("sin", d -> Math.sin(Math.toRadians(d))),
	cos("cos", d -> Math.cos(Math.toRadians(d))),
	tan("tan", d -> Math.tan(Math.toRadians(d))),
	asin("asin", d -> Math.toDegrees(Math.asin(d))),
	acos("acos", d -> Math.toDegrees(Math.acos(d))),
	atan("atan", d -> Math.toDegrees(Math.atan(d))),
	;
	/** 供界面下拉选择的全部算子名。 */
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	/** 名字到算子的表，供 {@link #byName(String)} 查。 */
	private static final Map<String, LogicOp> byName = new HashMap<>();
	/** 界面要当词来显示的符号，其余都是运算符，原样画。 */
	private static final Set<String> TOKEN_SYMBOLS = Set.of("not", "and", "or", "b-and", "xor", "flip");
	static {
		for (var op : values()) byName.put(op.name(), op);
	}
	public final OpObjLambda2 objFunction2;
	public final OpLambda2 function2;
	public final OpLambda1 function1;
	public final boolean unary, func;
	public final String symbol;
	LogicOp(String symbol, OpLambda2 function) {
		this(symbol, function, null);
	}
	LogicOp(String symbol, OpLambda2 function, OpObjLambda2 objFunction) {
		this.symbol = symbol;
		function2 = function;
		function1 = null;
		unary = false;
		objFunction2 = objFunction;
		func = false;
	}
	LogicOp(String symbol, boolean func, OpLambda2 function) {
		this.symbol = symbol;
		function2 = function;
		function1 = null;
		unary = false;
		objFunction2 = null;
		this.func = func;
	}
	LogicOp(String symbol, OpLambda1 function) {
		this.symbol = symbol;
		function1 = function;
		function2 = null;
		unary = true;
		objFunction2 = null;
		func = false;
	}
	/** @return 对应的算子，名字不认识时返回 {@code null}。 */
	public static LogicOp byName(String name) {
		return byName.get(name);
	}
	/** 返回带符号的角度差。 */
	static double angleDist(double a, double b) {
		var d = (a - b) % 360;
		return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
	}
	/**
	 * @return 界面显示用的名字。上面那几个词查本地化，其余返回符号本身——
	 *    {@code LogicFont.text} 认不出 key 时会把它当纯文本画，运算符正好落在这条路上。
	 */
	public String display() {
		return TOKEN_SYMBOLS.contains(symbol) ? "name.token.mlog." + symbol : symbol;
	}
	/**
	 * @return 悬停提示用的本地化键。
	 * 	<p>只有不好一眼看懂的算子写了说明，加减乘、取整这些没有对应的键。
	 */
	public String tipKey() {
		return "lenum.mlog." + name();
	}
	@Override
	public String toString() {
		return symbol;
	}
	public interface OpObjLambda2 {
		double get(Object a, Object b);
	}
	public interface OpLambda2 {
		double get(double a, double b);
	}
	public interface OpLambda1 {
		double get(double a);
	}
}
