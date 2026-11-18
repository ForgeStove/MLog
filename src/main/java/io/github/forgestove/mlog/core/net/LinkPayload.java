package io.github.forgestove.mlog.core.net;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/** {@code remove} 为 {@code true} 时断开链接。 */
public record LinkPayload(BlockPos pos, BlockPos target, boolean remove) implements CustomPacketPayload {
	public static final Type<LinkPayload> TYPE = new Type<>(getMLogRes("link"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LinkPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		LinkPayload::pos,
		BlockPos.STREAM_CODEC,
		LinkPayload::target,
		ByteBufCodecs.BOOL,
		LinkPayload::remove,
		LinkPayload::new
	);
	@Override
	public Type<LinkPayload> type() {
		return TYPE;
	}
}
