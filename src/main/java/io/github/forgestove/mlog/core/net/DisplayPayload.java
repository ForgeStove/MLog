package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.core.util.MLogUtil;
import io.github.forgestove.mlog.logic.DrawCmd;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
/** 显示屏的绘制命令；仅含本次 flush 的命令，画布内容留存于客户端。 */
public record DisplayPayload(BlockPos pos, List<DrawCmd> commands) implements CustomPacketPayload {
	public static final Type<DisplayPayload> TYPE = new Type<>(MLogUtil.getMLogRes("display"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DisplayPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		DisplayPayload::pos,
		DrawCmd.LIST_CODEC,
		DisplayPayload::commands,
		DisplayPayload::new
	);
	@Override
	public Type<DisplayPayload> type() {
		return TYPE;
	}
}
