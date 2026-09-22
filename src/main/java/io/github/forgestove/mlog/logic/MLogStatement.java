package io.github.forgestove.mlog.logic;
import org.jetbrains.annotations.Nullable;
/** 本模组的语句基类：在 {@link LStatement} 之上提供参数区布局、文本读写与本地化 key。 */
public abstract class MLogStatement extends LStatement {
	/** 参数区小词的词表 key 前缀（{@code name.token.mlog.<小词>}）；仅此类别名附加悬停提示。 */
	public static final String TOKEN_KEY_PREFIX = "name.token.mlog.";
	/** @return 语句说明的 lang key；无对应文本时不显示悬停提示。 */
	public static String tipKey(String id) {
		return nameKey(id) + ".tip";
	}
	/** 按一行代码的 token 填充自身字段，越界的尾部字段保持默认；无参数的语句无须覆写。 */
	public MLogStatement parse(String[] tokens, int length) {
		return this;
	}
	/** 声明参数区布局。 */
	public abstract void build(Table builder);
	/** @return 语句名的 lang key。 */
	public String nameKey() {
		return nameKey(id);
	}
	/** @return 语句名的 lang key。 */
	public static String nameKey(String id) {
		return "instruction.mlog." + id;
	}
	/** @return 同类型语句的副本，解析失败时返回 {@code null}。 */
	public @Nullable MLogStatement copy() {
		var source = new StringBuilder();
		write(source);
		// 按自身特权级别解析，否则特权语句复制后即为占位语句
		var parsed = LAssembler.read(source.toString(), privileged());
		return parsed.isEmpty() ? null : parsed.getFirst();
	}
}
