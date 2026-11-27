package io.github.forgestove.mlog.compat.sable;
import io.github.forgestove.mlog.core.Mods;
import io.github.forgestove.mlog.logic.LAccess;
import java.util.*;
import org.jetbrains.annotations.Nullable;
/** Sable 提供的属性条目，仅与 Sable 一同注册。 */
public enum SableAccess implements LAccess {
	plotX,
	plotY,
	plotZ,
	yaw,
	pitch,
	roll,
	;
	private static final Map<String, SableAccess> BY_NAME = new HashMap<>();
	static {
		for (var access : values()) BY_NAME.put(access.name(), access);
	}
	public static @Nullable SableAccess byName(String name) {
		return BY_NAME.get(name);
	}
	public static List<String> controls() {
		return List.of();
	}
	@Override
	public String namespace() {
		return Mods.sable.id();
	}
}
