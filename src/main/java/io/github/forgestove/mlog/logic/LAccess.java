package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.core.Mods;

import java.util.Locale;
/**
 * 可由 {@code sensor} 读取、由 {@code control} 写入的属性条目。
 * 内置项在 {@link VanillaAccess}，模组项在各自的 compat 包，运行期由 {@link AccessRegistry} 合并。
 */
public interface LAccess {
	/** 条目名，即 {@code @} 后的名字。枚举的 {@link Enum#name()} 直接满足。 */
	String name();
	/** 属性所属的命名空间，用于界面分组。 */
	default String namespace() {
		return Mods.minecraft.id();
	}
	/** 读取时按序号取容器内容。 */
	default Slot slot() {
		return Slot.NONE;
	}
	/** 对列表目标只取长度。 */
	default boolean listSize() {
		return false;
	}
	/** 方块状态属性用原名显示，不查翻译。 */
	default boolean blockProperty() {
		return false;
	}
	/** 按面区分取值，目前只有 Create 的过滤槽。 */
	default boolean sided() {
		return false;
	}
	/** 翻译键，界面层按它取名字和说明。 */
	default String key() {
		return "mlog.access." + name().toLowerCase(Locale.ROOT);
	}
	default String tipKey() {
		return key() + ".tip";
	}
	enum Slot {
		NONE,
		ITEM,
		FLUID
	}
}
