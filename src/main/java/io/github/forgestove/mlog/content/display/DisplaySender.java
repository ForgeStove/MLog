package io.github.forgestove.mlog.content.display;
import io.github.forgestove.mlog.core.net.DisplayBatchPayload;
import io.github.forgestove.mlog.core.net.DisplayBatchPayload.Entry;
import io.github.forgestove.mlog.logic.DrawCmd;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
/** 显示屏内容的发送：tick 内只入队，tick 末按玩家合并成一个包发出。 */
public final class DisplaySender {
	/** flush 取走后清空。 */
	private static final Map<ServerLevel, Map<BlockPos, List<DrawCmd>>> PENDING = new HashMap<>();
	/** 同一格本 tick 排过则覆盖，后到的才是最新的。 */
	public static void enqueue(Level level, BlockPos pos, List<DrawCmd> commands) {
		if (!(level instanceof ServerLevel serverLevel)) return;
		PENDING.computeIfAbsent(serverLevel, key -> new LinkedHashMap<>()).put(pos, commands);
	}
	/** 按玩家合并发出；每个玩家只收他看得见的那几格。 */
	public static void flush(Post event) {
		if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
		var pending = PENDING.remove(serverLevel);
		if (pending == null || pending.isEmpty()) return;
		for (var player : serverLevel.players()) {
			var entries = new ArrayList<Entry>();
			for (var entry : pending.entrySet()) {
				var pos = entry.getKey();
				if (!player.getChunkTrackingView().contains(pos.getX() >> 4, pos.getZ() >> 4)) continue;
				entries.add(new Entry(pos, entry.getValue()));
			}
			if (entries.isEmpty()) continue;
			PacketDistributor.sendToPlayer(player, new DisplayBatchPayload(entries));
		}
	}
	public static void clear(ServerStoppedEvent ignoredEvent) {
		PENDING.clear();
	}
}
