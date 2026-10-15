package io.github.forgestove.mlog.logic;
import org.jetbrains.annotations.Nullable;
/** 在 {@link LStatement} 之上提供参数区布局、文本读写与本地化 key。 */
public abstract class MLogStatement extends LStatement {
	/** 参数区小词的词表 key 前缀（{@code name.token.mlog.<小词>}）；仅此类别名附加悬停提示。 */
	public static final String TOKEN_KEY_PREFIX = "name.token.mlog.";
	/** 无对应文本时不显示悬停提示。 */
	public static String tipKey(String id) {
		return nameKey(id) + ".tip";
	}
	public static String nameKey(String id) {
		return "instruction.mlog." + id;
	}
	/** 越界的尾部字段保持默认；无参数的语句无须覆写。 */
	public MLogStatement parse(String[] tokens, int length) {
		return this;
	}
	public abstract void build(Table builder);
	public String nameKey() {
		return nameKey(typeName());
	}
	/** 解析失败时返回 {@code null}。 */
	public @Nullable MLogStatement copy() {
		var source = new StringBuilder();
		write(source);
		// 按自身特权级别解析，否则特权语句复制后即为占位语句
		var parsed = LAssembler.read(source.toString(), privileged());
		return parsed.isEmpty() ? null : parsed.getFirst();
	}
}
