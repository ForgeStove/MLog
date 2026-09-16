package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.logic.LExecutor.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;
/** 本期实现的语句集合。字段顺序与 Mindustry 一致，缺失的尾部字段保留默认值。 */
public class LStatements {
	/** 语句表里可选的语句类型，顺序即显示顺序。 */
	public static final List<Supplier<LStatement>> ALL = List.of(
		SetStatement::new,
		OperationStatement::new,
		SensorStatement::new,
		JumpStatement::new,
		PrintStatement::new
	);
	/**
	 * 界面宽度基准。字段按 Mindustry 的 180 折算到 MC 的字体尺度。
	 * <p>条件和算子按钮都是纯按钮：{@code OP_W} 放运算符，{@code OP_W_LONG} 放本地化之后的词
	 * （「不等于」「异或」这类比符号宽）。
	 */
	private static final int FIELD_W = 70, OP_W = 30, OP_W_LONG = 36, SELECT_W = 80;
	/** 解析失败或未实现的语句占位，编译时会被丢弃。 */
	public static class InvalidStatement extends LStatement {
		@Override
		public LInstruction build(LAssembler builder) {
			return null;
		}
		@Override
		public void write(StringBuilder builder) {}
		@Override
		public void buildParams(LayoutBuilder builder) {}
	}
	/** {@code set result 0} */
	public static class SetStatement extends LStatement {
		public String to = "result", from = "0";
		public static SetStatement parse(String[] tokens, int len) {
			var s = new SetStatement();
			if (len > 1) s.to = tokens[1];
			if (len > 2) s.from = tokens[2];
			return s;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SetI(builder.var(from), builder.var(to));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("set ").append(to).append(' ').append(sanitize(from));
		}
		@Override
		public void buildParams(LayoutBuilder builder) {
			builder.field(() -> to, value -> to = value, FIELD_W);
			builder.label(" = ");
			builder.field(() -> from, value -> from = value, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.operation;
		}
	}
	/** {@code op add result a b} */
	public static class OperationStatement extends LStatement {
		public LogicOp op = LogicOp.add;
		public String dest = "result", a = "a", b = "b";
		public static OperationStatement parse(String[] tokens, int len) {
			var s = new OperationStatement();
			if (len > 1) s.op = LogicOp.valueOf(tokens[1]);
			if (len > 2) s.dest = tokens[2];
			if (len > 3) s.a = tokens[3];
			if (len > 4) s.b = tokens[4];
			return s;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new OpI(op, builder.var(a), builder.var(b), builder.var(dest));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("op ")
				.append(op.name())
				.append(' ')
				.append(dest)
				.append(' ')
				.append(sanitize(a))
				.append(' ')
				.append(sanitize(b));
		}
		@Override
		public void buildParams(LayoutBuilder builder) {
			builder.field(() -> dest, value -> dest = value, FIELD_W);
			builder.label(" = ");
			// 一元运算只有一个操作数，函数式运算的算子写在前面
			if (op.unary) {
				opSelect(builder);
				builder.field(() -> a, value -> a = value, FIELD_W);
			} else if (op.func) {
				opSelect(builder);
				builder.field(() -> a, value -> a = value, FIELD_W);
				builder.field(() -> b, value -> b = value, FIELD_W);
			} else {
				builder.field(() -> a, value -> a = value, FIELD_W);
				opSelect(builder);
				builder.field(() -> b, value -> b = value, FIELD_W);
			}
		}
		private void opSelect(LayoutBuilder builder) {
			// 对齐 Mindustry：算子是纯按钮，点开选项列表，不给手输的输入框
			builder.option(
				() -> op.name(),
				value -> op = LogicOp.valueOf(value),
				() -> LogicOp.NAMES,
				name -> LogicOp.valueOf(name).display(),
				OP_W_LONG,
				4
			);
		}
		@Override
		public LCategory category() {
			return LCategory.operation;
		}
	}
	/** {@code sensor result block1 @totalItems} */
	public static class SensorStatement extends LStatement {
		public String to = "result", from = "block1", type = "@totalItems";
		public static SensorStatement parse(String[] tokens, int len) {
			var s = new SensorStatement();
			if (len > 1) s.to = tokens[1];
			if (len > 2) s.from = tokens[2];
			if (len > 3) s.type = tokens[3];
			return s;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SenseI(builder.var(from), builder.var(to), builder.var(type));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("sensor ").append(to).append(' ').append(from).append(' ').append(type);
		}
		@Override
		public void buildParams(LayoutBuilder builder) {
			builder.field(() -> to, value -> to = value, FIELD_W);
			builder.label(" = ");
			builder.select(() -> type, value -> type = value, () -> LAccess.NAMES, SELECT_W);
			builder.labelKey("name.token.mlog.in");
			builder.field(() -> from, value -> from = value, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.block;
		}
	}
	/** {@code jump 5 notEqual x false}，跳转标签由 {@link LParser} 在解析期换成行号。 */
	public static class JumpStatement extends LStatement {
		/** 编辑态的跳转目标。解析后由 {@link LParser} 回填，列表增删或重排后由画布重算。 */
		public @Nullable LStatement dest;
		public int destIndex;
		public ConditionOp op = ConditionOp.notEqual;
		public String value = "x", compare = "false";
		public static JumpStatement parse(String[] tokens, int len) {
			var s = new JumpStatement();
			if (len > 1) s.destIndex = Integer.parseInt(tokens[1]);
			if (len > 2) s.op = ConditionOp.valueOf(tokens[2]);
			if (len > 3) s.value = tokens[3];
			if (len > 4) s.compare = tokens[4];
			return s;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new JumpI(op, builder.var(value), builder.var(compare), destIndex);
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("jump ")
				.append(destIndex)
				.append(' ')
				.append(op.name())
				.append(' ')
				.append(sanitize(value))
				.append(' ')
				.append(sanitize(compare));
		}
		@Override
		public void buildParams(LayoutBuilder builder) {
			builder.labelKey("name.token.mlog.if");
			if (op != ConditionOp.always) {
				builder.field(() -> value, v -> value = v, FIELD_W);
				conditionSelect(builder);
				builder.field(() -> compare, v -> compare = v, FIELD_W);
			} else conditionSelect(builder);
			builder.spacer();
			builder.node(() -> dest, target -> dest = target);
		}
		private void conditionSelect(LayoutBuilder builder) {
			// 对齐 Mindustry：条件是纯按钮，点开选项列表，不带输入框
			builder.option(
				() -> op.name(),
				v -> op = ConditionOp.valueOf(v),
				() -> ConditionOp.NAMES,
				name -> ConditionOp.valueOf(name).display(),
				op == ConditionOp.always ? OP_W_LONG : OP_W,
				// 对齐 Mindustry：条件列表三列排开
				3
			);
		}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
	}
	/** {@code print "hello"} */
	public static class PrintStatement extends LStatement {
		public String value = "\"frog\"";
		public static PrintStatement parse(String[] tokens, int len) {
			var s = new PrintStatement();
			if (len > 1) s.value = tokens[1];
			return s;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new PrintI(builder.var(value));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("print ").append(sanitize(value));
		}
		@Override
		public void buildParams(LayoutBuilder builder) {
			// 打印的值占满整行，输入框和卡片同宽
			builder.field(() -> value, v -> value = v, LayoutBuilder.STRETCH);
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
	}
}
