package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.logic.LExecutor.*;
import io.github.forgestove.mlog.logic.Table.OptionGroup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.*;
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
			// 条件为纯按钮，点击展开选项列表，不带输入框
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
	/** {@code setrate}：改每 tick 执行的指令数，超出方块的速率时按速率封顶。 */
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
	 * <p>白名单仅约束非特权处理器：世界处理器可写任意属性，按名字扫描方块状态属性。
	 * <p>{@code power} 后固定跟两个值，按位置识别、不写关键字：
	 * {@code facing} 为接入源的面，0~5 取六个面、{@code null} 表示六面均接入；
	 * {@code strong} 用 0/1 决定是否同时施加强充能。
	 */
	@RegisterStatement(id = ControlStatement.ID, order = 70)
	public static class ControlStatement extends MLogStatement {
		public static final String ID = "control";
		public String type = "power", target = "block1", value = "15";
		/**
		 * 末尾的值按属性有两种读法：{@code power} 用作接入源的面，值设置一类用作行号。
		 */
		public String facing = "null", strong = "0";
		@Override
		public ControlStatement parse(String[] tokens, int len) {
			if (len > 1) type = tokens[1];
			if (len > 2) target = tokens[2];
			if (len > 3) value = tokens[3];
			// 缺尾值时保持默认
			if (len > 4) facing = tokens[4];
			if (len > 5) strong = tokens[5];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			// 末尾两个操作数仅在属性需要时给予变量，其余给占位常量：默认的 null 不应进入变量表
			return new ControlI(
				type,
				builder.var(target),
				builder.var(value),
				usesFacing() ? builder.var(facing) : builder.none(),
				isPower() ? builder.var(strong) : builder.none()
			);
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(type).append(' ').append(target).append(' ').append(sanitize(value));
			// 末尾值按属性写：power 两个（面、强充能），值设置一个（行号），过滤槽一个（面）
			if (!usesFacing()) return;
			builder.append(' ').append(sanitize(facing));
			if (!isPower()) return;
			builder.append(' ').append(sanitize(strong));
		}
		/** @return 末尾那个值这个属性用不用得到。 */
		private boolean usesFacing() {
			return isPower() || isValue() || isFilter();
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
			builder.option(() -> type, v -> type = v, LAccess::controlAllowed, ControlStatement::display, FIELD_W, 3, true);
			builder.labelKey("name.token.mlog.of");
			builder.field(() -> target, v -> target = v, FIELD_W);
			builder.labelKey("name.token.mlog.to");
			if (isFilter()) valueField(builder);
			else builder.field(() -> value, v -> value = v, FIELD_W);
			// 切换为其他属性时收起参数区；值予以保留，切回后仍然有效
			if (!isPower() && !isValue() && !isFilter()) return;
			// 同一字段的三种名称：power 为接入源的面，值设置为行号，过滤槽为过滤的面
			builder.labelKey(isValue() ? "name.token.mlog.row" : isPower() ? "name.token.mlog.facing" : "name.token.mlog.face");
			builder.field(() -> facing, v -> facing = v, FIELD_W);
			if (!isPower()) return;
			builder.labelKey("name.token.mlog.strong");
			builder.field(() -> strong, v -> strong = v, FIELD_W);
		}
		/** @return 属性字段显示用的文字：白名单内的属性走本地化，其余（手动输入的属性名）原样显示。 */
		private static String display(String value) {
			return LAccess.isControl(value) ? Component.translatable(LAccess.controlKey(value)).getString() : value;
		}
		/**
		 * 过滤槽字段：值为物品名，用物品图标墙选择。
		 * <p>写入文本的是 {@code @命名空间:路径}，与 {@code sensor} 的图标墙一致
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
			// 一元运算不取第二个操作数：给占位常量，未使用的 b 不应进入变量表
			return new OpI(op, builder.var(a), op.unary ? builder.none() : builder.var(b), builder.var(dest));
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
			// 算子为纯按钮，点击展开选项列表，不提供手动输入框
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
			// 缺尾值时保持默认
			if (len > 4) slot = tokens[4];
			if (len > 5) facing = tokens[5];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			// 面未使用时给占位常量：默认的 null 不应进入变量表
			return new SenseI(
				builder.var(from),
				builder.var(to),
				builder.var(type),
				builder.var(slot),
				"null".equals(facing) ? builder.none() : builder.var(facing)
			);
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(to).append(' ').append(from).append(' ').append(type);
			// 序号与面固定两位，未使用时自尾部省略；使用面时须保留序号位
			if (!"0".equals(slot) || !"null".equals(facing)) builder.append(' ').append(sanitize(slot));
			if (!"null".equals(facing)) builder.append(' ').append(sanitize(facing));
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> to, value -> to = value, FIELD_W);
			builder.label(" = ");
			// 三组：物品、液体、内置属性。前两组选中项为按名字读取的方块内容，
			// 执行时按字符串属性名处理
			builder.grouped(
				() -> type, value -> type = value, List.of(
					// 物品与流体为六列一行的图标墙，属性每行一条
					new OptionGroup("box", () -> SenseNames.ITEMS, 6),
					new OptionGroup("liquid", () -> SenseNames.FLUIDS, 6),
					new OptionGroup("tree", LAccess::names, 1)
				),
				// 内置属性有本地化名，物品/流体没有（二者为纯图标，显示名仅用于计算宽度与搜索）
				SensorStatement::display, SELECT_W
			);
			builder.labelKey("name.token.mlog.in");
			builder.field(() -> from, value -> from = value, FIELD_W);
			// 切换为其他属性时收起参数区；值予以保留，切回后仍然有效
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
		 * @return 该属性是否按面区分，界面据此决定是否显示「面」字段
		 * 	<p>仅 Create 的过滤槽如此：每个面可各存一份过滤。容器内容六个面相同，写入无效
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
	 * 可供 {@code sensor} 读取的物品与流体名，对应弹窗中的两组列表。
	 * <p>注册表条目上千，惰性构建一次即可——{@code OptionPopupScreen} 会缓存结果，
	 * 但类初始化本身也不应在服务端启动时空跑一遍。
	 * <p>置于语句类之外：过滤槽字段也使用同一张物品墙。
	 */
	public static final class SenseNames {
		public static final List<String> ITEMS = BuiltInRegistries.ITEM.stream()
			.filter(item -> item != Items.AIR)
			.map(item -> "@" + BuiltInRegistries.ITEM.getKey(item))
			.toList();
		/**
		 * 空流体须滤除：它没有静止贴图（{@code getStillTexture} 仅对 {@code Fluids.EMPTY}
		 * 允许返回 null），列出后只会渲染为空按钮。
		 * <p>「流动的水」一类也须滤除：它们与对应的源流体是两条注册项，却共用同一张贴图，
		 * 列出后只是重复项。
		 */
		public static final List<String> FLUIDS = BuiltInRegistries.FLUID.stream()
			.filter(fluid -> fluid != Fluids.EMPTY)
			// getSource() 返回自身的为源流体，返回他者的则为「流动的 X」内部变体
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
			// always 不取操作数：给占位常量，默认的 x/false 不应进入变量表
			var always = op == ConditionOp.always;
			var none = builder.none();
			return new JumpI(op, always ? none : builder.var(value), always ? none : builder.var(compare), destIndex);
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
			// 条件为纯按钮，点击展开选项列表，不带输入框
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
			// 打印的值占满整行，输入框与卡片同宽
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
			// 与「获取数据」同形：输入框 + 铅笔按钮。icon 指定 "char" 使弹窗按固定 16×16 的格子排列，
			// 字符本身有宽有窄，格子因此不会参差不齐
			builder.grouped(
				() -> value,
				v -> value = v,
				List.of(new OptionGroup("char", () -> CHAR_CODES, 8)),
				PrintCharStatement::charText,
				FIELD_W
			);
		}
		/** @return 按钮与列表显示的文字：可显示的字符直接显示，空格、控制字符与变量名原样显示。 */
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
			// 与 print 一样占满整行
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
	 * <p>相比 Mindustry 多一个 {@code z}、少一个 {@code team}（无队伍概念），文本格式因此不互通。
	 * 圆形的 {@code x y z} 为球心、{@code w} 为半径；长方形的为最小角加三边，后三边仅在长方形时写入。
	 * <p>仅世界处理器可用。
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
			// 圆形的 w 即半径，无后三边。与 control 的 power 一致，未使用时省略
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
			// 切换为圆形时收起宽高深；值予以保留，切回长方形后仍然有效
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
	/** {@code draw <类型> …}：往绘图缓冲区追加一条绘制命令，由 {@code drawflush} 成批送到显示屏。 */
	@RegisterStatement(id = DrawStatement.ID, order = 55)
	public static class DrawStatement extends MLogStatement {
		public static final String ID = "draw";
		public GraphicsType type = GraphicsType.clear;
		public String x = "0", y = "0", p1 = "0", p2 = "0", p3 = "0", p4 = "0";
		/** {@code print} 的对齐方式，与 {@code p1} 共用同一个操作数。 */
		public DrawAlign align = DrawAlign.bottomLeft;
		@Override
		public DrawStatement parse(String[] tokens, int len) {
			if (len > 1) type = GraphicsType.valueOf(tokens[1]);
			if (len > 2) x = tokens[2];
			if (len > 3) y = tokens[3];
			if (len > 4) select(tokens[4]);
			if (len > 5) p2 = tokens[5];
			if (len > 6) p3 = tokens[6];
			if (len > 7) p4 = tokens[7];
			return this;
		}
		/** 第四个操作数按类型解释：{@code print} 的对齐是名字，其余类型是数值。 */
		private void select(String token) {
			// MDT 文本中对其带 {@code @} 前缀（在其汇编器中为常量名），一并接受
			if (type == GraphicsType.print) align = DrawAlign.valueOf(token.startsWith("@") ? token.substring(1) : token);
			else p1 = token;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			var first = type == GraphicsType.print ? builder.var(Integer.toString(align.ordinal())) : builder.var(p1);
			return new DrawI(type, builder.var(x), builder.var(y), first, builder.var(p2), builder.var(p3), builder.var(p4));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(type.name());
			builder.append(' ').append(sanitize(x)).append(' ').append(sanitize(y));
			builder.append(' ').append(sanitize(type == GraphicsType.print ? align.name() : p1));
			builder.append(' ').append(sanitize(p2)).append(' ').append(sanitize(p3)).append(' ').append(sanitize(p4));
		}
		@Override
		public void build(Table builder) {
			builder.option(
				() -> type.name(),
				this::setType,
				() -> GraphicsType.NAMES,
				name -> GraphicsType.valueOf(name).display(),
				OP_W_LONG,
				2
			);
			switch (type) {
				case clear -> {
					fields(builder, "r", () -> x, v -> x = v);
					fields(builder, "g", () -> y, v -> y = v);
					fields(builder, "b", () -> p1, v -> p1 = v);
				}
				case color -> {
					fields(builder, "r", () -> x, v -> x = v);
					fields(builder, "g", () -> y, v -> y = v);
					fields(builder, "b", () -> p1, v -> p1 = v);
					fields(builder, "a", () -> p2, v -> p2 = v);
				}
				case col -> {
					builder.labelKey("name.token.mlog.color");
					builder.color(() -> x, v -> x = v, FIELD_W);
				}
				case stroke -> fields(builder, null, () -> x, v -> x = v);
				case line -> {
					fields(builder, "x", () -> x, v -> x = v);
					fields(builder, "y", () -> y, v -> y = v);
					fields(builder, "x2", () -> p1, v -> p1 = v);
					fields(builder, "y2", () -> p2, v -> p2 = v);
				}
				case rect, lineRect -> {
					fields(builder, "x", () -> x, v -> x = v);
					fields(builder, "y", () -> y, v -> y = v);
					fields(builder, "name.token.mlog.width", () -> p1, v -> p1 = v);
					fields(builder, "name.token.mlog.height", () -> p2, v -> p2 = v);
				}
				case poly, linePoly -> {
					fields(builder, "x", () -> x, v -> x = v);
					fields(builder, "y", () -> y, v -> y = v);
					fields(builder, "name.token.mlog.sides", () -> p1, v -> p1 = v);
					fields(builder, "name.token.mlog.radius", () -> p2, v -> p2 = v);
					fields(builder, "name.token.mlog.rotation", () -> p3, v -> p3 = v);
				}
				case triangle -> {
					fields(builder, "x", () -> x, v -> x = v);
					fields(builder, "y", () -> y, v -> y = v);
					fields(builder, "x2", () -> p1, v -> p1 = v);
					fields(builder, "y2", () -> p2, v -> p2 = v);
					fields(builder, "x3", () -> p3, v -> p3 = v);
					fields(builder, "y3", () -> p4, v -> p4 = v);
				}
				case image -> {
					fields(builder, "x", () -> x, v -> x = v);
					fields(builder, "y", () -> y, v -> y = v);
					fields(builder, "name.token.mlog.image", () -> p1, v -> p1 = v);
					fields(builder, "name.token.mlog.size", () -> p2, v -> p2 = v);
					fields(builder, "name.token.mlog.rotation", () -> p3, v -> p3 = v);
				}
				case print -> {
					fields(builder, "x", () -> x, v -> x = v);
					fields(builder, "y", () -> y, v -> y = v);
					builder.labelKey("name.token.mlog.align");
					builder.option(
						() -> align.name(),
						v -> align = DrawAlign.valueOf(v),
						() -> DrawAlign.NAMES,
						name -> DrawAlign.valueOf(name).display(),
						OP_W_LONG,
						3
					);
				}
				case translate, scale -> {
					fields(builder, "x", () -> x, v -> x = v);
					fields(builder, "y", () -> y, v -> y = v);
				}
				case rotate -> fields(builder, "name.token.mlog.angle", () -> p1, v -> p1 = v);
				case reset -> {}
			}
		}
		/** 切换类型时补齐该类型必须有默认值的字段，否则新类型初始无法绘制。 */
		private void setType(String name) {
			type = GraphicsType.valueOf(name);
			switch (type) {
				// 颜色分量全 0 等于全透明
				case color -> p2 = "255";
				case col -> x = "%ffffffff";
				case image -> {
					p1 = "@stone";
					p2 = "32";
					p3 = "0";
				}
				default -> {}
			}
		}
		/** 铺开一对「标签 + 输入框」；标签为 lang key 时带 {@code key.} 前缀，为 {@code null} 时仅放输入框。 */
		private static void fields(Table builder, @Nullable String label, Supplier<String> get, Consumer<String> set) {
			if (label != null) addLabel(builder, label);
			builder.field(get, set, FIELD_W);
		}
		private static void addLabel(Table builder, String label) {
			if (label.startsWith("name.token.")) builder.labelKey(label);
			else builder.label(label);
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
	}
	/** {@code drawflush <目标>}：把绘图缓冲区里的命令送到显示屏。 */
	@RegisterStatement(id = DrawFlushStatement.ID, order = 56)
	public static class DrawFlushStatement extends MLogStatement {
		public static final String ID = "drawflush";
		public String target = "display1";
		@Override
		public DrawFlushStatement parse(String[] tokens, int len) {
			if (len > 1) target = tokens[1];
			return this;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new DrawFlushI(builder.var(target));
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
}