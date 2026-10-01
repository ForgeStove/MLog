package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
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
	 * <p>范围为 {@link LogicLink#RANGE} 格的立方体，玩家站在立方体一侧、触及对侧目标即接近该距离。
	 * 距离小于连接范围时，绘制出的框与实际可连接的位置不一致。
	 */
	private static final int MAX_INTERACT_DISTANCE = LogicLink.RANGE * 2;
	public static void register(RegisterPayloadHandlersEvent event) {
		// 版本号随包体变更：命令的编码与类型序号已改，旧客户端须被拒绝
		var registrar = event.registrar(MLog.ID).versioned("5");
		registrar.playToServer(CodeUpdatePayload.TYPE, CodeUpdatePayload.STREAM_CODEC, MLogNetwork::onCodeUpdate);
		registrar.playToServer(LinkPayload.TYPE, LinkPayload.STREAM_CODEC, MLogNetwork::onLink);
		registrar.playToClient(LogicVarsPayload.TYPE, LogicVarsPayload.STREAM_CODEC, MLogNetwork::onSync);
		registrar.playToClient(DisplayPayload.TYPE, DisplayPayload.STREAM_CODEC, MLogNetwork::onDisplay);
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
	/** 目标包只会发给客户端，服务端不会执行到这里。 */
	private static void onSync(LogicVarsPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyVars(payload));
	}
	/** 同上，只会发给客户端。 */
	private static void onDisplay(DisplayPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyDisplay(payload));
	}
	/** @return 玩家可操作的处理器，校验不通过则返回 {@code null}。 */
	@SuppressWarnings("resource")
	private static @Nullable AbstractProcessorBlockEntity processor(IPayloadContext context, BlockPos pos) {
		if (!(context.player() instanceof ServerPlayer player)) return null;
		if (!player.canInteractWithBlock(pos, MAX_INTERACT_DISTANCE)) return null;
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
