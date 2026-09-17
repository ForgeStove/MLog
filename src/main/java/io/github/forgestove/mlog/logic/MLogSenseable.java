package io.github.forgestove.mlog.logic;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
/**
 * 能被 {@code sensor} 指令读取的对象。第三方方块让自己的方块实体实现此接口即可接入。
 * <p>属性名可能是 {@link LAccess} 里的内置项，也可能是任意的方块状态属性名。
 */
public interface MLogSenseable {
	/** 没有对象输出时的哨兵值，与 Mindustry 的 {@code Senseable.noSensed} 一致。 */
	Object NO_SENSED = new Object();
	/** @return 属性对应的数值，不认识该属性时返回 0。 */
	double sense(String access);
	/** @return 属性对应的对象（方块类型、名字、物品等），没有则返回 {@link #NO_SENSED}。 */
	default Object senseObject(String access) {
		return NO_SENSED;
	}
	/**
	 * 执行一次控制，对应 Mindustry 的 {@code Building#control}。
	 * <p>属性名按方块状态属性匹配，方块没有这个属性、或给的值不是它的合法取值时什么都不做。
	 *
	 * @param owner 下这条指令的处理器，需要跟随它生灭的效果（如红石充能）得记下它
	 * @return 是否真的改动了什么
	 */
	default boolean control(String access, double value, @Nullable BlockPos owner) {
		return false;
	}
}
