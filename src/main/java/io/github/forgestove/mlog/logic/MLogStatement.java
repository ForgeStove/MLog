package io.github.forgestove.mlog.logic;
import org.jetbrains.annotations.Nullable;
/**
 * 我们自己的语句基类。在 {@link LStatement} 之上，放它没有的东西：
 * 参数区布局、文本读写、本地化 key。各条语句都继承这一层。
 */
public abstract class MLogStatement extends LStatement {
	/** 参数区小词的词表 key 前缀（{@code name.token.mlog.<小词>}）。只有这类 label 挂悬停提示，见 {@link LStatement#param}。 */
	public static final String TOKEN_KEY_PREFIX = "name.token.mlog.";
	/**
	 * 按一行代码里的 token 填自己，越界的尾部字段保持默认。
	 * <p>扫描注册时先造出实例再调它，
	 * 所以子类直接写自己的字段、返回 {@code this} 就行；没有参数的语句不用覆盖。
	 */
	public MLogStatement parse(String[] tokens, int length) {
		return this;
	}
	/** 描述参数区布局。 */
	public abstract void build(Table builder);
	/** @return 语句名的 lang key。 */
	public String nameKey() {
		return "instruction.mlog." + typeName();
	}
	/** @return 语句说明的 lang key，没有对应文本时语句表不出悬停提示。 */
	public String tipKey() {
		return nameKey() + ".tip";
	}
	/** @return 复制出的同类型语句，解析失败返回 {@code null}。 */
	public @Nullable MLogStatement copy() {
		var source = new StringBuilder();
		write(source);
		// 按自身的特权级别解析回来：世界处理器上的特权语句一复制就变成占位，那就没法复制了
		var parsed = LAssembler.read(source.toString(), privileged());
		return parsed.isEmpty() ? null : parsed.getFirst();
	}
}
