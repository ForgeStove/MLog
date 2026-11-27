package io.github.forgestove.mlog.logic;
import io.github.forgestove.mlog.core.Mods;

import java.util.Locale;
public interface LAccess {
	String name();
	default String namespace() {
		return Mods.minecraft.id();
	}
	default Slot slot() {
		return Slot.NONE;
	}
	default boolean listSize() {
		return false;
	}
	default boolean blockProperty() {
		return false;
	}
	default boolean sided() {
		return false;
	}
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
