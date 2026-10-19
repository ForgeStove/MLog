package io.github.forgestove.mlog.logic;
import net.minecraft.locale.Language;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
public enum LogicOp implements Tipped {
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
	strictEqual("===", (a, b) -> 0), // 实际比较在 LExecutor.OpI 中特判，该 lambda 不参与运算
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
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	private static final Map<String, LogicOp> byName = new HashMap<>();
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
	public static LogicOp byName(String name) {
		return byName.get(name);
	}
	static double angleDist(double a, double b) {
		var d = (a - b) % 360;
		return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
	}
	/** 界面显示用的名字：有 {@code mlog.enum.op.<名>} 词条就用它，否则原样显示符号。 */
	public String display() {
		return Language.getInstance().has(key()) ? key() : symbol;
	}
	/** 枚举键；显示名可能只是符号，故与 {@link #display()} 分开。 */
	public String key() {
		return "mlog.enum.op." + name().toLowerCase(Locale.ROOT);
	}
	/** 悬停提示的本地化键；语言文件里只给易混淆的算子。 */
	@Override
	public String tipKey() {
		return key() + ".tip";
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
