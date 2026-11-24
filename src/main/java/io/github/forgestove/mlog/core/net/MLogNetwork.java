package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.content.display.*;
import io.github.forgestove.mlog.content.processor.AbstractProcessorBlockEntity;
import io.github.forgestove.mlog.logic.MLogUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.*;
public class MLogNetwork {
	private static final Map<Long, Long> COOLDOWN = new HashMap<>();
	private static final int COOLDOWN_TICKS = 20;
	public static void register(RegisterPayloadHandlersEvent event) {
		var registrar = event.registrar(MLog.ID).versioned("7");
		registrar.playToServer(CodeUpdatePayload.TYPE, CodeUpdatePayload.STREAM_CODEC, MLogNetwork::onCodeUpdate);
		registrar.playToServer(LinkPayload.TYPE, LinkPayload.STREAM_CODEC, MLogNetwork::onLink);
		registrar.playToServer(ProcessorTagPayload.TYPE, ProcessorTagPayload.STREAM_CODEC, MLogNetwork::onProcessorTag);
		registrar.playToServer(LinkUnitsRequestPayload.TYPE, LinkUnitsRequestPayload.STREAM_CODEC, MLogNetwork::onLinkUnits);
		registrar.playToServer(UnitOwnerRequestPayload.TYPE, UnitOwnerRequestPayload.STREAM_CODEC, MLogNetwork::onUnitOwner);
		registrar.playToServer(DisplaySyncPayload.TYPE, DisplaySyncPayload.STREAM_CODEC, MLogNetwork::onDisplaySync);
		registrar.playToClient(LogicVarsPayload.TYPE, LogicVarsPayload.STREAM_CODEC, MLogNetwork::onSync);
		registrar.playToClient(LinkUnitsPayload.TYPE, LinkUnitsPayload.STREAM_CODEC, MLogNetwork::onLinkUnitsSync);
		registrar.playToClient(UnitOwnerPayload.TYPE, UnitOwnerPayload.STREAM_CODEC, MLogNetwork::onUnitOwnerSync);
		registrar.playToClient(DisplayPayload.TYPE, DisplayPayload.STREAM_CODEC, MLogNetwork::onDisplay);
		registrar.playToClient(DisplayBatchPayload.TYPE, DisplayBatchPayload.STREAM_CODEC, MLogNetwork::onDisplayBatch);
		registrar.playToClient(MusicPayload.TYPE, MusicPayload.STREAM_CODEC, MLogNetwork::onMusic);
	}
	private static void onCodeUpdate(CodeUpdatePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player)) return;
			var processor = processor(context, payload.pos());
			if (processor == null || !accessible(player, processor)) return;
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
					? Component.translatable("mlog.gui.link.removed")
					: Component.translatable("mlog.gui.link.added"), true
			);
		});
	}
	private static void onLinkUnits(LinkUnitsRequestPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player)) return;
			var processor = processor(context, payload.pos());
			if (processor == null || !accessible(player, processor)) return;
			var ids = MLogUnits.controlledBy(payload.pos()).stream().mapToInt(Entity::getId).toArray();
			PacketDistributor.sendToPlayer(player, new LinkUnitsPayload(payload.pos(), ids));
		});
	}
	private static void onLinkUnitsSync(LinkUnitsPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyLinkUnits(payload));
	}
	private static void onProcessorTag(ProcessorTagPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			var processor = processor(context, payload.pos());
			if (processor == null || !processor.privileged()) return;
			if (!(context.player() instanceof ServerPlayer player) || !accessible(player, processor)) return;
			processor.setTag(payload.tag().replaceAll("\\p{Cntrl}", ""));
		});
	}
	@SuppressWarnings("resource")
	private static void onUnitOwner(UnitOwnerRequestPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player)) return;
			var owner = Optional.<BlockPos>empty();
			if (player.serverLevel().getEntity(payload.entityId()) instanceof Entity entity) {
				var found = MLogUnits.ownerOf(entity);
				if (found != null && (
					!(player.serverLevel().getBlockEntity(found) instanceof AbstractProcessorBlockEntity processor) || accessible(
						player,
						processor
					)
				)) owner = Optional.of(found);
			}
			PacketDistributor.sendToPlayer(player, new UnitOwnerPayload(payload.entityId(), owner));
		});
	}
	private static void onUnitOwnerSync(UnitOwnerPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyUnitOwner(payload));
	}
	private static void onDisplaySync(DisplaySyncPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player)) return;
			var level = player.serverLevel();
			var pos = payload.pos();
			var state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof TileLogicDisplayBlock)) return;
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
	private static void onSync(LogicVarsPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyVars(payload));
	}
	private static void onDisplay(DisplayPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyDisplay(payload));
	}
	private static void onDisplayBatch(DisplayBatchPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyBatch(payload));
	}
	private static void onMusic(MusicPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MLogClientNetwork.applyMusic(payload));
	}
	@SuppressWarnings("resource")
	private static @Nullable AbstractProcessorBlockEntity processor(IPayloadContext context, BlockPos pos) {
		if (!(context.player() instanceof ServerPlayer player)) return null;
		return player.serverLevel().getBlockEntity(pos) instanceof AbstractProcessorBlockEntity processor ? processor : null;
	}
	public static boolean accessible(ServerPlayer player, AbstractProcessorBlockEntity processor) {
		return !processor.privileged() || player.canUseGameMasterBlocks();
	}
}
