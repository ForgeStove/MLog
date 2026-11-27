package io.github.forgestove.mlog.compat.sable;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import io.github.forgestove.mlog.core.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.*;
import java.util.function.UnaryOperator;
public final class SableLinks {
	private static final Set<AbstractProcessorBlockEntity> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());
	public static void track(AbstractProcessorBlockEntity processor) {
		if (!Mods.sable.isLoaded()) return;
		var level = processor.getLevel();
		if (level == null || level.isClientSide) return;
		LOADED.add(processor);
	}
	public static void untrack(AbstractProcessorBlockEntity processor) {
		if (LOADED.isEmpty()) return;
		LOADED.remove(processor);
	}
	public static void clear(ServerStoppedEvent ignoredEvent) {
		LOADED.clear();
	}
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
