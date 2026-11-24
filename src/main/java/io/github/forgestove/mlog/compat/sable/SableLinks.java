package io.github.forgestove.mlog.compat.sable;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import io.github.forgestove.mlog.core.MLogMods;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.*;
import java.util.function.UnaryOperator;
public final class SableLinks {
	/** 服务端已加载的处理器。 */
	private static final Set<AbstractProcessorBlockEntity> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());
	public static void track(AbstractProcessorBlockEntity processor) {
		if (!MLogMods.sable.isLoaded()) return;
		var level = processor.getLevel();
		if (level == null || level.isClientSide) return;
		LOADED.add(processor);
	}
	public static void untrack(AbstractProcessorBlockEntity processor) {
		if (LOADED.isEmpty()) return;
		LOADED.remove(processor);
	}
	/** 服务器停止时清空，避免跨存档残留。 */
	public static void clear(ServerStoppedEvent ignoredEvent) {
		LOADED.clear();
	}
	/**
	 * 搬运结束后改写受影响的链接。
	 *
	 * @param level  搬运的目标世界
	 * @param blocks 搬运前的方块坐标
	 * @param toNew  搬运前的坐标换算到搬运后
	 */
	public static void handleMove(ServerLevel level, Iterable<BlockPos> blocks, UnaryOperator<BlockPos> toNew) {
		var moved = new HashSet<BlockPos>();
		for (var pos : blocks) moved.add(pos);
		if (moved.isEmpty()) return;
		for (var oldPos : moved) {
			var newPos = toNew.apply(oldPos);
			if (level.getBlockEntity(newPos) instanceof AbstractProcessorBlockEntity processor)
				processor.remapMovedLinks(oldPos, moved, toNew);
		}
		if (LOADED.isEmpty()) return;
		for (var processor : LOADED.toArray(AbstractProcessorBlockEntity[]::new)) {
			if (processor.isRemoved()) {
				LOADED.remove(processor);
				continue;
			}
			if (processor.getLevel() != level || moved.contains(processor.getBlockPos())) continue;
			processor.remapMovedLinks(processor.getBlockPos(), moved, toNew);
		}
	}
}
