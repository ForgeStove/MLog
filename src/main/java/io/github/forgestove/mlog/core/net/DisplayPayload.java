package io.github.forgestove.mlog.core.net;
import io.github.forgestove.mlog.MLog;
import io.github.forgestove.mlog.logic.DrawCmd;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
/**
 * 显示屏的绘制命令。
 * <p>命令不随方块实体数据走：区块包只在玩家看到这一块时把方块实体本身送过去，
 * 命令另发这一份——{@code replace} 为真即刚看到这一块时的全量，此后每次只发这一批新的。
 */
public record DisplayPayload(BlockPos pos, List<DrawCmd> commands, boolean replace) implements CustomPacketPayload {
	public static final Type<DisplayPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MLog.ID, "display"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DisplayPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		DisplayPayload::pos,
		DrawCmd.LIST_CODEC,
		DisplayPayload::commands,
		ByteBufCodecs.BOOL,
		DisplayPayload::replace,
		DisplayPayload::new
	);
	@Override
	public Type<DisplayPayload> type() {
		return TYPE;
	}
}
