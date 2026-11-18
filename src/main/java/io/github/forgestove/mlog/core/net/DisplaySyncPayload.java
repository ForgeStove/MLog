package io.github.forgestove.mlog.core.net;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import static io.github.forgestove.mlog.core.util.MLogUtil.getMLogRes;
/** 客户端画布首次创建时请求服务端重发该格所在显示组已发出的命令。 */
public record DisplaySyncPayload(BlockPos pos) implements CustomPacketPayload {
	public static final Type<DisplaySyncPayload> TYPE = new Type<>(getMLogRes("display_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DisplaySyncPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		DisplaySyncPayload::pos,
		DisplaySyncPayload::new
	);
	@Override
	public Type<DisplaySyncPayload> type() {
		return TYPE;
	}
}
