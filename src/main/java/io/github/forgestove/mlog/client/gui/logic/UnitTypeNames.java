package io.github.forgestove.mlog.client.gui.logic;
import io.github.forgestove.mlog.logic.MLogUnits;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.forgestove.mlog.core.util.MLogClientUtil.mc;
/** 可作为单位的实体类型：按当前世界实例判定，包含模组实体。 */
public class UnitTypeNames {
	private static @Nullable List<String> normal, privileged;
	/** 世界尚未加载时为空；{@code allowHostile} 为真时包含敌对生物。 */
	public static List<String> all(boolean allowHostile) {
		var cached = allowHostile ? privileged : normal;
		if (cached != null) return cached;
		var level = mc.level;
		if (level == null) return List.of();
		var list = new ArrayList<String>();
		for (var id : BuiltInRegistries.ENTITY_TYPE.keySet())
			try {
				var entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(level);
				if (allowHostile ? entity instanceof Mob : MLogUnits.bindableType(entity)) list.add("@" + id);
			} catch (RuntimeException ignored) {}
		list.sort(Comparator.naturalOrder());
		var result = List.copyOf(list);
		if (allowHostile) privileged = result;
		else normal = result;
		return result;
	}
}
