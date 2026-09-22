package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.logic.LExecutor.LInstruction;
import io.github.forgestove.mlog.logic.Table.Label;

import java.util.Locale;
/** 语句：指令的中间表示，既负责文本与指令之间的转换，也描述界面上的参数区布局。 */
public abstract class LStatement {
	/** 语句名，由 {@link Statements} 造出实例时按 {@link RegisterStatement#id()} 注入。 */
	protected String id;
	/** 把字段包成不会破坏 token 化的形式。 */
	public static String sanitize(String value) {
		if (value.isEmpty()) return "";
		if (value.length() == 1) {
			var c = value.charAt(0);
			if (c == '"' || c == ';' || c == ' ' || c == '\n' || c == '\t' || c == '#') return "invalid";
			return value;
		}
		var res = new StringBuilder(value.length());
		if (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
			res.append('"');
			for (var i = 1; i < value.length() - 1; i++) {
				var c = value.charAt(i);
				if (c == '\\' && i + 1 < value.length() - 1) {
					var next = value.charAt(i + 1);
					if (next == '"' || next == '\\' || next == 'n') {
						res.append(c).append(next);
						i++;
						continue;
					}
					if (next == 'u' && i + 5 < value.length() - 1 && isHex(value, i + 2)) {
						res.append(value, i, i + 6);
						i += 5;
						continue;
					}
				}
				switch (c) {
					case '"' -> res.append("\\\"");
					case '\\' -> res.append("\\\\");
					case '\n' -> res.append("\\n");
					default -> res.append(c);
				}
			}
			res.append('"');
		} else for (var i = 0; i < value.length(); i++) {
			var c = value.charAt(i);
			res.append(switch (c) {
				case ';' -> 's';
				case '"' -> '\'';
				case ' ', '\t', '\n', '#' -> '_';
				default -> c;
			});
		}
		return res.toString();
	}
	private static boolean isHex(String value, int from) {
		for (var i = from; i < from + 4; i++) if (Character.digit(value.charAt(i), 16) == -1) return false;
		return true;
	}
	/** @return 编译后的指令。 */
	public abstract LInstruction build(LAssembler builder);
	/** @return 界面上的分组与配色。 */
	public LCategory category() {
		return LCategory.unknown;
	}
	/**
	 * @return 是不是只有世界处理器能用。
	 * 	<p>非世界处理器的语句表里不列它，代码里写了的也会被换成认不出来的占位。
	 */
	public boolean privileged() {
		return false;
	}
	/** @return 是不是不该出现在语句表里（只能手写的那种）。 */
	public boolean hidden() {
		return false;
	}
	/**
	 * 读完之后再修字段。
	 * <p>调用点在 {@link Statements#parse}：缺尾值要等整行都读进来才判得出来，
	 * 塞不进子类的 {@link MLogStatement#parse}。
	 */
	public void afterRead() {}
	/**
	 * 给参数区的小词挂悬停提示。
	 * <p>key 是 {@code instruction.mlog.<语句名>.<小词>}，语言文件里没有这条就不显示（判断在界面层）。
	 * 小词取词表 key 的 token；取 label 上的文字的话，非英文界面下就查不到了。
	 */
	public void param(Label label) {
		label.setTipKey("instruction.mlog." + typeName() + "." + label.token());
	}
	/**
	 * @return 语句名，同时用作 lang key 后缀、文本里的名字与语句表的搜索依据。
	 * 	<p>值由注册表注入，实例这一侧不查注解；注册表造不出来的（认不出语句的占位）按类名推。
	 */
	public String typeName() {
		return id != null ? id : getClass().getSimpleName().replace("Statement", "").toLowerCase(Locale.ROOT);
	}
	/** 把自身写成一行逻辑代码。 */
	public abstract void write(StringBuilder builder);
}
