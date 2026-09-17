package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.logic.LExecutor.*;
import io.github.forgestove.mlog.logic.LayoutBuilder.OptionGroup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.*;
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
		PrintStatement::new,
		EndStatement::new,
		StopStatement::new,
		WaitStatement::new,
		SetRateStatement::new,
		GetLinkStatement::new,
		ControlStatement::new,
		SelectStatement::new,
		PackColorStatement::new,
		UnpackColorStatement::new
	);
	/**
	 * 界面宽度基准。字段按 Mindustry 的 180 折算到 MC 的字体尺度。
	 * <p>条件和算子按钮都是纯按钮：{@code OP_W} 放运算符，{@code OP_W_LONG} 放本地化之后的词
	 * （「不等于」「异或」这类比符号宽）。
	 */
	private static final int FIELD_W = 70, OP_W = 30, OP_W_LONG = 36;
	/**
	 * 获取数据的属性字段宽度。比 Mindustry 那边宽出一截：它的属性名是 {@code @copper} 这种，
	 * 这边还要带上 {@code minecraft:} 的命名空间。再宽下去，卡片一行就放不下
	 * 「结果 = 属性 于 方块」了。
	 */
	private static final int SELECT_W = 120;
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
	/** {@code select result lessThan a b c d}：条件成立取 c，否则取 d。 */
	public static class SelectStatement extends LStatement {
		public String result = "result", comp0 = "x", comp1 = "false", yes = "a", no = "b";
		public ConditionOp op = ConditionOp.notEqual;
		public static SelectStatement parse(String[] tokens, int len) {
			var s = new SelectStatement();
			if (len > 1) s.result = tokens[1];
			if (len > 2) s.op = ConditionOp.valueOf(tokens[2]);
			if (len > 3) s.comp0 = tokens[3];
			if (len > 4) s.comp1 = tokens[4];
			if (len > 5) s.yes = tokens[5];
			if (len > 6) s.no = tokens[6];
			return s;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SelectI(op, builder.var(result), builder.var(comp0), builder.var(comp1), builder.var(yes), builder.var(no));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("select ")
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
		public void buildParams(LayoutBuilder builder) {
			builder.field(() -> result, v -> result = v, FIELD_W);
			builder.label(" = ");
			builder.labelKey("name.token.mlog.if");
			builder.field(() -> comp0, v -> comp0 = v, FIELD_W);
			// 对齐 Mindustry：条件是纯按钮，点开选项列表，不带输入框
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
	public static class PackColorStatement extends LStatement {
		public String result = "result", r = "1", g = "0", b = "0", a = "1";
		public static PackColorStatement parse(String[] tokens, int len) {
			var s = new PackColorStatement();
			if (len > 1) s.result = tokens[1];
			if (len > 2) s.r = tokens[2];
			if (len > 3) s.g = tokens[3];
			if (len > 4) s.b = tokens[4];
			if (len > 5) s.a = tokens[5];
			return s;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new PackColorI(builder.var(result), builder.var(r), builder.var(g), builder.var(b), builder.var(a));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("packcolor ")
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
		public void buildParams(LayoutBuilder builder) {
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
	public static class UnpackColorStatement extends LStatement {
		public String r = "r", g = "g", b = "b", a = "a", value = "color";
		public static UnpackColorStatement parse(String[] tokens, int len) {
			var s = new UnpackColorStatement();
			if (len > 1) s.r = tokens[1];
			if (len > 2) s.g = tokens[2];
			if (len > 3) s.b = tokens[3];
			if (len > 4) s.a = tokens[4];
			if (len > 5) s.value = tokens[5];
			return s;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new UnpackColorI(builder.var(r), builder.var(g), builder.var(b), builder.var(a), builder.var(value));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("unpackcolor ")
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
		public void buildParams(LayoutBuilder builder) {
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
	public static class EndStatement extends LStatement {
		public static EndStatement parse() {
			return new EndStatement();
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new EndI();
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("end");
		}
		@Override
		public void buildParams(LayoutBuilder builder) {}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
	}
	/** {@code stop}：停在这里，不再往下走。 */
	public static class StopStatement extends LStatement {
		public static StopStatement parse() {
			return new StopStatement();
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new StopI(builder.index);
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("stop");
		}
		@Override
		public void buildParams(LayoutBuilder builder) {}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
	}
	/** {@code wait 0.5}：等够指定秒数再往下走。 */
	public static class WaitStatement extends LStatement {
		public String value = "0.5";
		public static WaitStatement parse(String[] tokens, int len) {
			var s = new WaitStatement();
			if (len > 1) s.value = tokens[1];
			return s;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new WaitI(builder.var(value), builder.index);
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("wait ").append(sanitize(value));
		}
		@Override
		public void buildParams(LayoutBuilder builder) {
			builder.field(() -> value, v -> value = v, FIELD_W);
			builder.labelKey("instruction.mlog.wait.unit");
		}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
	}
	/** {@code setrate}：改每 tick 执行的指令数，超出方块的速率就按速率封顶。 */
	public static class SetRateStatement extends LStatement {
		public String amount = "6";
		public static SetRateStatement parse(String[] tokens, int len) {
			var s = new SetRateStatement();
			if (len > 1) s.amount = tokens[1];
			return s;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SetRateI(builder.var(amount));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("setrate ").append(sanitize(amount));
		}
		@Override
		public void buildParams(LayoutBuilder builder) {
			builder.label("ipt = ");
			builder.field(() -> amount, v -> amount = v, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
	}
	/** {@code getlink result 0}：按序号取一条链接。 */
	public static class GetLinkStatement extends LStatement {
		public String output = "result", address = "0";
		public static GetLinkStatement parse(String[] tokens, int len) {
			var s = new GetLinkStatement();
			if (len > 1) s.output = tokens[1];
			if (len > 2) s.address = tokens[2];
			return s;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new GetLinkI(builder.var(output), builder.var(address));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("getlink ").append(output).append(' ').append(sanitize(address));
		}
		@Override
		public void buildParams(LayoutBuilder builder) {
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
	/** {@code control open block1 1}：控制建筑的状态，可写的属性见 {@link LAccess#CONTROLS}。 */
	public static class ControlStatement extends LStatement {
		public String type = "open", target = "block1", value = "1";
		public static ControlStatement parse(String[] tokens, int len) {
			var s = new ControlStatement();
			if (len > 1) s.type = tokens[1];
			if (len > 2) s.target = tokens[2];
			if (len > 3) s.value = tokens[3];
			return s;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new ControlI(type, builder.var(target), builder.var(value));
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append("control ").append(type).append(' ').append(target).append(' ').append(sanitize(value));
		}
		@Override
		public void buildParams(LayoutBuilder builder) {
			builder.labelKey("name.token.mlog.set");
			builder.option(() -> type, v -> type = v, () -> LAccess.CONTROLS, null, FIELD_W, 1);
			builder.labelKey("name.token.mlog.of");
			builder.field(() -> target, v -> target = v, FIELD_W);
			builder.field(() -> value, v -> value = v, FIELD_W);
		}
		@Override
		public LCategory category() {
			return LCategory.block;
		}
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
			// 三组：物品、液体、内置属性。对齐 Mindustry 的 showSelectTable，
			// 前两组选出来的是要按名字读的方块内容，执行时当字符串属性名处理
			builder.grouped(
				() -> type, value -> type = value, List.of(
					// 物品与流体是六列一行的图标墙，属性一条占一行
					new OptionGroup("box", () -> SenseNames.ITEMS, 6),
					new OptionGroup("liquid", () -> SenseNames.FLUIDS, 6),
					new OptionGroup("tree", () -> LAccess.NAMES, 1)
				),
				// 内置属性有本地化名，物品/流体没有（它俩是纯图标，显示名只用于搜宽度和搜索）
				SensorStatement::display, SELECT_W
			);
			builder.labelKey("name.token.mlog.in");
			builder.field(() -> from, value -> from = value, FIELD_W);
		}
		/** @return 属性字段显示用的文字：内置属性走本地化，其余（物品、流体、自定义属性名）原样显示。 */
		private static String display(String value) {
			var name = value.startsWith("@") ? value.substring(1) : value;
			return LAccess.byName(name) instanceof LAccess access ? Component.translatable(access.key()).getString() : value;
		}
		@Override
		public LCategory category() {
			return LCategory.block;
		}
		/**
		 * 可供 {@code sensor} 读取的物品与流体名，对应 Mindustry 弹窗里那两张列表。
		 * <p>注册表上千条，惰性建一次就够——{@code OptionPopupScreen} 会缓存结果，
		 * 但类初始化本身也不该在服务端启动时白跑一遍。
		 */
		private static final class SenseNames {
			static final List<String> ITEMS = BuiltInRegistries.ITEM.stream()
				.filter(item -> item != Items.AIR)
				.map(item -> "@" + BuiltInRegistries.ITEM.getKey(item))
				.toList();
			/**
			 * 空流体要滤掉：它没有静止贴图（{@code getStillTexture} 只有对 {@code Fluids.EMPTY}
			 * 才允许返回 null），列出来只会渲染成一个空按钮。
			 * <p>「流动的水」这类也要滤掉：它们和对应的源流体是两条注册项，却共用同一张贴图，
			 * 列出来只是同一项的重复。
			 */
			static final List<String> FLUIDS = BuiltInRegistries.FLUID.stream()
				.filter(fluid -> fluid != Fluids.EMPTY)
				// getSource() 返回自己的是源流体，返回别人的才是「流动的 X」那种内部变体
				.filter(fluid -> !(fluid instanceof FlowingFluid flowing) || flowing.getSource() == fluid)
				.map(fluid -> "@" + BuiltInRegistries.FLUID.getKey(fluid))
				.toList();
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
