package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.core.util.MLogUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** 链接模式询问处理器当前控制的单位，应答见 {@link LinkUnitsPayload}。 */
public record LinkUnitsRequestPayload(BlockPos pos) implements CustomPacketPayload {
	public static final Type<LinkUnitsRequestPayload> TYPE = new Type<>(MLogUtil.getMLogRes("link_units_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LinkUnitsRequestPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		LinkUnitsRequestPayload::pos,
		LinkUnitsRequestPayload::new
	);
	@Override
	public Type<LinkUnitsRequestPayload> type() {
		return TYPE;
	}
}
