package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.microprocessor.MicroProcessorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
/** 网络包注册与双端处理。客户端专属的处理放在 {@link MLogClientNetwork}，避免服务端加载到客户端类。 */
public final class MLogNetwork {
	/** 玩家与处理器的最大交互距离。 */
	private static final double MAX_INTERACT_DISTANCE = 8.0;
	public static void register(RegisterPayloadHandlersEvent event) {
		var registrar = event.registrar(MLog.ID).versioned("1");
		registrar.playToServer(CodeUpdatePayload.TYPE, CodeUpdatePayload.STREAM_CODEC, MLogNetwork::onCodeUpdate);
		registrar.playToServer(LinkPayload.TYPE, LinkPayload.STREAM_CODEC, MLogNetwork::onLink);
		registrar.playToClient(LogicVarsPayload.TYPE, LogicVarsPayload.STREAM_CODEC, MLogNetwork::onSync);
	}
	private static void onCodeUpdate(CodeUpdatePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			var processor = processor(context, payload.pos());
			if (processor != null) processor.setCode(payload.code());
		});
	}
	private static void onLink(LinkPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			var processor = processor(context, payload.pos());
			if (processor == null) return;
			var removed = payload.remove();
			var error = removed ? processor.removeLink(payload.target()) : processor.addLink(payload.target());
			// 成功与否都吱一声：链接模式在客户端没有任何别的回执，不提示就分不清成功还是被拒
			if (context.player() instanceof ServerPlayer player) {
				var done = removed ? "gui.mlog.link.removed" : "gui.mlog.link.added";
				player.displayClientMessage(Component.translatable(error != null ? error : done), true);
			}
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
}
