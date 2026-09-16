package io.github.forgestove.mlog.logic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/** 逻辑代码的运行时。移植自 Mindustry 的 {@code LExecutor}，去掉了单位、队伍、特权等概念。 */
public class LExecutor {
	public static final int MAX_INSTRUCTIONS = 1000;
	public static final int MAX_TEXT_BUFFER = 400;
	/** {@code print} 指令的输出缓冲区，每 tick 由方块实体取走并清空。 */
	public final StringBuilder textBuffer = new StringBuilder();
	public LInstruction[] instructions = {};
	/** 参与同步的变量（排除数字常量与内置变量）。 */
	public LVar[] vars = {};
	public LVar counter, thisv, ipt;
	public boolean yield;
	/** 执行所在的维度，用于把链接解析成实体方块。 */
	public @Nullable Level level;
	/** 处理器自身的世界坐标，用来把链接的相对坐标还原成绝对坐标。 */
	public @Nullable BlockPos selfPos;
	public boolean initialized() {
		return instructions.length > 0;
	}
	/** 执行一条指令。{@code @counter} 越界时归零，因此代码会自然循环。 */
	public void runOnce() {
		if (counter.numval >= instructions.length || counter.numval < 0) counter.numval = 0;
		if (counter.numval < instructions.length) {
			counter.isobj = false;
			instructions[(int) counter.numval++].run(this);
		}
	}
	/** 装载已编译的代码，重置全部变量。 */
	public void load(LAssembler builder) {
		textBuffer.setLength(0);
		var list = new ArrayList<LVar>();
		// 链接变量是常量但名字不以 _ / @ 开头，需要保留下来供界面显示
		for (var v : builder.vars.values()) if (!v.constant || v.name.charAt(0) != '_' && v.name.charAt(0) != '@') list.add(v);
		vars = list.toArray(LVar[]::new);
		instructions = builder.instructions;
		counter = builder.getVar("@counter");
		thisv = builder.getVar("@this");
		ipt = builder.getVar("@ipt");
	}
	/** 把链接解析成可感测对象。 */
	public @Nullable MLogSenseable resolve(@Nullable Object target) {
		if (target instanceof MLogSenseable senseable) return senseable;
		if (target instanceof LogicLink link && level != null && selfPos != null)
			return MLogSenseables.at(level, link.absolute(selfPos));
		return null;
	}
	/** 取出并清空 {@code print} 缓冲区。 */
	public String drainText() {
		var text = textBuffer.toString();
		textBuffer.setLength(0);
		return text;
	}
	public interface LInstruction {
		void run(LExecutor exec);
	}
	public record SetI(LVar from, LVar to) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (!to.constant) to.set(from);
		}
	}
	public record OpI(LogicOp op, LVar a, LVar b, LVar dest) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			// LogicOp 保证一元运算非空的是 function1、其余情况是 function2
			if (op.unary) dest.setnum(Objects.requireNonNull(op.function1).get(a.num()));
			else if (op.objFunction2 != null && a.isobj && b.isobj) dest.setnum(op.objFunction2.get(a.obj(), b.obj()));
			else dest.setnum(Objects.requireNonNull(op.function2).get(a.num(), b.num()));
		}
	}
	public record SenseI(LVar from, LVar to, LVar type) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			// 属性名可能是内置的 LAccess，也可能是任意的方块状态属性名
			var key = type.obj();
			var access = key instanceof LAccess builtin ? builtin.name() : key instanceof String name ? name : null;
			if (access == null) {
				to.setobj(null);
				return;
			}
			var senseable = exec.resolve(from.obj());
			if (senseable == null) {
				to.setobj(null);
				return;
			}
			var objOut = senseable.senseObject(access);
			// 没有对象输出时退回数值
			if (objOut == MLogSenseable.NO_SENSED) to.setnum(senseable.sense(access));
			else to.setobj(objOut);
		}
	}
	public record JumpI(ConditionOp op, LVar value, LVar compare, int address) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (address != -1 && op.test(value, compare)) exec.counter.numval = address;
		}
	}
	public record PrintI(LVar value) implements LInstruction {
		@Override
		public void run(LExecutor exec) {
			if (exec.textBuffer.length() >= MAX_TEXT_BUFFER) return;
			var str = format(exec, value);
			exec.textBuffer.append(str, 0, Math.min(str.length(), MAX_TEXT_BUFFER - exec.textBuffer.length()));
		}
		/**
		 * 把变量的值转成文本。
		 * <p>{@code print} 与变量表共用这一份，两处显示才会一致（Mindustry 也是这么做的）。
		 */
		public static String format(LExecutor exec, LVar var) {
			if (!var.isobj) {
				// 整数就不显示小数点
				var numval = var.numval;
				return Math.abs(numval - Math.round(numval)) < 0.00001 ? String.valueOf(Math.round(numval)) : String.valueOf(numval);
			}
			return formatValue(exec, var.objval);
		}
		/** 对齐 Mindustry 的 {@code PrintI.toString}：对象转成有意义的名字，认不出来的一律 {@code [object]}。 */
		private static String formatValue(LExecutor exec, @Nullable Object obj) {
			return switch (obj) {
				case null -> "null";
				case String text -> text;
				// 带命名空间的完整注册名，避免不同模组同名的方块混淆
				case Block block -> BuiltInRegistries.BLOCK.getKey(block).toString();
				case Item item -> BuiltInRegistries.ITEM.getKey(item).toString();
				case Enum<?> value -> value.name();
				case LogicLink link -> formatLink(exec, link);
				default -> "[object]";
			};
		}
		/** 链接输出它指向的方块名，没有目标时是 {@code null}。 */
		private static String formatLink(LExecutor exec, LogicLink link) {
			if (exec.level == null || exec.selfPos == null) return "null";
			var pos = link.absolute(exec.selfPos);
			if (!exec.level.isLoaded(pos)) return "null";
			return BuiltInRegistries.BLOCK.getKey(exec.level.getBlockState(pos).getBlock()).toString();
		}
	}
}
