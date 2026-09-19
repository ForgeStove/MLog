package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.logic.LExecutor.LInstruction;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
/** 语句：指令的中间表示，既负责文本与指令之间的转换，也描述界面上的参数区布局。 */
public abstract class LStatement {
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
	/**
	 * 按一行代码里的 token 填自己，越界的尾部字段保持默认。
	 * <p>对应 Mindustry 的 {@code LStatement#read}：扫描注册时先造出实例再调它，
	 * 所以子类直接写自己的字段、返回 {@code this} 就行；没有参数的语句不用覆盖。
	 */
	public LStatement parse(String[] tokens, int length) {
		return this;
	}
	/** 描述参数区布局。 */
	public abstract void buildParams(LayoutBuilder builder);
	/** @return 界面上的分组与配色。 */
	public LCategory category() {
		return LCategory.unknown;
	}
	/** @return 语句说明的 lang key，没有对应文本时语句表不出悬停提示。 */
	public String tipKey() {
		return nameKey() + ".tip";
	}
	/**
	 * @return 是不是只有世界处理器能用，对应 Mindustry 的 {@code LStatement#privileged}。
	 * 	<p>非世界处理器的语句表里不列它，代码里写了的也会被换成认不出来的占位。
	 */
	public boolean privileged() {
		return false;
	}
	/** @return 语句名的 lang key。 */
	public String nameKey() {
		return "instruction.mlog." + typeName();
	}
	/** @return 语句类型名，同时用作 lang key 后缀与语句表的搜索依据。 */
	public String typeName() {
		return typeName(getClass());
	}
	/** @return 类名去掉 {@code Statement} 后缀并转小写。 */
	public static String typeName(Class<?> cls) {
		return cls.getSimpleName().replace("Statement", "").toLowerCase(Locale.ROOT);
	}
	/** @return 复制出的同类型语句，解析失败返回 {@code null}。 */
	public @Nullable LStatement copy() {
		var source = new StringBuilder();
		write(source);
		// 按自身的特权级别解析回来：世界处理器上的特权语句一复制就变成占位，那就没法复制了
		var parsed = LAssembler.read(source.toString(), privileged());
		return parsed.isEmpty() ? null : parsed.getFirst();
	}
	/** 把自身写成一行逻辑代码。 */
	public abstract void write(StringBuilder builder);
}
