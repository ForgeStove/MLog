package io.github.forgestove.mlog.logic;
import net.minecraft.locale.Language;

import java.util.*;
/** {@code jump} 指令的跳转条件。 */
public enum ConditionOp implements Tipped {
	equal("==", (a, b) -> Math.abs(a - b) < 0.000001, Objects::equals),
	notEqual("not", (a, b) -> Math.abs(a - b) >= 0.000001, (a, b) -> !Objects.equals(a, b)),
	lessThan("<", (a, b) -> a < b),
	lessThanEq("<=", (a, b) -> a <= b),
	greaterThan(">", (a, b) -> a > b),
	greaterThanEq(">=", (a, b) -> a >= b),
	strictEqual("===", (a, b) -> false),
	always("always", (a, b) -> true);
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	private static final Map<String, ConditionOp> byName = new HashMap<>();
	static {
		for (var condition : values()) byName.put(condition.name(), condition);
	}
	public final CondObjOpLambda objFunction;
	public final CondOpLambda function;
	public final String symbol;
	ConditionOp(String symbol, CondOpLambda function) {
		this(symbol, function, null);
	}
	ConditionOp(String symbol, CondOpLambda function, CondObjOpLambda objFunction) {
		this.symbol = symbol;
		this.function = function;
		this.objFunction = objFunction;
	}
	public static ConditionOp byName(String name) {
		return byName.get(name);
	}
	public String display() {
		return Language.getInstance().has(key()) ? key() : symbol;
	}
	/**
	 * 枚举键。
	 * 显示名可能只是符号，故与 {@link #display()} 分开。
	 */
	public String key() {
		return "mlog.enum.op." + name().toLowerCase(Locale.ROOT);
	}
	/**
	 * {@code equal} 和 {@code notEqual} 与 {@link LogicOp} 共用一份文案。
	 * 大小比较类算子无对应说明，查不到即不显示提示。
	 */
	@Override
	public String tipKey() {
		return key() + ".tip";
	}
	public boolean test(LVar va, LVar vb) {
		if (this == strictEqual) return va.isobj == vb.isobj && (va.isobj ? Objects.equals(va.objval, vb.objval) : va.numval == vb.numval);
		if (objFunction != null && va.isobj && vb.isobj) return objFunction.get(va.obj(), vb.obj());
		return function.get(va.num(), vb.num());
	}
	@Override
	public String toString() {
		return symbol;
	}
	public interface CondObjOpLambda {
		boolean get(Object a, Object b);
	}
	public interface CondOpLambda {
		boolean get(double a, double b);
	}
}
