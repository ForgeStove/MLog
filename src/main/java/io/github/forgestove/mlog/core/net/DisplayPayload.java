package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.logic.DrawCmd;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
/** 显示屏的绘制命令；仅含本次 flush 的命令，画布内容留存于客户端。 */
public record DisplayPayload(BlockPos pos, List<DrawCmd> commands) implements CustomPacketPayload {
	public static final Type<DisplayPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MLog.ID, "display"));
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
