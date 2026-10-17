package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.display.*;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/** 客户端专属的处理放在 {@link MLogClientNetwork}，避免服务端加载到客户端类。 */
public class MLogNetwork {
	private static final Map<Long, Long> COOLDOWN = new HashMap<>();
	/** 同一格两次应答的最小间隔：漫开整组为平方代价，需要限流。 */
	private static final int COOLDOWN_TICKS = 20;
	public static void register(RegisterPayloadHandlersEvent event) {
		var registrar = event.registrar(MLog.ID).versioned("6");
		registrar.playToServer(CodeUpdatePayload.TYPE, CodeUpdatePayload.STREAM_CODEC, MLogNetwork::onCodeUpdate);
		registrar.playToServer(LinkPayload.TYPE, LinkPayload.STREAM_CODEC, MLogNetwork::onLink);
		registrar.playToServer(DisplaySyncPayload.TYPE, DisplaySyncPayload.STREAM_CODEC, MLogNetwork::onDisplaySync);
		registrar.playToClient(LogicVarsPayload.TYPE, LogicVarsPayload.STREAM_CODEC, MLogNetwork::onSync);
		registrar.playToClient(DisplayPayload.TYPE, DisplayPayload.STREAM_CODEC, MLogNetwork::onDisplay);
		registrar.playToClient(DisplayBatchPayload.TYPE, DisplayBatchPayload.STREAM_CODEC, MLogNetwork::onDisplayBatch);
	}
	private static void onCodeUpdate(CodeUpdatePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			var processor = processor(context, payload.pos());
			if (processor == null) return;
			if (context.player() instanceof ServerPlayer player && !accessible(player, processor)) return;
			processor.updateCode(payload.code());
		});
	}
	private static void onLink(LinkPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player)) return;
			var processor = processor(context, payload.pos());
			if (processor == null) return;
			if (!accessible(player, processor)) return;
			var removed = payload.remove();
			var error = removed ? processor.removeLink(payload.target()) : processor.addLink(payload.target());
			if (error != null) {
				player.displayClientMessage(error, true);
				return;
			}
			player.displayClientMessage(
				removed
					? Component.translatable("gui.mlog.link.removed")
					: Component.translatable("gui.mlog.link.added"), true
			);
		});
	}
	/** 画布重建时的补发请求：按请求格所在组重发有内容的格，无须等 {@code RESEND_INTERVAL}。 */
	private static void onDisplaySync(DisplaySyncPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player)) return;
			var level = player.serverLevel();
			var pos = payload.pos();
			var state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof TileLogicDisplayBlock)) return;
			// 只处理可见格的请求：整组重发为平方代价，不得作为无限入口
			if (!player.getChunkTrackingView().contains(pos.getX() >> 4, pos.getZ() >> 4)) return;
			var now = level.getGameTime();
			if (COOLDOWN.size() > 1024) COOLDOWN.clear();
			var previous = COOLDOWN.put(pos.asLong(), now);
			if (previous != null && now - previous < COOLDOWN_TICKS) return;
			var orientation = state.getValue(TileLogicDisplayBlock.ORIENTATION);
			var right = DisplayGroup.right(orientation);
			var down = DisplayGroup.down(orientation);
			var group = DisplayGroup.of(level, pos);
			for (var y = 0; y < group.height(); y++)
				for (var x = 0; x < group.width(); x++)
					if (group.contains(x, y) && level.getBlockEntity(group.at(
						right,
						down,
						x,
						y
					)) instanceof TileLogicDisplayBlockEntity display) display.resendTo(player);
		});
	}
	/** 该包只发给客户端，服务端不会执行到这里。 */
	private static void onSync(LogicVarsPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyVars(payload));
	}
	private static void onDisplay(DisplayPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyDisplay(payload));
	}
	private static void onDisplayBatch(DisplayBatchPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyBatch(payload));
	}
	@SuppressWarnings("resource")
	private static @Nullable AbstractProcessorBlockEntity processor(IPayloadContext context, BlockPos pos) {
		if (!(context.player() instanceof ServerPlayer player)) return null;
		return player.serverLevel().getBlockEntity(pos) instanceof AbstractProcessorBlockEntity processor ? processor : null;
	}
	/** 世界处理器仅 OP 可操作；界面已校验一次，此处须再校验，客户端数据包不可信。 */
	public static boolean accessible(ServerPlayer player, AbstractProcessorBlockEntity processor) {
		return !processor.privileged() || player.canUseGameMasterBlocks();
	}
}
