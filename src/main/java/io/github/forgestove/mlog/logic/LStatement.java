package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.logic.LExecutor.LInstruction;
import io.github.forgestove.mlog.logic.Table.Label;

import java.util.Locale;
/** 语句：指令的中间表示，既负责文本与指令之间的转换，也描述界面上的参数区布局。 */
public abstract class LStatement {
	/** 语句名，由 {@link Statements} 创建实例时注入。 */
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
	/** @return 是否仅世界处理器可用；非世界处理器不列出，代码中出现的会被替换为占位语句。 */
	public boolean privileged() {
		return false;
	}
	/** @return 是否从语句表隐藏（仅能手写的语句）。 */
	public boolean hidden() {
		return false;
	}
	/** 读取完成后修正字段，由 {@link Statements#parse} 调用。 */
	public void afterRead() {}
	/** 为参数区的小词附加悬停提示：key 为 {@code instruction.mlog.<语句名>.<小词>}，语言文件中不存在时不显示。 */
	public void param(Label label) {
		label.setTipKey("instruction.mlog." + typeName() + "." + label.token());
	}
	/** @return 语句名，兼作 lang key 后缀与语句表搜索依据；未注入时按类名推导。 */
	public String typeName() {
		return id != null ? id : getClass().getSimpleName().replace("Statement", "").toLowerCase(Locale.ROOT);
	}
	/** 把自身写成一行逻辑代码。 */
	public abstract void write(StringBuilder builder);
}
