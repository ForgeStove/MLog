package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.logic.MLogUnits;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 可作为单位的实体类型：按当前世界实例判定，包含模组实体。 */
public class UnitTypeNames {
	private static @Nullable List<String> cached;
	/** 世界尚未加载时为空。 */
	public static List<String> all() {
		if (cached != null) return cached;
		var level = mc.level;
		if (level == null) return List.of();
		var list = new ArrayList<String>();
		for (var id : BuiltInRegistries.ENTITY_TYPE.keySet()) try {
			if (MLogUnits.bindableType(BuiltInRegistries.ENTITY_TYPE.get(id).create(level))) list.add("@" + id);
		} catch (RuntimeException ignored) {}
		list.sort(Comparator.naturalOrder());
		return cached = List.copyOf(list);
	}
}
