package io.github.forgestove.mlog.logic;
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
}
