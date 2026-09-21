package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlockEntity;
import io.github.forgestove.mlog.logic.LogicLink;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
/** 网络包注册与双端处理。客户端专属的处理放在 {@link MLogClientNetwork}，避免服务端加载到客户端类。 */
public final class MLogNetwork {
	/**
	 * 玩家与处理器的最大交互距离，取连接范围的两倍。
	 * <p>范围是 {@link LogicLink#RANGE} 格的立方体，玩家站在立方体边上、再去够另一头的方块，
	 * 最远也就差不多这么远。卡得比范围还紧的话，画出来的框和实际连得上的地方就对不上。
	 */
	private static final int MAX_INTERACT_DISTANCE = LogicLink.RANGE * 2;
	public static void register(RegisterPayloadHandlersEvent event) {
		var registrar = event.registrar(MLog.ID).versioned("1");
		registrar.playToServer(CodeUpdatePayload.TYPE, CodeUpdatePayload.STREAM_CODEC, MLogNetwork::onCodeUpdate);
		registrar.playToServer(LinkPayload.TYPE, LinkPayload.STREAM_CODEC, MLogNetwork::onLink);
		registrar.playToClient(LogicVarsPayload.TYPE, LogicVarsPayload.STREAM_CODEC, MLogNetwork::onSync);
	}
	private static void onCodeUpdate(CodeUpdatePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			var processor = processor(context, payload.pos());
			if (processor == null) return;
			if (context.player() instanceof ServerPlayer player && !accessible(player, processor)) return;
			processor.setCode(payload.code());
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
	/** 目标包只会发给客户端，服务端不会执行到这里。 */
	private static void onSync(LogicVarsPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyVars(payload));
	}
	/** @return 玩家可操作的处理器，校验不通过则返回 {@code null}。 */
	@SuppressWarnings("resource")
	private static @Nullable MicroProcessorBlockEntity processor(IPayloadContext context, BlockPos pos) {
		if (!(context.player() instanceof ServerPlayer player)) return null;
		if (!player.canInteractWithBlock(pos, MAX_INTERACT_DISTANCE)) return null;
		return player.serverLevel().getBlockEntity(pos) instanceof MicroProcessorBlockEntity processor ? processor : null;
	}
	/**
	 * @return 玩家能不能改这个处理器。世界处理器和命令方块一样只有 OP 能碰，对齐 Mindustry 的
	 *    {@code LogicBlock#accessible}。
	 * 	<p>界面那边已经挡过一道，这里再挡一次：客户端拦不住，代码和链接都能被伪造的包改掉。
	 */
	public static boolean accessible(ServerPlayer player, MicroProcessorBlockEntity processor) {
		return !processor.privileged() || player.canUseGameMasterBlocks();
	}
}
