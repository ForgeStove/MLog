package io.github.forgestove.mlog.logic;
import net.minecraft.core.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
/**
 * 能被 {@code sensor} 指令读取的对象。第三方方块让自己的方块实体实现此接口即可接入。
 * <p>属性名可能是 {@link LAccess} 里的内置项，也可能是任意的方块状态属性名。
 */
public interface MLogSenseable {
	/** 没有对象输出时的哨兵值。 */
	Object NO_SENSED = new Object();
	/** @return 属性对应的数值，不认识该属性时返回 0。 */
	double sense(String access);
	/** @return 属性对应的对象（方块类型、名字、物品等），没有则返回 {@link #NO_SENSED}。 */
	default Object senseObject(String access) {
		return NO_SENSED;
	}
	/**
	 * @param slot 槽位下标
	 * @return 该格的物品，对应 {@code sensor} 的 {@code @slotItem}；非容器、越界或空槽返回 {@code null}
	 */
	default @Nullable Item itemAt(int slot) {
		return null;
	}
	/**
	 * @param tank 罐位下标
	 * @return 该罐的流体，对应 {@code sensor} 的 {@code @slotFluid}；无流体能力、越界或空罐返回 {@code null}
	 */
	default @Nullable Fluid fluidAt(int tank) {
		return null;
	}
	/**
	 * 执行一次控制。
	 * <p>属性名按方块状态属性匹配，方块没有这个属性、或给的值不是它的合法取值时什么都不做。
	 *
	 * @param value      要写入的值：数值属性取 {@code value.num()}，
	 *                   物品类属性（如 Create 的过滤槽）取其对象
	 * @param owner      发起该指令的处理器；需要随其生灭的效果（如红石充能）须记录它
	 * @param face       从哪一面接源供电；{@code null} 表示六面均接，整个方块被充能
	 * @param privileged 调用方是否为特权处理器。是否可改、可改哪些由目标自行判定；
	 *                   方块状态类属性走 {@link LAccess#controlAllowed()} 白名单，特权处理器不受限
	 * @param index      末尾值的另一种读法：按行号写的（如 Create 的值设置）使用此参数
	 * @return 是否实际产生改动
	 */
	default boolean control(
		String access,
		LVar value,
		@Nullable Direction face,
		@Nullable BlockPos owner,
		boolean privileged,
		int index
	) {
		return false;
	}
	/**
	 * 从 {@code position} 处读一个值。
	 * <p>位置是数字还是名字、要按哪种含义解释，由目标自己定，所以整只变量传进来。
	 *
	 * @param privileged 调用方是不是特权处理器。特权方块靠它挡下非特权的读写
	 * @return 是否处理了这次读取；没处理时由调用方把结果置空
	 */
	default boolean read(LVar position, LVar output, boolean privileged) {
		return false;
	}
	/**
	 * 往 {@code position} 处写一个值。
	 *
	 * @return 是否真的写进去了
	 */
	default boolean write(LVar position, LVar value, boolean privileged) {
		return false;
	}
	/**
	 * 接收 {@code printflush} 交过来的文本。
	 * <p>缓冲区由 {@code printflush} 负责清空，这里收不收都不影响它被清。
	 */
	default void print(String text) {}
}
