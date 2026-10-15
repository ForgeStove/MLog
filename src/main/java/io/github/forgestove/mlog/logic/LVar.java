package io.github.forgestove.mlog.logic;
import org.jetbrains.annotations.Nullable;
public class LVar {
	public final String name;
	public boolean isobj, constant;
	public @Nullable Object objval;
	public double numval;
	public LVar(String name) {
		this.name = name;
	}
	public static boolean invalid(double d) {
		return Double.isNaN(d) || Double.isInfinite(d);
	}
	public @Nullable Object obj() {
		return isobj ? objval : null;
	}
	public double num() {
		return isobj ? objval != null ? 1 : 0 : invalid(numval) ? 0 : numval;
	}
	public void setnum(double value) {
		if (constant) return;
		if (invalid(value)) {
			objval = null;
			isobj = true;
		} else {
			numval = value;
			objval = null;
			isobj = false;
		}
	}
	public void setobj(@Nullable Object value) {
		if (constant) return;
		objval = value;
		isobj = true;
	}
	/** 链接变量既是常量（逻辑代码不可改写）又须随链接集合变更，而 {@link #setobj} 对常量无效，故单独提供并越过 {@code constant} 保护；{@code value} 为 {@code null} 时降级为普通变量。 */
	public void setlink(@Nullable Object value) {
		isobj = true;
		if (value == null) {
			objval = null;
			constant = false;
		} else {
			objval = value;
			constant = true;
		}
	}
	public void set(LVar other) {
		isobj = other.isobj;
		if (isobj) objval = other.objval;
		else numval = invalid(other.numval) ? 0 : other.numval;
	}
	@Override
	public String toString() {
		return name + ": " + (isobj ? objval : numval) + (constant ? " [const]" : "");
	}
}
