package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.logic.LExecutor.*;
import io.github.forgestove.mlog.logic.Table.OptionGroup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;
/** 语句类由 {@link RegisterStatement} 注解扫描发现。 */
public class LStatements {
	private static final int FIELD_W = 70, OP_W = 30, OP_W_LONG = 36;
	private static final int SELECT_W = 120;
	/** 声音与音乐名带命名空间，编辑框比普通列表放宽。 */
	private static final int SELECT_W_LONG = 150;
	/** 天气取值，供 {@code weathersense} 与 {@code weatherset} 使用。 */
	private static final List<String> WEATHERS = List.of("@clear", "@rain", "@thunder");
	private static String weatherDisplay(String value) {
		return Component.translatable("mlog.weather." + (value.startsWith("@") ? value.substring(1) : value)).getString();
	}
	/** 天气名不是注册项，按名字做成字符串常量。 */
	private static LVar weatherVar(LAssembler builder, String symbol) {
		return WEATHERS.contains(symbol) ? builder.putConst("___" + symbol, symbol.substring(1)) : builder.var(symbol);
	}
	/** 解析失败或未实现的语句占位；写出为 {@code noop} 以保留行号，读取由 {@link LParser} 单独识别。 */
	public static class InvalidStatement extends MLogStatement {
		public static final String ID = "noop";
		@Override
		public LInstruction build(LAssembler builder) {
			return new NoopI();
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID);
		}
		@Override
		public void build(Table builder) {}
	}
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
		public void build(Table builder) {
			builder.field(() -> result, v -> result = v, FIELD_W);
			builder.label(" = ");
			builder.labelKey("mlog.token.if");
			builder.field(() -> comp0, v -> comp0 = v, FIELD_W);
			// 条件为纯按钮，点击展开选项列表，不带输入框
			builder.option(
				() -> op.name(),
				v -> op = ConditionOp.valueOf(v),
				() -> ConditionOp.NAMES,
				name -> ConditionOp.valueOf(name).display(),
				name -> Tipped.tipKeyOf(ConditionOp.class, name),
				OP_W,
				3,
				false
			);
			builder.field(() -> comp1, v -> comp1 = v, FIELD_W);
			builder.labelKey("mlog.token.then");
			builder.field(() -> yes, v -> yes = v, FIELD_W);
			builder.labelKey("mlog.token.else");
			builder.field(() -> no, v -> no = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SelectI(op, builder.var(result), builder.var(comp0), builder.var(comp1), builder.var(yes), builder.var(no));
		}
		@Override
		public LCategory category() {
			return LCategory.operation;
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
		public void build(Table builder) {
			builder.field(() -> result, v -> result = v, FIELD_W);
			builder.label(" = ");
			builder.labelKey("mlog.token.pack");
			builder.field(() -> r, v -> r = v, FIELD_W);
			builder.field(() -> g, v -> g = v, FIELD_W);
			builder.field(() -> b, v -> b = v, FIELD_W);
			builder.field(() -> a, v -> a = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new PackColorI(builder.var(result), builder.var(r), builder.var(g), builder.var(b), builder.var(a));
		}
		@Override
		public LCategory category() {
			return LCategory.operation;
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
		public void build(Table builder) {
			builder.field(() -> r, v -> r = v, FIELD_W);
			builder.field(() -> g, v -> g = v, FIELD_W);
			builder.field(() -> b, v -> b = v, FIELD_W);
			builder.field(() -> a, v -> a = v, FIELD_W);
			builder.label(" = ");
			builder.labelKey("mlog.token.unpack");
			builder.field(() -> value, v -> value = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new UnpackColorI(builder.var(r), builder.var(g), builder.var(b), builder.var(a), builder.var(value));
		}
		@Override
		public LCategory category() {
			return LCategory.operation;
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
	}
	/** {@code end}：本 tick 余下的指令不再执行。 */
	@RegisterStatement(id = EndStatement.ID, order = 170)
	public static class EndStatement extends MLogStatement {
		public static final String ID = "end";
		@Override
		public LInstruction build(LAssembler builder) {
			return new EndI();
		}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID);
		}
		@Override
		public void build(Table builder) {}
	}
	@RegisterStatement(id = StopStatement.ID, order = 130)
	public static class StopStatement extends MLogStatement {
		public static final String ID = "stop";
		@Override
		public LInstruction build(LAssembler builder) {
			return new StopI(builder.index);
		}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID);
		}
		@Override
		public void build(Table builder) {}
	}
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
		public void build(Table builder) {
			builder.field(() -> value, v -> value = v, FIELD_W);
			builder.labelKey("mlog.token.seconds");
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new WaitI(builder.var(value), builder.index);
		}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(value));
		}
	}
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
		public void build(Table builder) {
			builder.label("ipt = ");
			builder.field(() -> amount, v -> amount = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SetRateI(builder.var(amount));
		}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(amount));
		}
	}
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
		public void build(Table builder) {
			builder.field(() -> output, v -> output = v, FIELD_W);
			builder.label(" = ");
			builder.labelKey("mlog.token.link");
			builder.field(() -> address, v -> address = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new GetLinkI(builder.var(output), builder.var(address));
		}
		@Override
		public LCategory category() {
			return LCategory.block;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(output).append(' ').append(sanitize(address));
		}
	}
	/** {@code read result cell1 0}：从目标读一个值；地址为名字时读目标处理器的同名变量，为数字时按序号取链接。 */
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
		public void build(Table builder) {
			builder.field(() -> output, v -> output = v, FIELD_W);
			builder.label(" = ");
			builder.field(() -> target, v -> target = v, FIELD_W);
			builder.labelKey("mlog.token.at");
			builder.field(() -> address, v -> address = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new ReadI(builder.var(target), builder.var(address), builder.var(output));
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(output).append(' ').append(target).append(' ').append(sanitize(address));
		}
	}
	/** {@code write result cell1 0}：把值写进目标；地址按名字取变量，数字位置留给内存方块。 */
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
		public void build(Table builder) {
			builder.field(() -> input, v -> input = v, FIELD_W);
			builder.labelKey("mlog.token.to");
			builder.field(() -> target, v -> target = v, FIELD_W);
			builder.labelKey("mlog.token.at");
			builder.field(() -> address, v -> address = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new WriteI(builder.var(target), builder.var(address), builder.var(input));
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(input).append(' ').append(target).append(' ').append(sanitize(address));
		}
	}
	/** {@code control open block1 1}：控制建筑状态；可写的方块状态属性由 {@link LAccess#controlAllowed()} 白名单界定，非特权处理器受其约束。 */
	@RegisterStatement(id = ControlStatement.ID, order = 70)
	public static class ControlStatement extends MLogStatement {
		public static final String ID = "control";
		public String type = "redstone", target = "block1", value = "15";
		/** 末尾的值按属性有两种读法：{@code redstone} 用作接入源的面，值设置用作行号。 */
		public String facing = "null";
		private static String display(String value) {
			if (!LAccess.isControl(value)) return value;
			var key = LAccess.controlKey(value);
			// 方块状态属性没有译名时直接显示原名，露出 key 反而更难认
			return Language.getInstance().has(key) ? Component.translatable(key).getString() : value;
		}
		@Override
		public ControlStatement parse(String[] tokens, int len) {
			if (len > 1) type = tokens[1];
			if (len > 2) target = tokens[2];
			if (len > 3) value = tokens[3];
			if (len > 4) facing = tokens[4];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.set");
			// 属性只能从列表选，用整块按钮而非输入框加铅笔
			builder.groupedOption(() -> type, v -> type = v, SenseNames.CONTROL_GROUPS, ControlStatement::display, SELECT_W_LONG, true, false);
			builder.labelKey("mlog.token.of");
			builder.field(() -> target, v -> target = v, FIELD_W);
			builder.labelKey("mlog.token.to");
			if (isFilter()) valueField(builder);
			else builder.field(() -> value, v -> value = v, FIELD_W);
			// 切换为其他属性时收起参数区，值保留，切回后仍然有效
			if (!isRedstone() && !isValue() && !isFilter()) return;
			// 同一字段的三种名称：redstone 为接入源的面，值设置为行号，过滤槽为过滤的面
			builder.labelKey(isValue() ? "mlog.token.row" : isRedstone() ? "mlog.token.facing" : "mlog.token.face");
			builder.field(() -> facing, v -> facing = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			// 末尾操作数仅在属性需要时给变量，其余给占位常量：默认的 null 不应进入变量表
			return new ControlI(type, builder.var(target), builder.var(value), usesFacing() ? builder.var(facing) : builder.none());
		}
		@Override
		public LCategory category() {
			return LCategory.block;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(type).append(' ').append(target).append(' ').append(sanitize(value));
			// 末尾值按属性写：值设置为行号，其余为面
			if (!usesFacing()) return;
			builder.append(' ').append(sanitize(facing));
		}
		private boolean usesFacing() {
			return isRedstone() || isValue() || isFilter();
		}
		private boolean isRedstone() {
			return MLogSenseables.REDSTONE.equals(type);
		}
		private boolean isValue() {
			return MLogSenseables.VALUE.equals(type);
		}
		private boolean isFilter() {
			return MLogSenseables.FILTER.equals(type);
		}
		/** 过滤槽字段的值为物品名，用物品图标墙选择；写入文本的是 {@code @命名空间:路径}。 */
		private void valueField(Table builder) {
			builder.grouped(
				() -> value,
				v -> value = v,
				List.of(new OptionGroup("box", () -> SenseNames.ITEMS, 6)),
				ControlStatement::display,
				SELECT_W
			);
		}
	}
	/** {@code setprop <属性> <目标> <值>}：写入单位属性或容器内容，属性名与 {@code sensor} 相同。 */
	@RegisterStatement(id = SetPropStatement.ID, order = 310)
	public static class SetPropStatement extends MLogStatement {
		public static final String ID = "setprop";
		/** 可写属性；目标不支持时执行期忽略。 */
		private static final List<String> PROPERTIES = List.of(
			"@x", "@y", "@z", "@velocityX", "@velocityY", "@velocityZ", "@health", "@armor", "@flag"
		);
		public String type = "@x", of = "block1", value = "0";
		private static String display(String value) {
			if (!value.startsWith("@")) return value;
			var name = value.substring(1);
			var id = ResourceLocation.tryParse(name);
			var effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id);
			if (effect != null) return effect.getDisplayName().getString();
			if (LAccess.byName(name) instanceof LAccess access)
				return access.blockProperty() ? name : Component.translatable(access.key()).getString();
			return SenseNames.display(value);
		}
		@Override
		public SetPropStatement parse(String[] tokens, int len) {
			if (len > 1) type = tokens[1];
			if (len > 2) of = tokens[2];
			if (len > 3) value = tokens[3];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.set");
			builder.grouped(
				() -> type,
				v -> type = v,
				// 图标顺序：物品、流体、状态效果、属性
				List.of(
					new OptionGroup("box", () -> SenseNames.ITEMS, 6),
					new OptionGroup("liquid", () -> SenseNames.FLUIDS, 6),
					new OptionGroup("effect", () -> SenseNames.EFFECTS, 6),
					new OptionGroup("tree", () -> PROPERTIES, 3)
				),
				SetPropStatement::display,
				SELECT_W
			);
			builder.labelKey("mlog.token.of");
			builder.field(() -> of, v -> of = v, FIELD_W);
			builder.labelKey("mlog.token.to");
			builder.field(() -> value, v -> value = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SetPropI(builder.namedVar(type, BuiltInRegistries.MOB_EFFECT), builder.var(of), builder.var(value));
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(type).append(' ').append(of).append(' ').append(sanitize(value));
		}
	}
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
		public void build(Table builder) {
			builder.field(() -> to, value -> to = value, FIELD_W);
			builder.label(" = ");
			builder.field(() -> from, value -> from = value, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SetI(builder.var(from), builder.var(to));
		}
		@Override
		public LCategory category() {
			return LCategory.operation;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(to).append(' ').append(sanitize(from));
		}
	}
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
		@Override
		public LInstruction build(LAssembler builder) {
			// 一元运算不取第二个操作数：给占位常量，未使用的 b 不应进入变量表
			return new OpI(op, builder.var(a), op.unary ? builder.none() : builder.var(b), builder.var(dest));
		}
		@Override
		public LCategory category() {
			return LCategory.operation;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ')
				.append(op.name())
				.append(' ')
				.append(dest)
				.append(' ')
				.append(sanitize(a));
			if (!op.unary) builder.append(' ').append(sanitize(b));
		}
		private void opSelect(Table builder) {
			// 算子为纯按钮，点击展开选项列表，不提供手动输入框
			builder.option(
				() -> op.name(),
				value -> op = LogicOp.valueOf(value),
				() -> LogicOp.NAMES,
				name -> LogicOp.valueOf(name).display(),
				name -> Tipped.tipKeyOf(LogicOp.class, name),
				OP_W_LONG,
				4,
				false
			);
		}
	}
	/** {@code sensor result block1 @totalItems}：从建筑或单位读一个值，末尾固定两位（序号与面）。 */
	@RegisterStatement(id = SensorStatement.ID, order = 80)
	public static class SensorStatement extends MLogStatement {
		public static final String ID = "sensor";
		public String to = "result", from = "block1", type = "@totalItems";
		/** {@code @slotItem} / {@code @slotFluid} 会用到它，缺省第 0 格。 */
		public String slot = "0";
		/** {@code null} 表示不带面，0~5 取六个面。 */
		public String facing = "null";
		private static String display(String value) {
			var name = value.startsWith("@") ? value.substring(1) : value;
			// 裸名优先按内置属性显示：@redstone、@light 与同名内容冲突，读取时同样走属性；内容用带命名空间的名字选取
			if (LAccess.byName(name) instanceof LAccess access)
				return access.blockProperty() ? name : Component.translatable(access.key()).getString();
			var id = ResourceLocation.tryParse(name);
			if (id != null && BuiltInRegistries.ITEM.containsKey(id))
				return new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
			if (id != null && BuiltInRegistries.FLUID.containsKey(id))
				return new FluidStack(BuiltInRegistries.FLUID.get(id), 1).getHoverName().getString();
			return value;
		}
		@Override
		public SensorStatement parse(String[] tokens, int len) {
			if (len > 1) to = tokens[1];
			if (len > 2) from = tokens[2];
			if (len > 3) type = tokens[3];
			if (len > 4) slot = tokens[4];
			if (len > 5) facing = tokens[5];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> to, value -> to = value, FIELD_W);
			builder.label(" = ");
			// 顶部选种类（物品/流体/属性），左侧选模组，列表取两者交集、三列显示
			builder.grouped(
				() -> type, value -> type = value,
				SenseNames.SENSOR_KIND_GROUPS,
				SenseNames.SENSOR_SIDE_GROUPS,
				SensorStatement::display,
				SELECT_W_LONG
			);
			builder.labelKey("mlog.token.in");
			builder.field(() -> from, value -> from = value, FIELD_W);
			// 切换为其他属性时收起参数区，值保留，切回后仍然有效
			if (isSlot()) {
				builder.labelKey("mlog.token.slot");
				builder.field(() -> slot, value -> slot = value, FIELD_W);
			}
			if (!isSided()) return;
			builder.labelKey("mlog.token.face");
			builder.field(() -> facing, value -> facing = value, FIELD_W);
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
		public LCategory category() {
			return LCategory.block;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(to).append(' ').append(from).append(' ').append(type);
			// 尾两位按当前属性是否使用决定，不写隐藏字段残留的值；带面时须保留序号位
			if (isSlot()) {
				if (!"0".equals(slot)) builder.append(' ').append(sanitize(slot));
			} else if (isSided()) builder.append(" 0");
			if (isSided()) builder.append(' ').append(sanitize(facing));
		}
		private boolean isSlot() {
			return LAccess.usesSlot(LAccess.byName(access()));
		}
		/** 目前只有 Create 的过滤槽按面区分，容器内容六个面相同，写入无效。 */
		private boolean isSided() {
			return LAccess.byName(access()) == LAccess.filter;
		}
		private String access() {
			return type.startsWith("@") ? type.substring(1) : type;
		}
	}
	/** 可供 {@code sensor} 读取的物品与流体名；注册表条目上千，惰性构建一次。 */
	public static class SenseNames {
		public static final List<String> ITEMS = BuiltInRegistries.ITEM.stream()
			.filter(item -> item != Items.AIR)
			.map(item -> "@" + BuiltInRegistries.ITEM.getKey(item))
			.toList();
		/** 空流体与「流动的水」类变体须滤除：无静止贴图或与源流体共用贴图，会列出空按钮或重复项。 */
		public static final List<String> FLUIDS = BuiltInRegistries.FLUID.stream()
			.filter(fluid -> fluid != Fluids.EMPTY)
			// getSource() 返回自身的为源流体，返回他者的则是「流动的 X」内部变体
			.filter(fluid -> !(fluid instanceof FlowingFluid flowing) || flowing.getSource() == fluid)
			.map(fluid -> "@" + BuiltInRegistries.FLUID.getKey(fluid))
			.toList();
		/** 声音注册名，供 {@code playsound} 选择；音乐走 {@code playmusic}，不参与定位播放。 */
		public static final List<String> SOUNDS = BuiltInRegistries.SOUND_EVENT.keySet().stream()
			.filter(id -> !id.getPath().startsWith("music"))
			.map(id -> "@" + id)
			.sorted()
			.toList();
		/** 音乐注册名，供 {@code playmusic} 选择：背景音乐与唱片曲目都落在 {@code music*} 下。 */
		public static final List<String> MUSIC = BuiltInRegistries.SOUND_EVENT.keySet().stream()
			.filter(id -> id.getPath().startsWith("music"))
			.map(id -> "@" + id)
			.sorted()
			.toList();
		/** 原版声音按注册名路径首段分组，模组声音按命名空间整组，首组为全部。 */
		public static final List<OptionGroup> SOUND_GROUPS = groups(SOUNDS);
		public static final List<OptionGroup> MUSIC_GROUPS = groups(MUSIC);
		private static List<OptionGroup> groups(List<String> options) {
			// 原版与模组分开收集：原版分类在前，模组分类整体排在最后
			var vanilla = new TreeMap<String, List<String>>();
			var modded = new TreeMap<String, List<String>>();
			for (var option : options) {
				var name = option.startsWith("@") ? option.substring(1) : option;
				var id = ResourceLocation.tryParse(name);
				var mod = id != null && !ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace());
				(mod ? modded : vanilla).computeIfAbsent(id == null ? "other" : category(id), k -> new ArrayList<>()).add(option);
			}
			var groups = new ArrayList<OptionGroup>();
			groups.add(new OptionGroup("", () -> options, 1, "all"));
			for (var map : List.of(vanilla, modded))
				for (var entry : map.entrySet()) groups.add(new OptionGroup("", entry::getValue, 1, entry.getKey()));
			return List.copyOf(groups);
		}
		private static String category(ResourceLocation id) {
			// 模组声音按命名空间整组；原版再按路径首段细分
			if (!ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace())) return id.getNamespace();
			var path = id.getPath();
			var dot = path.indexOf('.');
			return dot < 0 ? "other" : path.substring(0, dot);
		}
		/** 按命名空间分组：minecraft 在前，其余按命名空间排序，首组为全部。 */
		public static List<OptionGroup> namespaceGroups(List<String> options) {
			return groupedBy(options, SenseNames::idNamespace);
		}
		private static String idNamespace(String option) {
			var name = option.startsWith("@") ? option.substring(1) : option;
			var id = ResourceLocation.tryParse(name);
			return id == null ? ResourceLocation.DEFAULT_NAMESPACE : id.getNamespace();
		}
		/** 通用分组：{@code namespaceOf} 决定每个选项归属哪个分类。 */
		private static List<OptionGroup> groupedBy(List<String> options, Function<String, String> namespaceOf) {
			var map = new TreeMap<String, List<String>>();
			for (var option : options) map.computeIfAbsent(namespaceOf.apply(option), k -> new ArrayList<>()).add(option);
			// 「全部」按分类顺序拼接：原版在前，其余按命名空间排序，模组不会和原版交错
			var ordered = new ArrayList<String>();
			var vanilla = map.remove(ResourceLocation.DEFAULT_NAMESPACE);
			if (vanilla != null) ordered.addAll(vanilla);
			for (var list : map.values()) ordered.addAll(list);
			var groups = new ArrayList<OptionGroup>();
			groups.add(new OptionGroup("", () -> ordered, 3, "all"));
			if (vanilla != null) groups.add(new OptionGroup("", () -> vanilla, 3, ResourceLocation.DEFAULT_NAMESPACE));
			for (var entry : map.entrySet()) groups.add(new OptionGroup("", entry::getValue, 3, entry.getKey()));
			return List.copyOf(groups);
		}
		/** 无需参数的粒子，供 {@code effect} 选择。 */
		public static final List<String> PARTICLES = BuiltInRegistries.PARTICLE_TYPE.keySet().stream()
			.filter(id -> BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof SimpleParticleType)
			.map(id -> "@" + id)
			.toList();
		/** 实体类型注册名，供 {@code spawn} 选择。 */
		public static final List<String> ENTITY_TYPES = BuiltInRegistries.ENTITY_TYPE.keySet().stream()
			.map(id -> "@" + id)
			.toList();
		/** 实体按模组分组，原版在前。 */
		public static final List<OptionGroup> ENTITY_GROUPS = namespaceGroups(ENTITY_TYPES);
		/** 物品、流体与属性的联合列表，供 {@code sensor} 选择。 */
		public static final List<String> SENSOR_OPTIONS = Stream.concat(
			Stream.concat(ITEMS.stream(), FLUIDS.stream()),
			LAccess.names().stream()
		).toList();
		/** {@code sensor} 顶部种类：物品、流体用六列，属性用三列。 */
		public static final List<OptionGroup> SENSOR_KIND_GROUPS = List.of(
			new OptionGroup("box", () -> ITEMS, 6, "items"),
			new OptionGroup("liquid", () -> FLUIDS, 6, "fluids"),
			new OptionGroup("tree", LAccess::names, 3, "properties")
		);
		/** {@code sensor} 左侧模组分类：内容优先于属性，原版在前；与顶部种类取交集。 */
		public static final List<OptionGroup> SENSOR_SIDE_GROUPS = groupedBy(SENSOR_OPTIONS, SenseNames::sensorNamespace);
		private static String sensorNamespace(String option) {
			var name = option.startsWith("@") ? option.substring(1) : option;
			var id = ResourceLocation.tryParse(name);
			// 与汇编一致：物品/流体名优先于同名属性
			if (id != null && (BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.FLUID.containsKey(id))) return id.getNamespace();
			return LAccess.namespace(name);
		}
		/** {@code control} 可写属性按模组分组，原版在前。 */
		public static final List<OptionGroup> CONTROL_GROUPS = controlGroups();
		private static List<OptionGroup> controlGroups() {
			var byNamespace = LAccess.controlGroups();
			var ordered = new ArrayList<String>();
			byNamespace.values().forEach(ordered::addAll);
			var groups = new ArrayList<OptionGroup>();
			groups.add(new OptionGroup("", () -> ordered, 3, "all"));
			byNamespace.forEach((namespace, list) -> groups.add(new OptionGroup("", () -> list, 3, namespace)));
			return List.copyOf(groups);
		}
		/** 状态效果注册名，供 {@code status} 与 {@code setprop} 选择。 */
		public static final List<String> EFFECTS = BuiltInRegistries.MOB_EFFECT.keySet().stream()
			.map(id -> "@" + id)
			.toList();
		/** 方块注册名，供 {@code ulocate} 选择。 */
		public static final List<String> BLOCKS = BuiltInRegistries.BLOCK.keySet().stream()
			.filter(id -> !id.toString().equals("minecraft:air"))
			.map(id -> "@" + id)
			.toList();
		/** 列表里显示去掉 {@code @} 与 {@code minecraft:} 的名字。 */
		public static String display(String value) {
			var name = value.startsWith("@") ? value.substring(1) : value;
			return name.startsWith("minecraft:") ? name.substring(10) : name;
		}
		/** 实体类型显示本地化名，认不出时退回注册名。 */
		public static String entityDisplay(String value) {
			var id = ResourceLocation.tryParse(value.startsWith("@") ? value.substring(1) : value);
			if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return display(value);
			return BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString();
		}
		/** 状态效果显示本地化名，认不出时退回注册名。 */
		public static String effectDisplay(String value) {
			var id = ResourceLocation.tryParse(value.startsWith("@") ? value.substring(1) : value);
			if (id == null) return display(value);
			var effect = BuiltInRegistries.MOB_EFFECT.get(id);
			return effect == null ? display(value) : effect.getDisplayName().getString();
		}
		/** 方块显示本地化名，认不出时退回注册名。 */
		public static String blockDisplay(String value) {
			var id = ResourceLocation.tryParse(value.startsWith("@") ? value.substring(1) : value);
			if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return display(value);
			return BuiltInRegistries.BLOCK.get(id).getName().getString();
		}
	}
	/** {@code jump 5 notEqual x false}；跳转标签由 {@link LParser} 在解析期换成行号。 */
	@RegisterStatement(id = JumpStatement.ID, order = 180)
	public static class JumpStatement extends MLogStatement {
		public static final String ID = "jump";
		/** 解析后由 {@link LParser} 回填，列表增删或重排后由画布重算。 */
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
		public void build(Table builder) {
			builder.labelKey("mlog.token.if");
			if (op != ConditionOp.always) {
				builder.field(() -> value, v -> value = v, FIELD_W);
				conditionSelect(builder);
				builder.field(() -> compare, v -> compare = v, FIELD_W);
			} else conditionSelect(builder);
			builder.spacer();
			builder.node(() -> dest, target -> dest = target);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			// always 不取操作数：给占位常量，默认的 x/false 不应进入变量表
			var always = op == ConditionOp.always;
			var none = builder.none();
			return new JumpI(op, always ? none : builder.var(value), always ? none : builder.var(compare), destIndex);
		}
		@Override
		public LCategory category() {
			return LCategory.control;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ')
				.append(destIndex)
				.append(' ')
				.append(op.name());
			if (op == ConditionOp.always) return;
			builder.append(' ').append(sanitize(value)).append(' ').append(sanitize(compare));
		}
		private void conditionSelect(Table builder) {
			// 条件为纯按钮，点击展开选项列表，不带输入框
			builder.option(
				() -> op.name(),
				v -> op = ConditionOp.valueOf(v),
				() -> ConditionOp.NAMES,
				name -> ConditionOp.valueOf(name).display(),
				name -> Tipped.tipKeyOf(ConditionOp.class, name),
				op == ConditionOp.always ? OP_W_LONG : OP_W,
				// 条件列表三列排开
				3,
				false
			);
		}
	}
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
		public void build(Table builder) {
			// 打印的值占满整行，输入框与卡片同宽
			builder.field(() -> value, v -> value = v, Table.STRETCH);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new PrintI(builder.var(value));
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(value));
		}
	}
	@RegisterStatement(id = PrintCharStatement.ID, order = 30)
	public static class PrintCharStatement extends MLogStatement {
		public static final String ID = "printchar";
		/** 可选的字符码取值域：32~126。 */
		private static final List<String> CHAR_CODES = IntStream.rangeClosed(32, 126).mapToObj(String::valueOf).toList();
		public String value = "65";
		private static String charText(String value) {
			try {
				var code = Integer.parseInt(value);
				return code > 32 && code < 127 ? String.valueOf((char) code) : value;
			} catch (NumberFormatException e) {
				return value;
			}
		}
		@Override
		public PrintCharStatement parse(String[] tokens, int len) {
			if (len > 1) value = tokens[1];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.char");
			// icon 指定 "char" 使弹窗按固定 16×16 格子排列，否则字符宽窄会使格子参差不齐
			builder.grouped(
				() -> value,
				v -> value = v,
				List.of(new OptionGroup("char", () -> CHAR_CODES, 8)),
				PrintCharStatement::charText,
				FIELD_W
			);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new PrintCharI(builder.var(value));
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(value));
		}
	}
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
		public void build(Table builder) {
			// 值占满整行，输入框与卡片同宽
			builder.field(() -> value, v -> value = v, Table.STRETCH);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new FormatI(builder.var(value));
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(value));
		}
	}
	@RegisterStatement(id = PrintFlushStatement.ID, order = 56)
	public static class PrintFlushStatement extends MLogStatement {
		public static final String ID = "printflush";
		public String target = "sign1";
		@Override
		public PrintFlushStatement parse(String[] tokens, int len) {
			if (len > 1) target = tokens[1];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.to");
			builder.field(() -> target, v -> target = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new PrintFlushI(builder.var(target));
		}
		@Override
		public LCategory category() {
			return LCategory.block;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(target);
		}
	}
	/** {@code query circle unit 0 64 0 10}：在区域内查单位或建筑，结果写进 {@code @queries}；圆形时 {@code w} 为半径，长方形时最小角加三边；非世界处理器上被替换为占位语句。 */
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
			if (len > 6) w = tokens[6];
			if (len > 7) h = tokens[7];
			if (len > 8) d = tokens[8];
			return this;
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
			// 切换为圆形时收起宽高深，值保留，切回长方形后仍然有效
			if (shape == QueryShape.circle) {
				builder.labelKey("mlog.token.radius");
				builder.field(() -> w, v -> w = v, FIELD_W);
				return;
			}
			builder.labelKey("mlog.token.width");
			builder.field(() -> w, v -> w = v, FIELD_W);
			builder.labelKey("mlog.token.height");
			builder.field(() -> h, v -> h = v, FIELD_W);
			builder.labelKey("mlog.token.depth");
			builder.field(() -> d, v -> d = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new QueryI(shape, type, builder.var(x), builder.var(y), builder.var(z), builder.var(w), builder.var(h), builder.var(d));
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
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
			// 圆形的 w 即半径，无后三边，未使用时省略
			if (shape != QueryShape.rect) return;
			builder.append(' ').append(sanitize(h)).append(' ').append(sanitize(d));
		}
	}
	/** {@code getblock <结果> <x> <y> <z>}：读出该处方块的注册名，未加载时为空。 */
	@RegisterStatement(id = GetBlockStatement.ID, order = 200)
	public static class GetBlockStatement extends MLogStatement {
		public static final String ID = "getblock";
		public String result = "result", x = "0", y = "0", z = "0";
		@Override
		public GetBlockStatement parse(String[] tokens, int len) {
			if (len > 1) result = tokens[1];
			if (len > 2) x = tokens[2];
			if (len > 3) y = tokens[3];
			if (len > 4) z = tokens[4];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> result, v -> result = v, FIELD_W);
			builder.label(" = ");
			builder.label("x");
			builder.field(() -> x, v -> x = v, FIELD_W);
			builder.label("y");
			builder.field(() -> y, v -> y = v, FIELD_W);
			builder.label("z");
			builder.field(() -> z, v -> z = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new GetBlockI(builder.var(result), builder.var(x), builder.var(y), builder.var(z));
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ')
				.append(sanitize(result))
				.append(' ')
				.append(sanitize(x))
				.append(' ')
				.append(sanitize(y))
				.append(' ')
				.append(sanitize(z));
		}
	}
	/** {@code setblock <x> <y> <z> <方块>}：设置该处的方块，未加载时不动。 */
	@RegisterStatement(id = SetBlockStatement.ID, order = 210)
	public static class SetBlockStatement extends MLogStatement {
		public static final String ID = "setblock";
		public String block = "@air", x = "0", y = "0", z = "0";
		@Override
		public SetBlockStatement parse(String[] tokens, int len) {
			if (len > 1) x = tokens[1];
			if (len > 2) y = tokens[2];
			if (len > 3) z = tokens[3];
			if (len > 4) block = tokens[4];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.set");
			builder.label("x");
			builder.field(() -> x, v -> x = v, FIELD_W);
			builder.label("y");
			builder.field(() -> y, v -> y = v, FIELD_W);
			builder.label("z");
			builder.field(() -> z, v -> z = v, FIELD_W);
			builder.labelKey("mlog.token.block");
			builder.field(() -> block, v -> block = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SetBlockI(builder.var(x), builder.var(y), builder.var(z), builder.var(block));
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ')
				.append(sanitize(x))
				.append(' ')
				.append(sanitize(y))
				.append(' ')
				.append(sanitize(z))
				.append(' ')
				.append(sanitize(block));
		}
	}
	/** {@code getflag <结果> <标记>}：标记存在时为 1。 */
	@RegisterStatement(id = GetFlagStatement.ID, order = 290)
	public static class GetFlagStatement extends MLogStatement {
		public static final String ID = "getflag";
		public String result = "result", flag = "\"flag\"";
		@Override
		public GetFlagStatement parse(String[] tokens, int len) {
			if (len > 1) result = tokens[1];
			if (len > 2) flag = tokens[2];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> result, v -> result = v, FIELD_W);
			builder.label(" = ");
			builder.labelKey("mlog.token.flag");
			builder.field(() -> flag, v -> flag = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new GetFlagI(builder.var(result), builder.var(flag));
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(result)).append(' ').append(sanitize(flag));
		}
	}
	/** {@code setflag <标记> <值>}：值为 0 或空对象时清除。 */
	@RegisterStatement(id = SetFlagStatement.ID, order = 300)
	public static class SetFlagStatement extends MLogStatement {
		public static final String ID = "setflag";
		public String flag = "\"flag\"", value = "true";
		@Override
		public SetFlagStatement parse(String[] tokens, int len) {
			if (len > 1) flag = tokens[1];
			if (len > 2) value = tokens[2];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.field(() -> flag, v -> flag = v, FIELD_W);
			builder.label(" = ");
			builder.field(() -> value, v -> value = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SetFlagI(builder.var(flag), builder.var(value));
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(flag)).append(' ').append(sanitize(value));
		}
	}
	/** {@code message <类型> <秒数> <成功>}：把打印缓存的内容显示出来；成功变量为 {@code @wait} 时等上一条结束再重试。 */
	@RegisterStatement(id = MessageStatement.ID, order = 260)
	public static class MessageStatement extends MLogStatement {
		public static final String ID = "message";
		public MessageType type = MessageType.title;
		public String duration = "3", success = "@wait";
		/** 旧存档的显示位置换到新类型上。 */
		private static MessageType typeOf(String token) {
			return switch (token) {
				case "notify" -> MessageType.chat;
				case "announce" -> MessageType.title;
				case "toast" -> MessageType.actionbar;
				case "mission" -> MessageType.bossbar;
				default -> {
					try {
						yield MessageType.valueOf(token);
					} catch (IllegalArgumentException ignored) {
						yield MessageType.title;
					}
				}
			};
		}
		@Override
		public MessageStatement parse(String[] tokens, int len) {
			if (len > 1) type = typeOf(tokens[1]);
			if (len > 2) duration = tokens[2];
			if (len > 3) success = tokens[3];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.option(
				() -> type.name(),
				v -> type = MessageType.valueOf(v),
				() -> MessageType.NAMES,
				name -> MessageType.valueOf(name).display(),
				OP_W_LONG,
				2
			);
			// 停留时长只对标题与动作栏有意义
			if (type == MessageType.title || type == MessageType.actionbar) {
				builder.labelKey("mlog.token.duration");
				builder.field(() -> duration, v -> duration = v, FIELD_W);
			}
			builder.labelKey("mlog.token.success");
			builder.field(() -> success, v -> success = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new MessageI(type, builder.var(duration), builder.var(success), builder.index);
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ').append(type.name())
				.append(' ').append(sanitize(duration))
				.append(' ').append(sanitize(success));
		}
	}
	/** {@code playsound <定位> <声音> <音量> <音高> <声像> <x> <y> <z> <限制>}：播放注册表中的声音，非定位时声像控制左右。 */
	@RegisterStatement(id = PlaySoundStatement.ID, order = 320)
	public static class PlaySoundStatement extends MLogStatement {
		public static final String ID = "playsound";
		private static final List<String> POSITIONAL = List.of("true", "false");
		public String positional = "true";
		public String sound = "@minecraft:block.note_block.harp", volume = "1", pitch = "1", pan = "0";
		public String x = "@thisx", y = "@thisy", z = "@thisz", limit = "true";
		@Override
		public PlaySoundStatement parse(String[] tokens, int len) {
			// 首槽是布尔时按新排布解析，否则按旧格式的六槽
			if (len > 1 && POSITIONAL.contains(tokens[1])) {
				positional = tokens[1];
				if (len > 2) sound = tokens[2];
				if (len > 3) volume = tokens[3];
				if (len > 4) pitch = tokens[4];
				if (len > 5) pan = tokens[5];
				if (len > 6) x = tokens[6];
				if (len > 7) y = tokens[7];
				if (len > 8) z = tokens[8];
				if (len > 9) limit = tokens[9];
				return this;
			}
			if (len > 1) sound = tokens[1];
			if (len > 2) volume = tokens[2];
			if (len > 3) pitch = tokens[3];
			if (len > 4) x = tokens[4];
			if (len > 5) y = tokens[5];
			if (len > 6) z = tokens[6];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.option(
				() -> positional,
				v -> positional = v,
				() -> POSITIONAL,
				v -> Component.translatable("mlog.token." + ("true".equals(v) ? "positional" : "global")).getString(),
				OP_W_LONG,
				2
			);
			builder.labelKey("mlog.token.sound");
			builder.grouped(
				() -> sound,
				v -> sound = v,
				SenseNames.SOUND_GROUPS,
				SenseNames::display,
				SELECT_W_LONG,
				true,
				true
			);
			builder.labelKey("mlog.token.volume");
			builder.field(() -> volume, v -> volume = v, FIELD_W);
			builder.labelKey("mlog.token.pitch");
			builder.field(() -> pitch, v -> pitch = v, FIELD_W);
			if ("true".equals(positional)) {
				builder.label("x");
				builder.field(() -> x, v -> x = v, FIELD_W);
				builder.label("y");
				builder.field(() -> y, v -> y = v, FIELD_W);
				builder.label("z");
				builder.field(() -> z, v -> z = v, FIELD_W);
			} else {
				builder.labelKey("mlog.token.pan");
				builder.field(() -> pan, v -> pan = v, FIELD_W);
			}
			builder.labelKey("mlog.token.limit");
			builder.field(() -> limit, v -> limit = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new PlaySoundI(
				Boolean.parseBoolean(positional),
				builder.namedVar(sound, BuiltInRegistries.SOUND_EVENT),
				builder.var(volume),
				builder.var(pitch),
				builder.var(pan),
				builder.var(x),
				builder.var(y),
				builder.var(z),
				builder.var(limit)
			);
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ').append(positional)
				.append(' ').append(sanitize(sound))
				.append(' ').append(sanitize(volume))
				.append(' ').append(sanitize(pitch))
				.append(' ').append(sanitize(pan))
				.append(' ').append(sanitize(x))
				.append(' ').append(sanitize(y))
				.append(' ').append(sanitize(z))
				.append(' ').append(sanitize(limit));
		}
	}
	/** {@code playmusic <音乐> <中断>}：在音乐通道播放；中断为真时先停掉当前音乐，音乐为空时停止。 */
	@RegisterStatement(id = PlayMusicStatement.ID, order = 330)
	public static class PlayMusicStatement extends MLogStatement {
		public static final String ID = "playmusic";
		public String music = "@minecraft:music.game", interrupt = "true";
		@Override
		public PlayMusicStatement parse(String[] tokens, int len) {
			if (len > 1) music = tokens[1];
			if (len > 2) interrupt = tokens[2];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.music");
			builder.grouped(
				() -> music,
				v -> music = v,
				SenseNames.MUSIC_GROUPS,
				SenseNames::display,
				SELECT_W_LONG,
				true,
				true
			);
			builder.labelKey("mlog.token.interrupt");
			builder.field(() -> interrupt, v -> interrupt = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new PlayMusicI(builder.namedVar(music, BuiltInRegistries.SOUND_EVENT), builder.var(interrupt));
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ').append(sanitize(music))
				.append(' ').append(sanitize(interrupt));
		}
	}
	/** {@code effect <粒子> <x> <y> <z> <数量> <dx> <dy> <dz> <速度>}：在指定位置生成粒子。 */
	@RegisterStatement(id = EffectStatement.ID, order = 270)
	public static class EffectStatement extends MLogStatement {
		public static final String ID = "effect";
		public String particle = "@minecraft:flame", x = "@thisx", y = "@thisy", z = "@thisz", count = "1";
		public String dx = "0", dy = "0", dz = "0", speed = "0";
		@Override
		public EffectStatement parse(String[] tokens, int len) {
			if (len > 1) particle = tokens[1];
			if (len > 2) x = tokens[2];
			if (len > 3) y = tokens[3];
			if (len > 4) z = tokens[4];
			if (len > 5) count = tokens[5];
			if (len > 6) dx = tokens[6];
			if (len > 7) dy = tokens[7];
			if (len > 8) dz = tokens[8];
			if (len > 9) speed = tokens[9];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.particle");
			builder.effect(() -> particle, v -> particle = v, SELECT_W);
			builder.label("x");
			builder.field(() -> x, v -> x = v, FIELD_W);
			builder.label("y");
			builder.field(() -> y, v -> y = v, FIELD_W);
			builder.label("z");
			builder.field(() -> z, v -> z = v, FIELD_W);
			builder.labelKey("mlog.token.count");
			builder.field(() -> count, v -> count = v, FIELD_W);
			builder.label("dx");
			builder.field(() -> dx, v -> dx = v, FIELD_W);
			builder.label("dy");
			builder.field(() -> dy, v -> dy = v, FIELD_W);
			builder.label("dz");
			builder.field(() -> dz, v -> dz = v, FIELD_W);
			builder.labelKey("mlog.token.speed");
			builder.field(() -> speed, v -> speed = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new EffectI(
				builder.namedVar(particle, BuiltInRegistries.PARTICLE_TYPE),
				builder.var(x),
				builder.var(y),
				builder.var(z),
				builder.var(count),
				builder.var(dx),
				builder.var(dy),
				builder.var(dz),
				builder.var(speed)
			);
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ').append(sanitize(particle))
				.append(' ').append(sanitize(x))
				.append(' ').append(sanitize(y))
				.append(' ').append(sanitize(z))
				.append(' ').append(sanitize(count))
				.append(' ').append(sanitize(dx))
				.append(' ').append(sanitize(dy))
				.append(' ').append(sanitize(dz))
				.append(' ').append(sanitize(speed));
		}
	}
	/** {@code ubind <类型>}：绑定附近该类型最近的非敌对生物。 */
	@RegisterStatement(id = UnitBindStatement.ID, order = 280)
	public static class UnitBindStatement extends MLogStatement {
		public static final String ID = "ubind";
		public String type = "@minecraft:iron_golem";
		@Override
		public UnitBindStatement parse(String[] tokens, int len) {
			if (len > 1) type = tokens[1];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.type");
			builder.unitTypes(() -> type, v -> type = v, SELECT_W, 2);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new UnitBindI(builder.namedVar(type, BuiltInRegistries.ENTITY_TYPE));
		}
		@Override
		public LCategory category() {
			return LCategory.unit;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(type));
		}
	}
	/** {@code ucontrol <动作> <p1> <p2> <p3> <p4> <p5>}：控制绑定的生物。 */
	@RegisterStatement(id = UnitControlStatement.ID, order = 290)
	public static class UnitControlStatement extends MLogStatement {
		public static final String ID = "ucontrol";
		public UnitControlType type = UnitControlType.move;
		public String p1 = "0", p2 = "0", p3 = "0", p4 = "0", p5 = "within";
		@Override
		public UnitControlStatement parse(String[] tokens, int len) {
			if (len > 1) type = UnitControlType.valueOf(tokens[1]);
			if (len > 2) p1 = tokens[2];
			if (len > 3) p2 = tokens[3];
			if (len > 4) p3 = tokens[4];
			if (len > 5) p4 = tokens[5];
			if (len > 6) p5 = tokens[6];
			return this;
		}
		@Override
		public void build(Table builder) {
				builder.option(
					() -> type.name(),
					v -> type = UnitControlType.valueOf(v),
					() -> UnitControlType.NAMES,
					name -> UnitControlType.valueOf(name).display(),
					name -> Tipped.tipKeyOf(UnitControlType.class, name),
					OP_W_LONG,
					2,
					false
				);
			switch (type) {
				case move, approach, pathfind -> {
					xyz(builder);
					if (type == UnitControlType.approach) {
						builder.labelKey("mlog.token.radius");
						builder.field(() -> p4, v -> p4 = v, FIELD_W);
					}
				}
				case within -> {
					xyz(builder);
					builder.labelKey("mlog.token.radius");
					builder.field(() -> p4, v -> p4 = v, FIELD_W);
					builder.labelKey("mlog.token.result");
					builder.field(() -> p5, v -> p5 = v, FIELD_W);
				}
				case targetp -> {
					builder.labelKey("mlog.token.block-unit");
					builder.field(() -> p1, v -> p1 = v, FIELD_W);
					builder.labelKey("mlog.token.shoot");
					builder.field(() -> p2, v -> p2 = v, FIELD_W);
				}
				case target -> {
					xyz(builder);
					builder.labelKey("mlog.token.shoot");
					builder.field(() -> p4, v -> p4 = v, FIELD_W);
				}
				case itemDrop -> {
					builder.labelKey("mlog.token.to");
					builder.field(() -> p1, v -> p1 = v, FIELD_W);
					builder.labelKey("mlog.token.amount");
					builder.field(() -> p2, v -> p2 = v, FIELD_W);
				}
				case itemTake -> {
					builder.labelKey("mlog.token.from");
					builder.field(() -> p1, v -> p1 = v, FIELD_W);
					builder.labelKey("mlog.token.item");
					builder.grouped(
						() -> p2,
						v -> p2 = v,
						List.of(new OptionGroup("box", () -> SenseNames.ITEMS, 6)),
						SenseNames::display,
						SELECT_W
					);
					builder.labelKey("mlog.token.amount");
					builder.field(() -> p3, v -> p3 = v, FIELD_W);
				}
				case flag -> {
					builder.labelKey("mlog.token.value");
					builder.field(() -> p1, v -> p1 = v, FIELD_W);
				}
				case getBlock -> {
					xyz(builder);
					builder.labelKey("mlog.token.type");
					builder.field(() -> p4, v -> p4 = v, FIELD_W);
					builder.labelKey("mlog.token.building");
					builder.field(() -> p5, v -> p5 = v, FIELD_W);
				}
				case idle, stop, unbind -> {}
			}
		}
		private void xyz(Table builder) {
			builder.label("x");
			builder.field(() -> p1, v -> p1 = v, FIELD_W);
			builder.label("y");
			builder.field(() -> p2, v -> p2 = v, FIELD_W);
			builder.label("z");
			builder.field(() -> p3, v -> p3 = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new UnitControlI(type, builder.var(p1), builder.var(p2), builder.var(p3), builder.var(p4), builder.var(p5));
		}
		@Override
		public LCategory category() {
			return LCategory.unit;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ').append(type.name());
			switch (type) {
				case idle, stop, unbind -> {}
				case flag -> operands(builder, p1);
				case targetp, itemDrop -> operands(builder, p1, p2);
				case move, pathfind, itemTake -> operands(builder, p1, p2, p3);
				case approach, target -> operands(builder, p1, p2, p3, p4);
				case within, getBlock -> operands(builder, p1, p2, p3, p4, p5);
			}
		}
		private static void operands(StringBuilder builder, String... values) {
			for (var value : values) builder.append(' ').append(sanitize(value));
		}
	}
	/** {@code uradar <筛选1> <筛选2> <筛选3> <排序> <来源> <顺序> <结果>}：以绑定生物为圆心搜索。 */
	@RegisterStatement(id = UnitRadarStatement.ID, order = 300)
	public static class UnitRadarStatement extends MLogStatement {
		public static final String ID = "uradar";
		public RadarFilter target1 = RadarFilter.hostile, target2 = RadarFilter.any, target3 = RadarFilter.any;
		public RadarSort sort = RadarSort.distance;
		/** 字段顺序照原样，执行时固定为 {@code @unit}。 */
		public String source = "0", order = "1", result = "result";
		@Override
		public UnitRadarStatement parse(String[] tokens, int len) {
			if (len > 1) target1 = RadarFilter.valueOf(tokens[1]);
			if (len > 2) target2 = RadarFilter.valueOf(tokens[2]);
			if (len > 3) target3 = RadarFilter.valueOf(tokens[3]);
			if (len > 4) sort = RadarSort.valueOf(tokens[4]);
			if (len > 5) source = tokens[5];
			if (len > 6) order = tokens[6];
			if (len > 7) result = tokens[7];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.target");
			builder.option(
				() -> target1.name(),
				v -> target1 = RadarFilter.valueOf(v),
				() -> RadarFilter.NAMES,
				name -> RadarFilter.valueOf(name).display(),
				OP_W_LONG,
				3
			);
			builder.labelKey("mlog.token.and");
			builder.option(
				() -> target2.name(),
				v -> target2 = RadarFilter.valueOf(v),
				() -> RadarFilter.NAMES,
				name -> RadarFilter.valueOf(name).display(),
				OP_W_LONG,
				3
			);
			builder.labelKey("mlog.token.and");
			builder.option(
				() -> target3.name(),
				v -> target3 = RadarFilter.valueOf(v),
				() -> RadarFilter.NAMES,
				name -> RadarFilter.valueOf(name).display(),
				OP_W_LONG,
				3
			);
			builder.option(
				() -> sort.name(),
				v -> sort = RadarSort.valueOf(v),
				() -> RadarSort.NAMES,
				name -> RadarSort.valueOf(name).display(),
				OP_W_LONG,
				2
			);
			builder.labelKey("mlog.token.order");
			builder.field(() -> order, v -> order = v, FIELD_W);
			builder.labelKey("mlog.token.result");
			builder.field(() -> result, v -> result = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new UnitRadarI(target1, target2, target3, sort, builder.var(order), builder.var(result));
		}
		@Override
		public LCategory category() {
			return LCategory.unit;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ').append(target1.name())
				.append(' ').append(target2.name())
				.append(' ').append(target3.name())
				.append(' ').append(sort.name())
				.append(' ').append(sanitize(source))
				.append(' ').append(sanitize(order))
				.append(' ').append(sanitize(result));
		}
	}
	/** {@code ulocate <类型> <目标> <半径> <x> <y> <z> <found>}：寻找方块、出生点或受伤生物。 */
	@RegisterStatement(id = UnitLocateStatement.ID, order = 310)
	public static class UnitLocateStatement extends MLogStatement {
		public static final String ID = "ulocate";
		public LocateType type = LocateType.block;
		public String target = "@minecraft:stone", radius = "8";
		public String outX = "outx", outY = "outy", outZ = "outz", outFound = "found";
		@Override
		public UnitLocateStatement parse(String[] tokens, int len) {
			if (len > 1) type = LocateType.valueOf(tokens[1]);
			if (len > 2) target = tokens[2];
			if (len > 3) radius = tokens[3];
			if (len > 4) outX = tokens[4];
			if (len > 5) outY = tokens[5];
			if (len > 6) outZ = tokens[6];
			if (len > 7) outFound = tokens[7];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.option(
				() -> type.name(),
				v -> type = LocateType.valueOf(v),
				() -> LocateType.NAMES,
				name -> LocateType.valueOf(name).display(),
				OP_W_LONG,
				3
			);
			if (type == LocateType.block) {
				builder.labelKey("mlog.token.block");
				builder.grouped(
					() -> target,
					v -> target = v,
					List.of(new OptionGroup("block", () -> SenseNames.BLOCKS, 6)),
					SenseNames::blockDisplay,
					SELECT_W
				);
			}
			builder.labelKey("mlog.token.radius");
			builder.field(() -> radius, v -> radius = v, FIELD_W);
			builder.label("x");
			builder.field(() -> outX, v -> outX = v, FIELD_W);
			builder.label("y");
			builder.field(() -> outY, v -> outY = v, FIELD_W);
			builder.label("z");
			builder.field(() -> outZ, v -> outZ = v, FIELD_W);
			builder.labelKey("mlog.token.found");
			builder.field(() -> outFound, v -> outFound = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new UnitLocateI(
				type,
				builder.namedVar(target, BuiltInRegistries.BLOCK),
				builder.var(radius),
				builder.var(outX),
				builder.var(outY),
				builder.var(outZ),
				builder.var(outFound)
			);
		}
		@Override
		public LCategory category() {
			return LCategory.unit;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ').append(type.name())
				.append(' ').append(sanitize(target))
				.append(' ').append(sanitize(radius))
				.append(' ').append(sanitize(outX))
				.append(' ').append(sanitize(outY))
				.append(' ').append(sanitize(outZ))
				.append(' ').append(sanitize(outFound));
		}
	}
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
		public void build(Table builder) {
			builder.field(() -> result, v -> result = v, FIELD_W);
			builder.labelKey("mlog.token.-lookup");
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
		public LInstruction build(LAssembler builder) {
			return new LookupI(type, builder.var(result), builder.var(id));
		}
		@Override
		public LCategory category() {
			return LCategory.operation;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(type.name()).append(' ').append(result).append(' ').append(sanitize(id));
		}
	}
	@RegisterStatement(id = DrawStatement.ID, order = 15)
	public static class DrawStatement extends MLogStatement {
		public static final String ID = "draw";
		public GraphicsType type = GraphicsType.clear;
		public String x = "0", y = "0", p1 = "0", p2 = "0", p3 = "0", p4 = "0";
		/** {@code print} 的对齐方式，与 {@code p1} 共用操作数。 */
		public DrawAlign align = DrawAlign.bottomLeft;
		/** 非 {@code null} 的标签走 lang key，为 {@code null} 时只放字段。 */
		private static void fields(Table builder, @Nullable String label, Supplier<String> get, Consumer<String> set) {
			if (label != null) addLabel(builder, label);
			builder.field(get, set, FIELD_W);
		}
		private static void addLabel(Table builder, String label) {
			if (label.startsWith("mlog.token.")) builder.labelKey(label);
			else builder.label(label);
		}
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
		@Override
		public void build(Table builder) {
			builder.option(
				() -> type.name(),
				this::setType,
				() -> GraphicsType.NAMES,
				name -> GraphicsType.valueOf(name).display(),
				name -> Tipped.tipKeyOf(GraphicsType.class, name),
				OP_W_LONG,
				2,
				false
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
					builder.labelKey("mlog.token.color");
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
					fields(builder, "mlog.token.width", () -> p1, v -> p1 = v);
					fields(builder, "mlog.token.height", () -> p2, v -> p2 = v);
				}
				case poly, linePoly -> {
					fields(builder, "x", () -> x, v -> x = v);
					fields(builder, "y", () -> y, v -> y = v);
					fields(builder, "mlog.token.sides", () -> p1, v -> p1 = v);
					fields(builder, "mlog.token.radius", () -> p2, v -> p2 = v);
					fields(builder, "mlog.token.rotation", () -> p3, v -> p3 = v);
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
					fields(builder, "mlog.token.image", () -> p1, v -> p1 = v);
					fields(builder, "mlog.token.size", () -> p2, v -> p2 = v);
					fields(builder, "mlog.token.rotation", () -> p3, v -> p3 = v);
				}
				case print -> {
					fields(builder, "x", () -> x, v -> x = v);
					fields(builder, "y", () -> y, v -> y = v);
					builder.labelKey("mlog.token.align");
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
				case rotate -> fields(builder, "mlog.token.angle", () -> p1, v -> p1 = v);
				case reset -> {}
			}
		}
		/** 第四个操作数按类型解释：{@code print} 的对齐是名字，其余类型是数值。 */
		private void select(String token) {
			// 该对齐带 {@code @} 前缀（汇编器中为常量名），一并接受
			if (type == GraphicsType.print) align = DrawAlign.valueOf(token.startsWith("@") ? token.substring(1) : token);
			else p1 = token;
		}
		@Override
		public LInstruction build(LAssembler builder) {
			var first = type == GraphicsType.print ? builder.var(Integer.toString(align.ordinal())) : builder.var(p1);
			return new DrawI(type, builder.var(x), builder.var(y), first, builder.var(p2), builder.var(p3), builder.var(p4));
		}
		@Override
		public LCategory category() {
			return LCategory.io;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(type.name());
			switch (type) {
				case reset -> {}
				case col, stroke -> operands(builder, x);
				case rotate -> operands(builder, p1);
				case translate, scale -> operands(builder, x, y);
				case clear -> operands(builder, x, y, p1);
				case print -> operands(builder, x, y, align.name());
				case color, line, rect, lineRect -> operands(builder, x, y, p1, p2);
				case poly, linePoly, image -> operands(builder, x, y, p1, p2, p3);
				case triangle -> operands(builder, x, y, p1, p2, p3, p4);
			}
		}
		private static void operands(StringBuilder builder, String... values) {
			for (var value : values) builder.append(' ').append(sanitize(value));
		}
		/** 切换类型时补齐该类型的必需默认值，否则新类型无法绘制。 */
		private void setType(String name) {
			type = GraphicsType.valueOf(name);
			switch (type) {
				// 默认全 0 即全透明
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
	}
	@RegisterStatement(id = DrawFlushStatement.ID, order = 50)
	public static class DrawFlushStatement extends MLogStatement {
		public static final String ID = "drawflush";
		public String target = "display1";
		@Override
		public DrawFlushStatement parse(String[] tokens, int len) {
			if (len > 1) target = tokens[1];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.to");
			builder.field(() -> target, v -> target = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new DrawFlushI(builder.var(target));
		}
		@Override
		public LCategory category() {
			return LCategory.block;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(target);
		}
	}
	/** {@code spawn <类型> <x> <y> <z> <朝向> <结果>}：按类型生成实体。 */
	@RegisterStatement(id = SpawnStatement.ID, order = 220)
	public static class SpawnStatement extends MLogStatement {
		public static final String ID = "spawn";
		public String type = "@minecraft:pig", x = "0", y = "0", z = "0", rotation = "0", result = "result";
		@Override
		public SpawnStatement parse(String[] tokens, int len) {
			if (len > 1) type = tokens[1];
			if (len > 2) x = tokens[2];
			if (len > 3) y = tokens[3];
			if (len > 4) z = tokens[4];
			if (len > 5) rotation = tokens[5];
			if (len > 6) result = tokens[6];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.type");
			builder.grouped(() -> type, v -> type = v, SenseNames.ENTITY_GROUPS, SenseNames::entityDisplay, SELECT_W_LONG, true);
			builder.label("x");
			builder.field(() -> x, v -> x = v, FIELD_W);
			builder.label("y");
			builder.field(() -> y, v -> y = v, FIELD_W);
			builder.label("z");
			builder.field(() -> z, v -> z = v, FIELD_W);
			builder.labelKey("mlog.token.angle");
			builder.field(() -> rotation, v -> rotation = v, FIELD_W);
			builder.labelKey("mlog.token.result");
			builder.field(() -> result, v -> result = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new SpawnI(
				builder.namedVar(type, BuiltInRegistries.ENTITY_TYPE),
				builder.var(x),
				builder.var(y),
				builder.var(z),
				builder.var(rotation),
				builder.var(result)
			);
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ').append(sanitize(type))
				.append(' ').append(sanitize(x))
				.append(' ').append(sanitize(y))
				.append(' ').append(sanitize(z))
				.append(' ').append(sanitize(rotation))
				.append(' ').append(result);
		}
	}
	/** {@code status <apply|clear> <效果> <目标> <秒数>}：施加或清除状态效果。 */
	@RegisterStatement(id = StatusStatement.ID, order = 230)
	public static class StatusStatement extends MLogStatement {
		public static final String ID = "status";
		private static final List<String> ACTIONS = List.of("apply", "clear");
		public String action = "apply", effect = "@minecraft:poison", unit = "unit", duration = "10";
		@Override
		public StatusStatement parse(String[] tokens, int len) {
			// true 为清除，false 为施加
			if (len > 1) action = switch (tokens[1]) {
				case "true" -> "clear";
				case "false" -> "apply";
				default -> ACTIONS.contains(tokens[1]) ? tokens[1] : "apply";
			};
			if (len > 2) effect = tokens[2];
			if (len > 3) unit = tokens[3];
			if (len > 4) duration = tokens[4];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.option(
				() -> action,
				v -> action = v,
				() -> ACTIONS,
				v -> Component.translatable(TOKEN_KEY_PREFIX + v).getString(),
				OP_W_LONG,
				2
			);
			// 状态图标与设置属性的效果分组共用同一套渲染
			builder.groupedOption(
				() -> effect,
				v -> effect = v,
				List.of(new OptionGroup("effect", () -> SenseNames.EFFECTS, 6)),
				SenseNames::effectDisplay,
				SELECT_W,
				false,
				false
			);
			builder.labelKey("mlog.token.target");
			builder.field(() -> unit, v -> unit = v, FIELD_W);
			builder.labelKey("mlog.token.seconds");
			builder.field(() -> duration, v -> duration = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new StatusI(
				"clear".equals(action),
				builder.namedVar(effect, BuiltInRegistries.MOB_EFFECT),
				builder.var(unit),
				builder.var(duration)
			);
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			// 文本按布尔槽写回
			builder.append(ID)
				.append(' ').append("clear".equals(action))
				.append(' ').append(sanitize(effect))
				.append(' ').append(sanitize(unit))
				.append(' ').append(sanitize(duration));
		}
	}
	/** {@code weathersense <结果> <天气>}：天气成立时写 1。 */
	@RegisterStatement(id = WeatherSenseStatement.ID, order = 240)
	public static class WeatherSenseStatement extends MLogStatement {
		public static final String ID = "weathersense";
		public String result = "result", weather = "@clear";
		@Override
		public WeatherSenseStatement parse(String[] tokens, int len) {
			if (len > 1) result = tokens[1];
			if (len > 2) weather = tokens[2];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.result");
			builder.field(() -> result, v -> result = v, FIELD_W);
			builder.labelKey("mlog.token.weather");
			builder.option(() -> weather, v -> weather = v, () -> WEATHERS, LStatements::weatherDisplay, OP_W_LONG, 3);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new WeatherSenseI(builder.var(result), weatherVar(builder, weather));
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(result).append(' ').append(weather);
		}
	}
	/** {@code weatherset <天气> <状态>}：开关天气。 */
	@RegisterStatement(id = WeatherSetStatement.ID, order = 250)
	public static class WeatherSetStatement extends MLogStatement {
		public static final String ID = "weatherset";
		public String weather = "@clear", state = "true";
		@Override
		public WeatherSetStatement parse(String[] tokens, int len) {
			if (len > 1) weather = tokens[1];
			if (len > 2) state = tokens[2];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.weather");
			builder.option(() -> weather, v -> weather = v, () -> WEATHERS, LStatements::weatherDisplay, OP_W_LONG, 3);
			builder.labelKey("mlog.token.state");
			builder.field(() -> state, v -> state = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new WeatherSetI(weatherVar(builder, weather), builder.var(state));
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(weather).append(' ').append(sanitize(state));
		}
	}
	/** {@code explosion <x> <y> <z> <半径> <破坏>}：在指定位置引爆。 */
	@RegisterStatement(id = ExplosionStatement.ID, order = 280)
	public static class ExplosionStatement extends MLogStatement {
		public static final String ID = "explosion";
		public String x = "0", y = "0", z = "0", radius = "5", destroy = "true";
		@Override
		public ExplosionStatement parse(String[] tokens, int len) {
			if (len > 1) x = tokens[1];
			if (len > 2) y = tokens[2];
			if (len > 3) z = tokens[3];
			if (len > 4) radius = tokens[4];
			if (len > 5) destroy = tokens[5];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.label("x");
			builder.field(() -> x, v -> x = v, FIELD_W);
			builder.label("y");
			builder.field(() -> y, v -> y = v, FIELD_W);
			builder.label("z");
			builder.field(() -> z, v -> z = v, FIELD_W);
			builder.labelKey("mlog.token.radius");
			builder.field(() -> radius, v -> radius = v, FIELD_W);
			builder.labelKey("mlog.token.destroy");
			builder.field(() -> destroy, v -> destroy = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new ExplosionI(builder.var(x), builder.var(y), builder.var(z), builder.var(radius), builder.var(destroy));
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID)
				.append(' ').append(sanitize(x))
				.append(' ').append(sanitize(y))
				.append(' ').append(sanitize(z))
				.append(' ').append(sanitize(radius))
				.append(' ').append(sanitize(destroy));
		}
	}
	/** {@code localeprint <键>}：把本地化文本写入打印缓存。 */
	@RegisterStatement(id = LocalePrintStatement.ID, order = 340)
	public static class LocalePrintStatement extends MLogStatement {
		public static final String ID = "localeprint";
		public String value = "\"name\"";
		@Override
		public LocalePrintStatement parse(String[] tokens, int len) {
			if (len > 1) value = tokens[1];
			return this;
		}
		@Override
		public void build(Table builder) {
			builder.labelKey("mlog.token.key");
			builder.field(() -> value, v -> value = v, FIELD_W);
		}
		@Override
		public LInstruction build(LAssembler builder) {
			return new LocalePrintI(builder.var(value));
		}
		@Override
		public LCategory category() {
			return LCategory.world;
		}
		@Override
		public boolean privileged() {
			return true;
		}
		@Override
		public void write(StringBuilder builder) {
			builder.append(ID).append(' ').append(sanitize(value));
		}
	}
}
