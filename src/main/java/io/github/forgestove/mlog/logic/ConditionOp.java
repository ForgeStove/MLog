package io.github.forgestove.mlog.logic;
import java.util.*;
/** {@code jump} 指令的跳转条件。 */
public enum ConditionOp {
	equal("==", (a, b) -> Math.abs(a - b) < 0.000001, Objects::equals),
	notEqual("not", (a, b) -> Math.abs(a - b) >= 0.000001, (a, b) -> !Objects.equals(a, b)),
	lessThan("<", (a, b) -> a < b),
	lessThanEq("<=", (a, b) -> a <= b),
	greaterThan(">", (a, b) -> a > b),
	greaterThanEq(">=", (a, b) -> a >= b),
	strictEqual("===", (a, b) -> false),
	always("always", (a, b) -> true);
	/** 供界面下拉选择的全部条件名。 */
	public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();
	/** 名字到条件的表，供 {@link #byName(String)} 查。 */
	private static final Map<String, ConditionOp> byName = new HashMap<>();
	static {
		for (var condition : values()) byName.put(condition.name(), condition);
	}
	/** @return 对应的条件，名字不认识时返回 {@code null}。 */
	public static ConditionOp byName(String name) {
		return byName.get(name);
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
	/**
	 * @return 界面显示用的名字。{@code not} 和 {@code always} 是词，走本地化；其余都是符号，原样显示。
	 * 	<p>返回的 key 找不到译文时会被原样显示出来，符号正好落在这条路上。
	 */
	public String display() {
		return switch (this) {
			case notEqual -> "name.token.mlog.not";
			case always -> "name.token.mlog.always";
			default -> symbol;
		};
	}
	/**
	 * @return 悬停提示用的本地化键。
	 * 	<p>{@code equal} / {@code notEqual} 和 {@link LogicOp} 同名，两处共用一份文案；
	 * 	大小比较那几个没有说明，查不到就不提示。
	 */
	public String tipKey() {
		return "lenum.mlog." + name();
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
