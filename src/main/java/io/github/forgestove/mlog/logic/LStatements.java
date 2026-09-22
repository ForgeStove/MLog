package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.logic.LExecutor.*;
import io.github.forgestove.mlog.logic.Table.OptionGroup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;
/** 各条语句的实现。语句类由 {@link RegisterStatement} 注解扫描发现 */
public class LStatements {
	/**
	 * 界面宽度基准。字段宽度 180 折算到 MC 的字体尺度。
	 * <p>条件和算子按钮都是纯按钮：{@code OP_W} 放运算符，{@code OP_W_LONG} 放本地化之后的词
	 * （「不等于」「异或」这类比符号宽）。
	 */
	private static final int FIELD_W = 70, OP_W = 30, OP_W_LONG = 36;
	private static final int SELECT_W = 120;
	/** 解析失败或未实现的语句占位，编译时会被丢弃。 */
	public static class InvalidStatement extends MLogStatement {
		@Override
		public LInstruction build(LAssembler builder) {
			return null;
		}
		@Override
		public void write(StringBuilder builder) {}
		@Override
		public void build(Table builder) {}
	}
	/** {@code select result lessThan a b c d}：条件成立取 c，否则取 d。 */
	@RegisterStatement(id = SelectStatement.ID, order = 110)
	public static class SelectStatement extends MLogStatement {
		public static final String ID = "select";
		public String result = "result", comp0 = "x", comp1 = "false", yes = "a", no = "b";
		public ConditionOp op = ConditionOp.notEqual;
		@Override
		public SelectStatement parse(String[] tokens, int len) {
			if (len > 1) result = tokens[1];
			if (len > 2) op = ConditionOp.valueOf(tokens[2]);
			if (len > 3) comp0 = tokens[3];
			if (len > 4) comp1 = tokens[4];
			if (len > 5) yes = tokens[5];
			if (len > 6) no = tokens[6];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SelectI(op, builder.var(result), builder.var(comp0), builder.var(comp1), builder.var(yes), builder.var(no));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ')
				.append(result)
				.append(' ')
				.append(op.name())
				.append(' ')
				.append(sanitize(comp0))
				.append(' ')
				.append(sanitize(comp1))
				.append(' ')
				.append(sanitize(yes))
				.append(' ')
				.append(sanitize(no));
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> result, v -> result = v, FIELD_W);
			builder.label(" = ");
			builder.labelKey("name.token.mlog.if");
			builder.field(() -> comp0, v -> comp0 = v, FIELD_W);
			// 条件是纯按钮，点开选项列表，不带输入框
			builder.option(
				() -> op.name(),
				v -> op = ConditionOp.valueOf(v),
				() -> ConditionOp.NAMES,
				name -> ConditionOp.valueOf(name).display(),
				OP_W,
				3
			);
			builder.field(() -> comp1, v -> comp1 = v, FIELD_W);
			builder.labelKey("name.token.mlog.then");
			builder.field(() -> yes, v -> yes = v, FIELD_W);
			builder.labelKey("name.token.mlog.else");
			builder.field(() -> no, v -> no = v, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.operation;
		}
	}
	/** {@code packcolor result r g b a}：四个 0~1 的分量打包成一个颜色值。 */
	@RegisterStatement(id = PackColorStatement.ID, order = 150)
	public static class PackColorStatement extends MLogStatement {
		public static final String ID = "packcolor";
		public String result = "result", r = "1", g = "0", b = "0", a = "1";
		@Override
		public PackColorStatement parse(String[] tokens, int len) {
			if (len > 1) result = tokens[1];
			if (len > 2) r = tokens[2];
			if (len > 3) g = tokens[3];
			if (len > 4) b = tokens[4];
			if (len > 5) a = tokens[5];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new PackColorI(builder.var(result), builder.var(r), builder.var(g), builder.var(b), builder.var(a));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ')
				.append(result)
				.append(' ')
				.append(sanitize(r))
				.append(' ')
				.append(sanitize(g))
				.append(' ')
				.append(sanitize(b))
				.append(' ')
				.append(sanitize(a));
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> result, v -> result = v, FIELD_W);
			builder.label(" = ");
			builder.labelKey("name.token.mlog.pack");
			builder.field(() -> r, v -> r = v, FIELD_W);
			builder.field(() -> g, v -> g = v, FIELD_W);
			builder.field(() -> b, v -> b = v, FIELD_W);
			builder.field(() -> a, v -> a = v, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.operation;
		}
	}
	/** {@code unpackcolor r g b a color}：把一个颜色值拆回四个 0~1 的分量。 */
	@RegisterStatement(id = UnpackColorStatement.ID, order = 160)
	public static class UnpackColorStatement extends MLogStatement {
		public static final String ID = "unpackcolor";
		public String r = "r", g = "g", b = "b", a = "a", value = "color";
		@Override
		public UnpackColorStatement parse(String[] tokens, int len) {
			if (len > 1) r = tokens[1];
			if (len > 2) g = tokens[2];
			if (len > 3) b = tokens[3];
			if (len > 4) a = tokens[4];
			if (len > 5) value = tokens[5];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new UnpackColorI(builder.var(r), builder.var(g), builder.var(b), builder.var(a), builder.var(value));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ')
				.append(r)
				.append(' ')
				.append(g)
				.append(' ')
				.append(b)
				.append(' ')
				.append(a)
				.append(' ')
				.append(sanitize(value));
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> r, v -> r = v, FIELD_W);
			builder.field(() -> g, v -> g = v, FIELD_W);
			builder.field(() -> b, v -> b = v, FIELD_W);
			builder.field(() -> a, v -> a = v, FIELD_W);
			builder.label(" = ");
			builder.labelKey("name.token.mlog.unpack");
			builder.field(() -> value, v -> value = v, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.operation;
		}
	}
	/** {@code end}：这一 tick 剩下的指令都不跑了。 */
	@RegisterStatement(id = EndStatement.ID, order = 170)
	public static class EndStatement extends MLogStatement {
		public static final String ID = "end";
		@Override
		public LInstruction build(LAssembler builder) {
			return new EndI();
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID);
		}
		@Override
		public void build(Table builder) {}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
	}
	/** {@code stop}：停在这里，不再往下走。 */
	@RegisterStatement(id = StopStatement.ID, order = 130)
	public static class StopStatement extends MLogStatement {
		public static final String ID = "stop";
		@Override
		public LInstruction build(LAssembler builder) {
			return new StopI(builder.index);
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID);
		}
		@Override
		public void build(Table builder) {}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
	}
	/** {@code wait 0.5}：等够指定秒数再往下走。 */
	@RegisterStatement(id = WaitStatement.ID, order = 120)
	public static class WaitStatement extends MLogStatement {
		public static final String ID = "wait";
		public String value = "0.5";
		@Override
		public WaitStatement parse(String[] tokens, int len) {
			if (len > 1) value = tokens[1];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new WaitI(builder.var(value), builder.index);
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(value));
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> value, v -> value = v, FIELD_W);
			builder.labelKey("instruction.mlog.wait.unit");
		}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
	}
	/** {@code setrate}：改每 tick 执行的指令数，超出方块的速率就按速率封顶。 */
	@RegisterStatement(id = SetRateStatement.ID, order = 200)
	public static class SetRateStatement extends MLogStatement {
		public static final String ID = "setrate";
		public String amount = "6";
		@Override
		public SetRateStatement parse(String[] tokens, int len) {
			if (len > 1) amount = tokens[1];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SetRateI(builder.var(amount));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(amount));
		}
		@Override
		public void build(Table builder) {
			builder.label("ipt = ");
			builder.field(() -> amount, v -> amount = v, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
	}
	/** {@code getlink result 0}：按序号取一条链接。 */
	@RegisterStatement(id = GetLinkStatement.ID, order = 60)
	public static class GetLinkStatement extends MLogStatement {
		public static final String ID = "getlink";
		public String output = "result", address = "0";
		@Override
		public GetLinkStatement parse(String[] tokens, int len) {
			if (len > 1) output = tokens[1];
			if (len > 2) address = tokens[2];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new GetLinkI(builder.var(output), builder.var(address));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(output).append(' ').append(sanitize(address));
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> output, v -> output = v, FIELD_W);
			builder.label(" = ");
			builder.labelKey("name.token.mlog.link");
			builder.field(() -> address, v -> address = v, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.block;
		}
	}
	/**
	 * {@code read result cell1 0}：从目标读一个值。
	 * <p>位置是名字时读目标处理器变量池里的同名变量，是数字时按序号取它的一条链接。
	 */
	@RegisterStatement(id = ReadStatement.ID, order = 0)
	public static class ReadStatement extends MLogStatement {
		public static final String ID = "read";
		public String output = "result", target = "cell1", address = "0";
		@Override
		public ReadStatement parse(String[] tokens, int len) {
			if (len > 1) output = tokens[1];
			if (len > 2) target = tokens[2];
			if (len > 3) address = tokens[3];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new ReadI(builder.var(target), builder.var(address), builder.var(output));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(output).append(' ').append(target).append(' ').append(sanitize(address));
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> output, v -> output = v, FIELD_W);
			builder.label(" = ");
			builder.field(() -> target, v -> target = v, FIELD_W);
			builder.labelKey("name.token.mlog.at");
			builder.field(() -> address, v -> address = v, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
	}
	/** {@code write result cell1 0}：把值写进目标。只能按变量名写，数字位置留给内存方块。 */
	@RegisterStatement(id = WriteStatement.ID, order = 10)
	public static class WriteStatement extends MLogStatement {
		public static final String ID = "write";
		public String input = "result", target = "cell1", address = "0";
		@Override
		public WriteStatement parse(String[] tokens, int len) {
			if (len > 1) input = tokens[1];
			if (len > 2) target = tokens[2];
			if (len > 3) address = tokens[3];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new WriteI(builder.var(target), builder.var(address), builder.var(input));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(input).append(' ').append(target).append(' ').append(sanitize(address));
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> input, v -> input = v, FIELD_W);
			builder.labelKey("name.token.mlog.to");
			builder.field(() -> target, v -> target = v, FIELD_W);
			builder.labelKey("name.token.mlog.at");
			builder.field(() -> address, v -> address = v, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
	}
	/**
	 * {@code control open block1 1}：控制建筑的状态，可写的属性见 {@link LAccess#controlAllowed()}。
	 * <p>白名单只约束非特权处理器：世界处理器想改什么就写什么，按名字扫方块状态属性。
	 * <p>{@code power} 后面固定跟两个值，按位置认、不写字：
	 * {@code facing} 说的是**从哪一面接源**，0~5 取六个面、{@code null} 表示六面都接；
	 * {@code strong} 用 0/1 决定要不要连强充能一起给。
	 */
	@RegisterStatement(id = ControlStatement.ID, order = 70)
	public static class ControlStatement extends MLogStatement {
		public static final String ID = "control";
		public String type = "power", target = "block1", value = "15";
		/**
		 * 末尾的值，按属性两种读法：{@code power} 当接源的面，值设置那类当行号。
		 */
		public String facing = "null", strong = "0";
		@Override
		public ControlStatement parse(String[] tokens, int len) {
			if (len > 1) type = tokens[1];
			if (len > 2) target = tokens[2];
			if (len > 3) value = tokens[3];
			// 缺尾值就保持默认
			if (len > 4) facing = tokens[4];
			if (len > 5) strong = tokens[5];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new ControlI(type, builder.var(target), builder.var(value), builder.var(facing), builder.var(strong));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(type).append(' ').append(target).append(' ').append(sanitize(value));
			// 末尾值按属性写：power 两个（面、强充能），值设置一个（行号），过滤槽一个（面）
			if (!isPower() && !isValue() && !isFilter()) return;
			builder.append(' ').append(sanitize(facing));
			if (!isPower()) return;
			builder.append(' ').append(sanitize(strong));
		}
		/** @return 是不是在设红石输出，只有它认面与强充能那两个值。 */
		private boolean isPower() {
			return MLogSenseables.POWER.equals(type);
		}
		/** @return 是不是在改值设置，它认末尾那个行号。 */
		private boolean isValue() {
			return MLogSenseables.VALUE.equals(type);
		}
		/** @return 是不是在设过滤槽，它的值是个物品。 */
		private boolean isFilter() {
			return MLogSenseables.FILTER.equals(type);
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("name.token.mlog.set");
			builder.option(() -> type, v -> type = v, LAccess::controlAllowed, ControlStatement::display, FIELD_W, 3);
			builder.labelKey("name.token.mlog.of");
			builder.field(() -> target, v -> target = v, FIELD_W);
			builder.labelKey("name.token.mlog.to");
			if (isFilter()) valueField(builder);
			else builder.field(() -> value, v -> value = v, FIELD_W);
			// 换成别的属性时把参数区收回去；值留着，换回来还在
			if (!isPower() && !isValue() && !isFilter()) return;
			// 同一字段三种叫法：power 是接源的面，值设置是行号，过滤槽是过滤的面
			builder.labelKey(isValue() ? "name.token.mlog.row" : isPower() ? "name.token.mlog.facing" : "name.token.mlog.face");
			builder.field(() -> facing, v -> facing = v, FIELD_W);
			if (!isPower()) return;
			builder.labelKey("name.token.mlog.strong");
			builder.field(() -> strong, v -> strong = v, FIELD_W);
		}
		/** @return 属性字段显示用的文字：白名单里的属性走本地化，其余（自己敲的属性名）原样显示。 */
		private static String display(String value) {
			return LAccess.isControl(value) ? Component.translatable(LAccess.controlKey(value)).getString() : value;
		}
		/**
		 * 过滤槽那个字段：值为物品名，用物品图标墙选。
		 * <p>写进文本的是 {@code @命名空间:路径}，与 {@code sensor} 那两张墙一致
		 */
		private void valueField(Table builder) {
			builder.grouped(
				() -> value,
				v -> value = v,
				List.of(new OptionGroup("box", () -> SenseNames.ITEMS, 6)),
				ControlStatement::display,
				SELECT_W
			);
		}
		@Override
		public LCategory category() {
			return LCategory.block;
		}
	}
	/** {@code set result 0} */
	@RegisterStatement(id = SetStatement.ID, order = 90)
	public static class SetStatement extends MLogStatement {
		public static final String ID = "set";
		public String to = "result", from = "0";
		@Override
		public SetStatement parse(String[] tokens, int len) {
			if (len > 1) to = tokens[1];
			if (len > 2) from = tokens[2];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SetI(builder.var(from), builder.var(to));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(to).append(' ').append(sanitize(from));
		}
		@Override
		public void build(Table builder) {
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
	@RegisterStatement(id = OpStatement.ID, order = 100)
	public static class OpStatement extends MLogStatement {
		public static final String ID = "op";
		public LogicOp op = LogicOp.add;
		public String dest = "result", a = "a", b = "b";
		@Override
		public OpStatement parse(String[] tokens, int len) {
			if (len > 1) op = LogicOp.valueOf(tokens[1]);
			if (len > 2) dest = tokens[2];
			if (len > 3) a = tokens[3];
			if (len > 4) b = tokens[4];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new OpI(op, builder.var(a), builder.var(b), builder.var(dest));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ')
				.append(op.name())
				.append(' ')
				.append(dest)
				.append(' ')
				.append(sanitize(a))
				.append(' ')
				.append(sanitize(b));
		}
		@Override
		public void build(Table builder) {
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
		private void opSelect(Table builder) {
			// 算子是纯按钮，点开选项列表，不给手输的输入框
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
	/**
	 * {@code sensor result block1 @totalItems}：从建筑或单位读一个值。
	 * <p>末尾固定两位：序号与读取的面。序号给 {@code @slotItem} / {@code @slotFluid} 用；
	 * 面只给 Create 过滤槽用，{@code null} 表示不带面、0~5 取六个面。
	 */
	@RegisterStatement(id = SensorStatement.ID, order = 80)
	public static class SensorStatement extends MLogStatement {
		public static final String ID = "sensor";
		public String to = "result", from = "block1", type = "@totalItems";
		/** 只有 {@code @slotItem} / {@code @slotFluid} 用得上，缺省第 0 格。 */
		public String slot = "0";
		/** 从哪一面读：{@code null} 表示不带面，0~5 取六个面。 */
		public String facing = "null";
		@Override
		public SensorStatement parse(String[] tokens, int len) {
			if (len > 1) to = tokens[1];
			if (len > 2) from = tokens[2];
			if (len > 3) type = tokens[3];
			// 缺尾值就保持默认
			if (len > 4) slot = tokens[4];
			if (len > 5) facing = tokens[5];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SenseI(builder.var(from), builder.var(to), builder.var(type), builder.var(slot), builder.var(facing));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(to).append(' ').append(from).append(' ').append(type);
			// 序号与面固定两位，未用到则从后面省；要用面须保留序号位
			if (!"0".equals(slot) || !"null".equals(facing)) builder.append(' ').append(sanitize(slot));
			if (!"null".equals(facing)) builder.append(' ').append(sanitize(facing));
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> to, value -> to = value, FIELD_W);
			builder.label(" = ");
			// 三组：物品、液体、内置属性。前两组选出来的是要按名字读的方块内容，
			// 执行时当字符串属性名处理
			builder.grouped(
				() -> type, value -> type = value, List.of(
					// 物品与流体是六列一行的图标墙，属性一条占一行
					new OptionGroup("box", () -> SenseNames.ITEMS, 6),
					new OptionGroup("liquid", () -> SenseNames.FLUIDS, 6),
					new OptionGroup("tree", LAccess::names, 1)
				),
				// 内置属性有本地化名，物品/流体没有（它俩是纯图标，显示名只用于搜宽度和搜索）
				SensorStatement::display, SELECT_W
			);
			builder.labelKey("name.token.mlog.in");
			builder.field(() -> from, value -> from = value, FIELD_W);
			// 换成别的属性时把参数区收回去；值留着，换回来还在
			if (isSlot()) {
				builder.labelKey("name.token.mlog.slot");
				builder.field(() -> slot, value -> slot = value, FIELD_W);
			}
			if (!isSided()) return;
			builder.labelKey("name.token.mlog.face");
			builder.field(() -> facing, value -> facing = value, FIELD_W);
		}
		/** @return 属性字段显示用的文字：内置属性走本地化，其余（物品、流体、自定义属性名）原样显示。 */
		private static String display(String value) {
			var name = value.startsWith("@") ? value.substring(1) : value;
			return LAccess.byName(name) instanceof LAccess access ? Component.translatable(access.key()).getString() : value;
		}
		/** @return 是否读容器的某一格 / 某一罐，只有它们认序号 */
		private boolean isSlot() {
			return LAccess.usesSlot(LAccess.byName(access()));
		}
		/**
		 * @return 该属性是否按面分，界面据此决定是否显示「面」框
		 * 	<p>只有 Create 的过滤槽算：它能一个面存一份过滤。容器内容六面同一份，写了也是白写
		 */
		private boolean isSided() {
			return LAccess.byName(access()) == LAccess.filter;
		}
		/** @return 属性名去掉 {@code @} 后的样子 */
		private String access() {
			return type.startsWith("@") ? type.substring(1) : type;
		}
		@Override
		public LCategory category() {
			return LCategory.block;
		}
	}
	/**
	 * 可供 {@code sensor} 读取的物品与流体名，对应弹窗里那两张列表。
	 * <p>注册表上千条，惰性建一次就够——{@code OptionPopupScreen} 会缓存结果，
	 * 但类初始化本身也不该在服务端启动时白跑一遍。
	 * <p>放在语句类外面：过滤槽字段也用同一张物品墙。
	 */
	public static final class SenseNames {
		public static final List<String> ITEMS = BuiltInRegistries.ITEM.stream()
			.filter(item -> item != Items.AIR)
			.map(item -> "@" + BuiltInRegistries.ITEM.getKey(item))
			.toList();
		/**
		 * 空流体要滤掉：它没有静止贴图（{@code getStillTexture} 只有对 {@code Fluids.EMPTY}
		 * 才允许返回 null），列出来只会渲染成一个空按钮。
		 * <p>「流动的水」这类也要滤掉：它们和对应的源流体是两条注册项，却共用同一张贴图，
		 * 列出来只是同一项的重复。
		 */
		public static final List<String> FLUIDS = BuiltInRegistries.FLUID.stream()
			.filter(fluid -> fluid != Fluids.EMPTY)
			// getSource() 返回自己的是源流体，返回别人的才是「流动的 X」那种内部变体
			.filter(fluid -> !(fluid instanceof FlowingFluid flowing) || flowing.getSource() == fluid)
			.map(fluid -> "@" + BuiltInRegistries.FLUID.getKey(fluid))
			.toList();
	}
	/** {@code jump 5 notEqual x false}，跳转标签由 {@link LParser} 在解析期换成行号。 */
	@RegisterStatement(id = JumpStatement.ID, order = 180)
	public static class JumpStatement extends MLogStatement {
		public static final String ID = "jump";
		/** 编辑态的跳转目标。解析后由 {@link LParser} 回填，列表增删或重排后由画布重算。 */
		public @Nullable MLogStatement dest;
		public int destIndex;
		public ConditionOp op = ConditionOp.notEqual;
		public String value = "x", compare = "false";
		@Override
		public JumpStatement parse(String[] tokens, int len) {
			if (len > 1) destIndex = Integer.parseInt(tokens[1]);
			if (len > 2) op = ConditionOp.valueOf(tokens[2]);
			if (len > 3) value = tokens[3];
			if (len > 4) compare = tokens[4];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new JumpI(op, builder.var(value), builder.var(compare), destIndex);
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ')
				.append(destIndex)
				.append(' ')
				.append(op.name())
				.append(' ')
				.append(sanitize(value))
				.append(' ')
				.append(sanitize(compare));
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("name.token.mlog.if");
			if (op != ConditionOp.always) {
				builder.field(() -> value, v -> value = v, FIELD_W);
				conditionSelect(builder);
				builder.field(() -> compare, v -> compare = v, FIELD_W);
			} else conditionSelect(builder);
			builder.spacer();
			builder.node(() -> dest, target -> dest = target);
		}
		private void conditionSelect(Table builder) {
			// 条件是纯按钮，点开选项列表，不带输入框
			builder.option(
				() -> op.name(),
				v -> op = ConditionOp.valueOf(v),
				() -> ConditionOp.NAMES,
				name -> ConditionOp.valueOf(name).display(),
				op == ConditionOp.always ? OP_W_LONG : OP_W,
				// 条件列表三列排开
				3
			);
		}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
	}
	/** {@code print "hello"} */
	@RegisterStatement(id = PrintStatement.ID, order = 20)
	public static class PrintStatement extends MLogStatement {
		public static final String ID = "print";
		public String value = "\"frog\"";
		@Override
		public PrintStatement parse(String[] tokens, int len) {
			if (len > 1) value = tokens[1];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new PrintI(builder.var(value));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(value));
		}
		@Override
		public void build(Table builder) {
			// 打印的值占满整行，输入框和卡片同宽
			builder.field(() -> value, v -> value = v, Table.STRETCH);
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
	}
	/** {@code printchar 65}：往打印缓冲区里追加一个字符，值是字符码。 */
	@RegisterStatement(id = PrintCharStatement.ID, order = 30)
	public static class PrintCharStatement extends MLogStatement {
		public static final String ID = "printchar";
		/** 可挑的字符码：32~126。 */
		private static final List<String> CHAR_CODES = IntStream.rangeClosed(32, 126).mapToObj(String::valueOf).toList();
		public String value = "65";
		@Override
		public PrintCharStatement parse(String[] tokens, int len) {
			if (len > 1) value = tokens[1];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new PrintCharI(builder.var(value));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(value));
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("name.token.mlog.char");
			// 和「获取数据」同一个形状：输入框 + 铅笔按钮。icon 给 "char" 是让弹窗按固定 16×16 的格子铺，
			// 字符本身有宽有窄，格子才不会跟着参差不齐
			builder.grouped(
				() -> value,
				v -> value = v,
				List.of(new OptionGroup("char", () -> CHAR_CODES, 8)),
				PrintCharStatement::charText,
				FIELD_W
			);
		}
		/** @return 按钮与列表里显示的文字：能看的字符就直接显示，空格、控制字符和变量名原样显示。 */
		private static String charText(String value) {
			try {
				var code = Integer.parseInt(value);
				return code > 32 && code < 127 ? String.valueOf((char) code) : value;
			} catch (NumberFormatException e) {
				return value;
			}
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
	}
	/** {@code format "x = {0}"}：把打印缓冲区里的 {@code {N}} 占位符换成这个值。 */
	@RegisterStatement(id = FormatStatement.ID, order = 40)
	public static class FormatStatement extends MLogStatement {
		public static final String ID = "format";
		public String value = "\"frog\"";
		@Override
		public FormatStatement parse(String[] tokens, int len) {
			if (len > 1) value = tokens[1];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new FormatI(builder.var(value));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(value));
		}
		@Override
		public void build(Table builder) {
			// 和 print 一样占满整行
			builder.field(() -> value, v -> value = v, Table.STRETCH);
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
	}
	/** {@code printflush sign1}：把 {@code print} 的输出写进目标。 */
	@RegisterStatement(id = PrintFlushStatement.ID, order = 50)
	public static class PrintFlushStatement extends MLogStatement {
		public static final String ID = "printflush";
		public String target = "sign1";
		@Override
		public PrintFlushStatement parse(String[] tokens, int len) {
			if (len > 1) target = tokens[1];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new PrintFlushI(builder.var(target));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(target);
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("name.token.mlog.to");
			builder.field(() -> target, v -> target = v, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.block;
		}
	}
	/**
	 * {@code query circle unit 0 64 0 10}：在区域里查单位或建筑，结果写进 {@code @queries}。
	 * <p>比 Mindustry 多一个 {@code z}、少了 {@code team}（MC 没有队伍），所以文本格式和那边不互通。
	 * 圆形的 {@code x y z} 是球心、{@code w} 是半径；长方体的是最小角加三边，后三边只有长方体才写。
	 * <p><b>只给世界处理器用</b>。
	 */
	@RegisterStatement(id = QueryStatement.ID, order = 190)
	public static class QueryStatement extends MLogStatement {
		public static final String ID = "query";
		public QueryShape shape = QueryShape.circle;
		public QueryType type = QueryType.unit;
		public String x = "0", y = "0", z = "0", w = "10", h = "10", d = "10";
		@Override
		public QueryStatement parse(String[] tokens, int len) {
			if (len > 1) shape = QueryShape.valueOf(tokens[1]);
			if (len > 2) type = QueryType.valueOf(tokens[2]);
			if (len > 3) x = tokens[3];
			if (len > 4) y = tokens[4];
			if (len > 5) z = tokens[5];
			// 缺尾的值保持默认
			if (len > 6) w = tokens[6];
			if (len > 7) h = tokens[7];
			if (len > 8) d = tokens[8];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new QueryI(shape, type, builder.var(x), builder.var(y), builder.var(z), builder.var(w), builder.var(h), builder.var(d));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ')
				.append(shape.name())
				.append(' ')
				.append(type.name())
				.append(' ')
				.append(sanitize(x))
				.append(' ')
				.append(sanitize(y))
				.append(' ')
				.append(sanitize(z))
				.append(' ')
				.append(sanitize(w));
			// 圆形的 w 就是半径，没有后三边。和 control 的 power 一样，用不到就不写
			if (shape != QueryShape.rect) return;
			builder.append(' ').append(sanitize(h)).append(' ').append(sanitize(d));
		}
		@Override
		public void build(Table builder) {
			builder.option(
				() -> shape.name(),
				v -> shape = QueryShape.valueOf(v),
				() -> QueryShape.NAMES,
				name -> QueryShape.valueOf(name).display(),
				OP_W_LONG,
				2
			);
			builder.option(
				() -> type.name(),
				v -> type = QueryType.valueOf(v),
				() -> QueryType.NAMES,
				name -> QueryType.valueOf(name).display(),
				OP_W_LONG,
				2
			);
			builder.label("x");
			builder.field(() -> x, v -> x = v, FIELD_W);
			builder.label("y");
			builder.field(() -> y, v -> y = v, FIELD_W);
			builder.label("z");
			builder.field(() -> z, v -> z = v, FIELD_W);
			// 换成圆形就把宽高深收回去；值留着，换回长方体还在
			if (shape == QueryShape.circle) {
				builder.labelKey("name.token.mlog.radius");
				builder.field(() -> w, v -> w = v, FIELD_W);
				return;
			}
			builder.labelKey("name.token.mlog.width");
			builder.field(() -> w, v -> w = v, FIELD_W);
			builder.labelKey("name.token.mlog.height");
			builder.field(() -> h, v -> h = v, FIELD_W);
			builder.labelKey("name.token.mlog.depth");
			builder.field(() -> d, v -> d = v, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
	}
	/** {@code lookup item result 0}：按编号在注册表里查一项内容。 */
	@RegisterStatement(id = LookupStatement.ID, order = 140)
	public static class LookupStatement extends MLogStatement {
		public static final String ID = "lookup";
		public LookupType type = LookupType.item;
		public String result = "result", id = "0";
		@Override
		public LookupStatement parse(String[] tokens, int len) {
			if (len > 1) type = LookupType.valueOf(tokens[1]);
			if (len > 2) result = tokens[2];
			if (len > 3) id = tokens[3];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new LookupI(type, builder.var(result), builder.var(id));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(type.name()).append(' ').append(result).append(' ').append(sanitize(id));
		}
		@Override
		public void build(Table builder) {
			// 排版：结果 = 查询 [类型] # [编号]
			builder.field(() -> result, v -> result = v, FIELD_W);
			builder.labelKey("name.token.mlog.-lookup");
			builder.option(
				() -> type.name(),
				v -> type = LookupType.valueOf(v),
				() -> LookupType.NAMES,
				name -> LookupType.valueOf(name).display(),
				OP_W_LONG,
				2
			);
			builder.label(" # ");
			builder.field(() -> id, v -> id = v, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.operation;
		}
	}
}