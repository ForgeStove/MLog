package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.core.util.MLogUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** 查询单位的控制来源，应答见 {@link UnitOwnerPayload}。 */
public record UnitOwnerRequestPayload(int entityId) implements CustomPacketPayload {
	public static final Type<UnitOwnerRequestPayload> TYPE = new Type<>(MLogUtil.getMLogRes("unit_owner_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, UnitOwnerRequestPayload> STREAM_CODEC
		= StreamCodec.of((buf, payload) -> buf.writeVarInt(payload.entityId()), buf -> new UnitOwnerRequestPayload(buf.readVarInt()));
	@Override
	public Type<UnitOwnerRequestPayload> type() {
		return TYPE;
	}
}
