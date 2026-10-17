package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
/** 第三方方块令其方块实体实现本接口即可接入 {@code sensor}；属性名可为 {@link LAccess} 的内置项，也可为任意的方块状态属性名。 */
public interface MLogSenseable {
	/** 无对象值时的哨兵：返回该值表示此属性没有对象值，调用方须改用 {@link #sense} 读取数值。 */
	Object NO_SENSED = new Object();
	/** 不认识该属性时返回 0。 */
	double sense(String access);
	default Object senseObject(String access) {
		return NO_SENSED;
	}
	/** 对应 {@code sensor} 的 {@code @slotItem}；非容器、越界或空槽返回 {@code null}。 */
	default @Nullable Item itemAt(int slot) {
		return null;
	}
	/** 对应 {@code sensor} 的 {@code @slotFluid}；无流体能力、越界或空罐返回 {@code null}。 */
	default @Nullable Fluid fluidAt(int tank) {
		return null;
	}
	/** 物品容器；无物品能力时为 {@code null}。 */
	default @Nullable IItemHandler itemHandler() {
		return null;
	}
	/**
	 * 属性名按方块状态属性匹配，方块无此属性或所给值非法时不产生改动；数值属性取 {@code value.num()}，物品类属性取其对象。
	 * {@code face} 为 {@code null} 表示六面均接，{@code privileged} 决定方块状态属性是否受 {@link LAccess#controlAllowed()} 白名单约束；{@code owner}
	 * 供需随处理器生灭的效果记录来源，{@code index} 是按行号写入的属性所用的末尾值。
	 */
	default boolean control(String access, LVar value, @Nullable Direction face, @Nullable BlockPos owner, boolean privileged, int index) {
		return false;
	}
	/** 写入 {@code setprop} 的属性；目标不支持该属性时无动作。 */
	default void setProp(LAccess access, LVar value) {}
	/** 写入 {@code setprop} 的内容属性，具体含义由内容类型决定；目标不支持时无动作。 */
	default void setProp(String content, LVar value) {}
	/** 位置是数字还是名字、按哪种含义解释由目标自行决定，故整个变量传入；{@code privileged} 为调用方是否为特权处理器，未处理时由调用方把结果置空。 */
	default boolean read(LVar position, LVar output, boolean privileged) {
		return false;
	}
	default boolean write(LVar position, LVar value, boolean privileged) {
		return false;
	}
	/** 是否接收不影响打印缓冲区被 {@code printflush} 清空。 */
	default void print(String text) {}
}
