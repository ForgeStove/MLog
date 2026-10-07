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
/** 网络包注册与双端处理。客户端专属的处理放在 {@link MLogClientNetwork}，避免服务端加载到客户端类。 */
public final class MLogNetwork {
	/** 补发请求的冷却表：格坐标 → 上次应答的游戏刻。 */
	private static final Map<Long, Long> COOLDOWN = new HashMap<>();
	/** 同一格两次应答的最小间隔：漫开整组是平方代价（16×16 约十几万次比较），须防刷。 */
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
	/**
	 * 客户端画布刚建出来时（拆、并组、移出视野回来）发来的补发请求。
	 * <p>内容一直留在服务端的 {@code last} 里：按请求那一格所在的一组，把组内有内容的格重发回去，
	 * 客户端一个往返就把画布补上，不必等 {@code RESEND_INTERVAL}。
	 */
	private static void onDisplaySync(DisplaySyncPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player)) return;
			var level = player.serverLevel();
			var pos = payload.pos();
			var state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof TileLogicDisplayBlock)) return;
			// 只答应看得见那一格的请求：漫开整组是平方代价，不能当免费接口用
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
	/** 目标包只会发给客户端，服务端不会执行到这里。 */
	private static void onSync(LogicVarsPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyVars(payload));
	}
	/** 同上，只会发给客户端。 */
	private static void onDisplay(DisplayPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyDisplay(payload));
	}
	/** 同上，只会发给客户端。 */
	private static void onDisplayBatch(DisplayBatchPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyBatch(payload));
	}
	/** @return 该坐标上的处理器，不是处理器时返回 {@code null}。 */
	@SuppressWarnings("resource")
	private static @Nullable AbstractProcessorBlockEntity processor(IPayloadContext context, BlockPos pos) {
		if (!(context.player() instanceof ServerPlayer player)) return null;
		return player.serverLevel().getBlockEntity(pos) instanceof AbstractProcessorBlockEntity processor ? processor : null;
	}
	/**
	 * @return 玩家是否可修改该处理器：世界处理器与命令方块相同，仅 OP 可操作。
	 * 	<p>界面已校验一次，此处再校验一次：客户端不可信，代码与链接均可被伪造的数据包修改。
	 */
	public static boolean accessible(ServerPlayer player, AbstractProcessorBlockEntity processor) {
		return !processor.privileged() || player.canUseGameMasterBlocks();
	}
}
