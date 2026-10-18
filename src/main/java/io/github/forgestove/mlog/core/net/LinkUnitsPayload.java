package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.core.util.MLogUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** 链接模式的服务端应答：该处理器当前控制的单位 id；请求见 {@link LinkUnitsRequestPayload}。 */
public record LinkUnitsPayload(BlockPos pos, int[] ids) implements CustomPacketPayload {
	public static final Type<LinkUnitsPayload> TYPE = new Type<>(MLogUtil.getMLogRes("link_units"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LinkUnitsPayload> STREAM_CODEC = StreamCodec.of(
		(buf, payload) -> {
			buf.writeBlockPos(payload.pos());
			buf.writeVarInt(payload.ids().length);
			for (var id : payload.ids()) buf.writeVarInt(id);
		},
		buf -> {
			var pos = buf.readBlockPos();
			var ids = new int[buf.readVarInt()];
			for (var i = 0; i < ids.length; i++) ids[i] = buf.readVarInt();
			return new LinkUnitsPayload(pos, ids);
		}
	);
	@Override
	public Type<LinkUnitsPayload> type() {
		return TYPE;
	}
}
