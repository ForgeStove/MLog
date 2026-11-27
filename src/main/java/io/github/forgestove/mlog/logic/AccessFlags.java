package io.github.forgestove.mlog.logic;
/**
 * {@link LAccess} 实现共用的能力位。
 * 单独放在类里，枚举常量参数才能在类初始化前引用。
 */
public final class AccessFlags {
	public static final int BLOCK_PROPERTY = 1;
	public static final int SIDED = 1 << 1;
	public static final int SLOT_ITEM = 1 << 2;
	public static final int SLOT_FLUID = 1 << 3;
	public static final int LIST_SIZE = 1 << 4;
	private AccessFlags() {}
}
