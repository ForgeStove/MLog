package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.core.util.MLogUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;
/** 单位控制来源的服务端应答：处理器位置或空值；请求见 {@link UnitOwnerRequestPayload}。 */
public record UnitOwnerPayload(int entityId, Optional<BlockPos> owner) implements CustomPacketPayload {
	public static final Type<UnitOwnerPayload> TYPE = new Type<>(MLogUtil.getMLogRes("unit_owner"));
	public static final StreamCodec<RegistryFriendlyByteBuf, UnitOwnerPayload> STREAM_CODEC = StreamCodec.of(
		(buf, payload) -> {
			buf.writeVarInt(payload.entityId());
			buf.writeBoolean(payload.owner().isPresent());
			payload.owner().ifPresent(buf::writeBlockPos);
		},
		buf -> {
			var id = buf.readVarInt();
			var owner = buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.<BlockPos>empty();
			return new UnitOwnerPayload(id, owner);
		}
	);
	@Override
	public Type<UnitOwnerPayload> type() {
		return TYPE;
	}
}
